"""LangGraph nodes. Pricing nodes are deterministic; the LLM is used for unknown cities, the day plan and
(optionally) grounded web price hints, which are accepted only near the deterministic estimate."""

import asyncio
import logging
from datetime import date, timedelta
from decimal import Decimal

from app.config import get_settings
from app.graph.state import LegCtx, PlanState, StopCtx
from app.llm.base import CapReached, DayPlanContext, LlmError, LlmProvider, PriceQuery
from app.llm.models import DayPlanDraft, LlmDay
from app.llm.template import template_days
from app.prices.hints import AcceptedHint, accept, hint_cache
from app.prices.links import LegLink, StayLink, build_links
from app.pricing.catalog import City, country, find_city, season_for, season_profiles
from app.pricing.engine import (
    OVERNIGHT_HOURS,
    WEB_HINT,
    DaySegment,
    amount_range,
    budget_status,
    downgrade,
    is_international,
    misc_item,
    money,
    per_day_items_multi,
    recommend,
    rooms_for,
    stay_item,
    total_range,
    transport_item,
    transport_quotes,
)
from app.pricing.fx import fx_quote
from app.schemas import (
    Activity,
    BudgetStatus,
    Confidence,
    CostItem,
    DayPlan,
    FxInfo,
    ItineraryResponse,
    LegPlan,
    SeasonInfo,
    Stay,
    StopPlan,
    TransportMode,
    TransportOption,
    TripRequest,
    WebHint,
)

log = logging.getLogger(__name__)

PACE_ALIASES = {
    "relaxed": "relaxed", "leisure": "relaxed", "slow": "relaxed", "family": "relaxed",
    "balanced": "balanced", "moderate": "balanced", "cultural": "balanced",
    "packed": "packed", "adventure": "packed", "fast": "packed", "backpacking": "packed",
}
MAX_INTERESTS = 10
HINT_MODES = (TransportMode.FLIGHT, TransportMode.BUS)


def resolve_pace(req: TripRequest) -> str:
    for raw in (req.answers.get("pace"), req.travel_style):
        key = (raw or "").strip().lower()
        if key in PACE_ALIASES:
            return PACE_ALIASES[key]
    return "balanced"


def resolve_interests(req: TripRequest) -> list[str]:
    raw = [s for s in (req.answers.get("interests") or "").split(",")] + list(req.preferences)
    seen: dict[str, str] = {}
    for item in raw:
        cleaned = item.strip()
        if cleaned and cleaned.lower() not in seen:
            seen[cleaned.lower()] = cleaned
    return list(seen.values())[:MAX_INTERESTS]


def is_overnight(option: TransportOption) -> bool:
    return option.mode in (TransportMode.BUS, TransportMode.TRAIN) and option.duration_hours > OVERNIGHT_HOURS


def time_left(deadline: float | None, cap: float | None = None) -> float | None:
    """Seconds until the deadline (event-loop time), optionally capped; None means no limit."""
    if deadline is None:
        return cap
    left = max(0.0, deadline - asyncio.get_running_loop().time())
    return left if cap is None else min(cap, left)


async def resolve_city(name: str | None, llm: LlmProvider, limit: float | None = None) -> City | None:
    if not name or not name.strip():
        return None
    city = find_city(name)
    if city:
        return city
    try:
        guess = await asyncio.wait_for(llm.estimate_city(name.strip()), limit)
    except (TimeoutError, LlmError):
        log.info("City lookup timed out or failed; treating the city as unknown")
        return None
    if guess is None or (guess.lat == 0 and guess.lon == 0):
        return None
    if not (-90 <= guess.lat <= 90 and -180 <= guess.lon <= 180):
        return None
    profile = guess.season_profile if guess.season_profile in season_profiles() else "metro"
    code = (guess.country_code or "IN").strip().upper()
    return City(
        name=(guess.canonical_name or name).strip()[:100],
        lat=guess.lat,
        lon=guess.lon,
        tier=min(3, max(1, guess.tier)),
        airport=guess.airport,
        rail=guess.rail,
        season_profile=profile,
        island=guess.island,
        known=False,
        country=code if len(code) == 2 and code.isalpha() else "IN",
    )


def fallback_city(name: str, country_code: str | None) -> City:
    code = (country_code or "IN").upper()
    return City(name=name.strip()[:100], lat=0, lon=0, tier=2, airport=False, rail=False,
                season_profile=country(code).season_profile, known=False, country=code)


def stop_specs(req: TripRequest) -> list[tuple[str, str | None, int]]:
    if req.multi_city:
        return [(s.city, s.country_code, s.nights) for s in req.stops]
    return [(req.destination, None, req.nights)]


def visa_warnings(origin: City | None, stops: list[StopCtx]) -> list[str]:
    home = origin.country if origin else "IN"
    notes = []
    for code in dict.fromkeys(s.city.country for s in stops if s.located):
        note = country(code).visa_note if code != home else ""
        if note:
            notes.append(f"Visa ({country(code).name}): {note} Check official sources before booking.")
    return notes


# ---------- nodes ----------


async def intake(state: PlanState) -> PlanState:
    req, llm = state["request"], state["llm"]
    warnings: list[str] = []
    has_origin = bool(req.origin and req.origin.strip())
    limit = time_left(state.get("deadline"), state.get("city_timeout"))
    specs = stop_specs(req)
    resolved = await asyncio.gather(
        resolve_city(req.origin if has_origin else None, llm, limit),
        *(resolve_city(name, llm, limit) for name, _, _ in specs),
    )
    origin, cities = resolved[0], resolved[1:]

    stops: list[StopCtx] = []
    offset = 0
    for i, ((name, code, nights), city) in enumerate(zip(specs, cities, strict=True)):
        located = city is not None
        if city is None:
            warnings.append(f"Could not locate {name}; costs use typical mid-size city rates.")
            city = fallback_city(name, code)
        elif not city.known:
            warnings.append(f"{city.name} is not in our price catalogue; its estimates are approximate.")
        last = i == len(specs) - 1
        arrival = req.start_date + timedelta(days=offset)
        departure = req.end_date if last else arrival + timedelta(days=nights)
        stops.append(StopCtx(seq=i + 1, city=city, located=located, nights=nights, arrival=arrival,
                             departure=departure, season=season_for(city.season_profile, arrival, departure),
                             last=last))
        offset += nights

    if not has_origin:
        warnings.append("Add a starting city to include travel costs to the destination.")
    elif origin is None:
        warnings.append(f"Could not locate {req.origin}; travel costs to the destination are not included.")
    warnings += visa_warnings(origin, stops)

    main = max(stops, key=lambda s: s.nights)
    return {
        "fx": await fx_quote(req.currency),
        "origin_city": origin,
        "stops": stops,
        "dest_city": stops[0].city,
        "dest_located": stops[0].located,
        "season": main.season,
        "pace": resolve_pace(req),
        "interests": resolve_interests(req),
        "comfort": req.comfort_level,
        "modes": list(req.transport_modes),
        "budget": req.budget_amount,
        "intake_warnings": warnings,
        "loops": 0,
        "guard_warnings": [],
    }


def plan_legs(state: PlanState) -> list[LegCtx]:
    """Single destination: one round trip. Multi-city: one-way legs origin -> s1 -> ... -> sN -> origin.
    Legs touching a place that could not be located are skipped (indices stay stable)."""
    req, origin, stops = state["request"], state["origin_city"], state["stops"]
    if not req.multi_city:
        if origin is None or not stops[0].located:
            return []
        return [LegCtx(0, origin, stops[0].city, req.start_date, True, stops[0].season)]
    hops: list[tuple[City, City, date, StopCtx, bool]] = []
    if origin is not None:
        hops.append((origin, stops[0].city, req.start_date, stops[0], stops[0].located))
    for prev, nxt in zip(stops, stops[1:], strict=False):
        hops.append((prev.city, nxt.city, prev.departure, nxt, prev.located and nxt.located))
    if origin is not None:
        hops.append((stops[-1].city, origin, req.end_date, stops[-1], stops[-1].located))
    return [LegCtx(i, a, b, on, False, stop.season) for i, (a, b, on, stop, ok) in enumerate(hops) if ok]


def transport(state: PlanState) -> PlanState:
    req = state["request"]
    fx = state["fx"].rate
    no_overnight = (req.answers.get("overnightTravel") or "").strip().lower() == "no"
    warnings: list[str] = []
    legs = plan_legs(state)
    for leg in legs:
        args = (leg.origin, leg.destination)
        quotes = transport_quotes(*args, state["modes"], state["comfort"], req.travellers_count, leg.season,
                                  fx=fx, round_trip=leg.round_trip)
        if not quotes and state["modes"]:
            quotes = transport_quotes(*args, [], state["comfort"], req.travellers_count, leg.season,
                                      fx=fx, round_trip=leg.round_trip)
            if quotes:
                wanted = ", ".join(m.value.lower() for m in state["modes"])
                warnings.append(f"No {wanted} option between {leg.origin.name} and {leg.destination.name}; "
                                "showing the available options instead.")
        if no_overnight:
            quotes = [q for q in quotes if not is_overnight(q.option)] or quotes
        leg.quotes = quotes
        leg.chosen = recommend(quotes, state["comfort"], req.travellers_count, fx=fx)
    first = legs[0] if legs else None
    return {
        "legs": legs,
        "quotes": first.quotes if first else [],
        "chosen": first.chosen if first else None,
        "transport_warnings": warnings,
    }


def stay(state: PlanState) -> PlanState:
    req, fx = state["request"], state["fx"]
    items: list[CostItem] = []
    stays: dict[int, Stay] = {}
    rooms = rooms_for(req.travellers_count)
    for stop in state["stops"]:
        if stop.nights == 0:
            continue
        item, nightly = stay_item(stop.city, state["comfort"], stop.nights, req.travellers_count, stop.season,
                                  fx=fx.rate, currency=fx.quote, stop_seq=stop.seq if req.multi_city else None)
        items.append(item)
        stays[stop.seq] = Stay(tier=state["comfort"], nightly_rate=nightly, nights=stop.nights, rooms=rooms)
    single = None if req.multi_city else stays.get(1)
    return {"stay": single, "stop_stays": stays, "stay_items": items}


async def hints(state: PlanState) -> PlanState:
    """Optional grounded web price hints for flight/bus legs and stays (bounded per plan and per day)."""
    settings = get_settings()
    req = state["request"]
    if not settings.web_hints_available or settings.web_hints_per_plan == 0:
        return {"hints": {}, "web_hints_skipped": False}
    if not req.allow_web_hints:
        return {"hints": {}, "web_hints_skipped": True}

    fx = state["fx"].rate
    targets: list[tuple[str, PriceQuery, Decimal]] = []
    for leg in state.get("legs") or []:
        chosen = leg.chosen
        if (chosen and chosen.option.mode in HINT_MODES and chosen.per_unit > 0
                and leg.origin.known and leg.destination.known):
            query = PriceQuery(kind=chosen.option.mode.value, month=leg.on.strftime("%Y-%m"),
                               travel_class=chosen.option.travel_class, from_city=leg.origin.name,
                               to_city=leg.destination.name)
            targets.append((f"leg:{leg.index}", query, chosen.per_unit / fx))
    stays = state.get("stop_stays") or {}
    for stop in state["stops"]:
        model = stays.get(stop.seq)
        if model is not None and stop.city.known:
            query = PriceQuery(kind="STAY", month=stop.arrival.strftime("%Y-%m"),
                               travel_class=state["comfort"].value, city=stop.city.name)
            targets.append((f"stay:{stop.seq}", query, model.nightly_rate.expected / fx))
    targets = targets[: settings.web_hints_per_plan]

    budget = {"lookups": state.get("hint_lookups", 0), "skipped": False}

    async def lookup(query: PriceQuery):
        key = query.cache_key()
        hit, cached = hint_cache.get(key)
        if hit:
            return cached
        if budget["lookups"] >= settings.web_hints_per_plan:
            return None
        budget["lookups"] += 1
        try:
            found = await asyncio.wait_for(
                state["llm"].price_hint(query),
                time_left(state.get("deadline"), settings.price_hint_timeout_seconds),
            )
        except CapReached:
            budget["skipped"] = True
            return None
        except (TimeoutError, LlmError):
            return None
        hint_cache.put(key, found, settings.price_hint_cache_ttl_seconds)
        return found

    found = await asyncio.gather(*(lookup(q) for _, q, _ in targets))
    accepted: dict[str, AcceptedHint] = {}
    for (key, query, baseline), hint in zip(targets, found, strict=True):
        ok = accept(hint, baseline, fx, query)
        if ok:
            accepted[key] = ok
    return {"hints": accepted, "hint_lookups": budget["lookups"], "web_hints_skipped": budget["skipped"]}


def with_hint(item: CostItem, hint: AcceptedHint, expected: Decimal) -> CostItem:
    return item.model_copy(update={
        "amount": amount_range(expected, Confidence.MEDIUM),
        "source": WEB_HINT,
        "confidence": Confidence.MEDIUM,
        "source_url": hint.hint.source_url,
        "assumptions": (item.assumptions or "") + "; based on a web price hint",
    })


def costs(state: PlanState) -> PlanState:
    req, season = state["request"], state["season"]
    hinted = state.get("hints") or {}
    multi = req.multi_city
    items: list[CostItem] = []
    for leg in state.get("legs") or []:
        if leg.chosen is None:
            continue
        item = transport_item(leg.chosen, leg.origin.name, leg.destination.name, leg.season,
                              leg_index=leg.index if multi else None)
        hint = hinted.get(f"leg:{leg.index}")
        if hint:
            trips = 2 if leg.chosen.round_trip else 1
            item = with_hint(item, hint, hint.unit_price * req.travellers_count * trips)
            leg.chosen.option.cost = item.amount
        items.append(item)
    stops = {s.seq: s for s in state["stops"]}
    rooms = rooms_for(req.travellers_count)
    for item in state.get("stay_items") or []:
        seq = item.stop_seq or 1
        hint = hinted.get(f"stay:{seq}")
        if hint:
            item = with_hint(item, hint, hint.unit_price * stops[seq].nights * rooms)
        items.append(item)
    segments = [DaySegment(s.city, s.days, s.season) for s in state["stops"]]
    items += per_day_items_multi(segments, state["comfort"], req.travellers_count, state["pace"], season,
                                 fx=state["fx"].rate)
    items.append(misc_item(items, season))
    total = total_range(items)
    return {"items": items, "total": total, "status": budget_status(total.expected, state["budget"])}


def budget_guard(state: PlanState) -> PlanState:
    """Over budget: first lower comfort, then switch to the cheapest transport; bounded by max_loops."""
    req, loops = state["request"], state.get("loops", 0)
    keep = (req.answers.get("budgetConflict") or "").strip().lower() == "keep"
    if state["status"] is not BudgetStatus.OVER or keep or loops >= state["max_loops"]:
        return {"guard_done": True}

    warnings = list(state.get("guard_warnings", []))
    lower = downgrade(state["comfort"])
    if lower is not None:
        warnings.append(f"Comfort lowered from {state['comfort'].value.title()} to {lower.value.title()} "
                        "to fit your budget.")
        return {"comfort": lower, "loops": loops + 1, "guard_warnings": warnings, "guard_done": False}

    quotes, chosen = state.get("quotes") or [], state.get("chosen")
    if chosen and quotes:
        cheapest = min(quotes, key=lambda q: q.option.cost.expected)
        if cheapest.option.mode is not chosen.option.mode:
            warnings.append(f"Switched to {cheapest.option.mode.value.lower()} travel, the cheapest option, "
                            "to fit your budget.")
            return {"modes": [cheapest.option.mode], "loops": loops + 1, "guard_warnings": warnings,
                    "guard_done": False}
    return {"guard_done": True}


def after_guard(state: PlanState) -> str:
    return "itinerary" if state.get("guard_done") else "transport"


def trip_name(state: PlanState) -> str:
    names = [s.city.name for s in state["stops"]]
    return names[0] if len(names) == 1 else ", ".join(names[:-1]) + f" and {names[-1]}"


def stop_day_ranges(state: PlanState) -> tuple[tuple[str, int, int], ...]:
    if not state["request"].multi_city:
        return ()
    ranges, first = [], 1
    for s in state["stops"]:
        last = first + s.days - 1
        ranges.append((s.city.name, first, last))
        first = last + 1
    return tuple(ranges)


async def itinerary(state: PlanState) -> PlanState:
    req = state["request"]
    dest = trip_name(state)
    keep = state.get("keep_days")
    if keep and len(keep) == req.days:
        return {"draft": DayPlanDraft(trip_title=state.get("keep_title") or f"{req.days}-day {dest} trip",
                                      area_suggestion=state.get("keep_area") or "", days=keep)}

    budget, total, currency = state["budget"], state["total"], state["fx"].quote
    budget_note = (
        f"{currency} {money(budget)} total; estimated {currency} {total.expected} ({state['status'].value.lower()})"
        if budget else f"not specified; estimated {currency} {total.expected}"
    )
    chosen = state.get("chosen")
    ctx = DayPlanContext(
        destination=dest,
        origin=state["origin_city"].name if state["origin_city"] else req.origin,
        start_date=req.start_date,
        days=req.days,
        travellers=req.travellers_count,
        comfort_level=state["comfort"].value,
        pace=state["pace"],
        interests=state["interests"],
        season=state["season"].type.value,
        season_notes=state["season"].notes,
        arrival_mode=chosen.option.mode.value if chosen else None,
        budget_note=budget_note,
        stops=stop_day_ranges(state),
    )
    try:
        draft = await asyncio.wait_for(state["llm"].plan_days(ctx), time_left(state.get("deadline")))
    except (TimeoutError, LlmError):
        log.warning("Day plan unavailable (LLM timeout, error or daily cap); using the template outline")
        return {"draft": template_days(ctx),
                "itinerary_warnings": ["We couldn't personalise the day-by-day plan in time, so this is a simple "
                                       "outline. Regenerate or ask in chat to refine it."]}
    return {"draft": draft}


def to_day(index: int, day: LlmDay, start: date) -> DayPlan:
    return DayPlan(
        day=index + 1,
        date=start + timedelta(days=index),
        title=day.title.strip()[:200] or f"Day {index + 1}",
        summary=day.summary.strip()[:1000],
        activities=[
            Activity(time_of_day=a.time_of_day, title=a.title.strip()[:200] or "Free time",
                     description=a.description.strip()[:1000])
            for a in day.activities[:10]
        ],
    )


def _hinted_stay(model: Stay, hint: AcceptedHint | None) -> Stay:
    if hint is None:
        return model
    return model.model_copy(update={"nightly_rate": amount_range(hint.unit_price, Confidence.MEDIUM)})


def output(state: PlanState) -> PlanState:
    req, total, season, fx = state["request"], state["total"], state["season"], state["fx"]
    currency = fx.quote
    draft: DayPlanDraft = state["draft"]
    hinted = state.get("hints") or {}
    warnings = (list(state.get("intake_warnings", [])) + list(state.get("transport_warnings", []))
                + list(state.get("guard_warnings", [])) + list(state.get("itinerary_warnings", [])))
    budget: Decimal | None = state["budget"]
    if state["status"] is BudgetStatus.OVER and budget:
        warnings.append(f"Estimated cost exceeds your budget by about {currency} {money(total.expected - budget)}.")
    if any(i.confidence is Confidence.LOW for i in state["items"]):
        warnings.append("Some costs are rough estimates (low confidence); check live prices before booking.")
    warnings.append(f"Prices are indicative {season.type.value.lower()}-season estimates, not live quotes.")

    days = [to_day(i, d, req.start_date) for i, d in enumerate(sorted(draft.days, key=lambda d: d.day)[: req.days])]
    stay_model = state.get("stay")
    if stay_model is not None:
        stay_model = _hinted_stay(stay_model, hinted.get("stay:1")).model_copy(
            update={"area_suggestion": draft.area_suggestion.strip()[:200] or None})

    legs = state.get("legs") or []
    stops = state["stops"]
    stop_stays = state.get("stop_stays") or {}
    stop_plans, leg_plans = [], []
    if req.multi_city:
        stop_plans = [
            StopPlan(seq=s.seq, city=s.city.name, country_code=s.city.country, nights=s.nights,
                     arrival_date=s.arrival, departure_date=s.departure, season=s.season.type,
                     stay=_hinted_stay(stop_stays[s.seq], hinted.get(f"stay:{s.seq}")) if s.seq in stop_stays
                     else None)
            for s in stops
        ]
        leg_plans = [
            LegPlan(index=leg.index, from_city=leg.origin.name, to_city=leg.destination.name,
                    international=is_international(leg.origin, leg.destination),
                    options=[q.option for q in leg.quotes])
            for leg in legs
        ]
    links = build_links(
        [LegLink(leg.index if req.multi_city else None, leg.origin, leg.destination, leg.chosen.option.mode, leg.on)
         for leg in legs if leg.chosen],
        [StayLink(s.seq if req.multi_city else None, s.city, s.arrival, s.departure)
         for s in stops if s.nights > 0 and s.located],
        req.travellers_count,
        rooms_for(req.travellers_count),
    )
    web_hints = [WebHint(label=h.label, source_url=h.hint.source_url, source_title=h.hint.source_title,
                         search_suggestions_html=h.hint.search_suggestions_html) for h in hinted.values()]

    result = ItineraryResponse(
        trip_title=draft.trip_title.strip()[:200] or f"Trip to {trip_name(state)}",
        destination=state["dest_city"].name,
        origin=state["origin_city"].name if state["origin_city"] else req.origin,
        comfort_level=state["comfort"],
        total_estimated_cost_inr=money(total.expected / fx.rate),
        total_cost=total,
        budget_status=state["status"],
        season=SeasonInfo(type=season.type, multiplier=season.multiplier, notes=season.notes or None),
        transport_options=[q.option for q in state.get("quotes") or []],
        stay=stay_model,
        cost_breakdown=state["items"],
        warnings=warnings,
        days=days,
        currency=currency,
        total_estimated_cost=total.expected,
        fx=FxInfo(base=fx.base, quote=fx.quote, rate=fx.rate, as_of=fx.as_of, source=fx.source),
        stops=stop_plans,
        legs=leg_plans,
        booking_links=links,
        web_hints=web_hints,
        web_hints_skipped=bool(state.get("web_hints_skipped")),
    )
    return {"result": result}
