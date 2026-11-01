"""Multi-city helpers: route validation (6 stops, 400 km road legs) and nearby-place suggestions."""

import asyncio
import logging
from datetime import timedelta
from decimal import Decimal
from typing import Annotated

from fastapi import APIRouter, Depends

from app.config import Settings, get_settings
from app.graph.nodes import resolve_city, resolve_interests
from app.llm.base import LlmError, LlmProvider, StopRankContext
from app.llm.factory import get_llm
from app.pricing.catalog import City, all_cities, clusters, is_supported_country, normalize, season_for
from app.pricing.engine import (
    DaySegment,
    money,
    per_day_items_multi,
    recommend,
    road_connected,
    stay_item,
    total_range,
    transport_quotes,
)
from app.pricing.fx import fx_quote
from app.route import MAX_LEG_KM, MAX_STOPS, best_order, check_route, road_km
from app.schemas import (
    AmountRange,
    RouteLeg,
    RouteRequest,
    RouteResponse,
    RouteStop,
    Season,
    StopSuggestion,
    SuggestRequest,
    SuggestResponse,
    TripRequest,
)

log = logging.getLogger(__name__)

router = APIRouter(prefix="/internal/v1/stops", tags=["stops"])

ROAD_SPEED_KMH = Decimal(45)


@router.post("/route", response_model=RouteResponse, response_model_by_alias=True)
async def check_stops_route(
    request: RouteRequest,
    llm: Annotated[LlmProvider, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> RouteResponse:
    limit = settings.city_lookup_timeout_seconds
    has_origin = bool(request.origin and request.origin.strip())
    resolved = await asyncio.gather(
        resolve_city(request.origin if has_origin else None, llm, limit),
        *(resolve_city(s.city, llm, limit) for s in request.stops),
    )
    origin, cities = resolved[0], list(resolved[1:])
    violations: list[str] = []
    for spec, city in zip(request.stops, cities, strict=True):
        if city is None:
            violations.append(f"Could not locate {spec.city}")
        elif not is_supported_country(city.country):
            violations.append(f"{city.name} is in a country we do not support yet")

    order = list(range(len(cities)))
    if request.optimize_order and not violations:
        order = best_order([c for c in cities if c is not None], origin)
    specs = [request.stops[i] for i in order]
    ordered = [cities[i] for i in order]

    stops = [
        RouteStop(seq=i + 1, city=city.name if city else spec.city,
                  country_code=city.country if city else (spec.country_code or "IN").upper(),
                  nights=spec.nights, known=bool(city and city.known))
        for i, (spec, city) in enumerate(zip(specs, ordered, strict=True))
    ]
    legs: list[RouteLeg] = []
    for i, (a, b) in enumerate(zip(ordered, ordered[1:], strict=False)):
        if a is None or b is None:
            legs.append(RouteLeg(from_city=stops[i].city, to_city=stops[i + 1].city, road_km=None,
                                 within_limit=False))
            continue
        leg = check_route([a, b])[0]
        legs.append(RouteLeg(from_city=a.name, to_city=b.name, road_km=leg.km, within_limit=leg.ok))
        if not leg.ok and leg.reason:
            violations.append(leg.reason)
    return RouteResponse(stops=stops, legs=legs, valid=not violations, violations=violations)


def _cluster_mates(names: set[str]) -> set[str]:
    mates: set[str] = set()
    for members in clusters().values():
        keys = {normalize(m) for m in members}
        if keys & names:
            mates |= keys
    return mates


def _add(a: AmountRange, b: AmountRange) -> AmountRange:
    return AmountRange(min=a.min + b.min, expected=a.expected + b.expected, max=a.max + b.max)


@router.post("/suggest", response_model=SuggestResponse, response_model_by_alias=True)
async def suggest_stops(
    request: SuggestRequest,
    llm: Annotated[LlmProvider, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> SuggestResponse:
    trip: TripRequest = request.trip
    names = [s.city for s in trip.stops] if trip.multi_city else [trip.destination]
    if len(names) >= MAX_STOPS:
        return SuggestResponse(suggestions=[])
    limit = settings.city_lookup_timeout_seconds
    current = [c for c in await asyncio.gather(*(resolve_city(n, llm, limit) for n in names)) if c is not None]
    if not current:
        return SuggestResponse(suggestions=[])
    origin = await resolve_city(trip.origin, llm, limit) if trip.origin else None

    taken = {normalize(c.name) for c in current} | ({normalize(origin.name)} if origin else set())
    mates = _cluster_mates(taken)
    fx = await fx_quote(trip.currency)
    window_end = trip.end_date
    candidates: list[tuple[bool, Decimal, City, City]] = []
    for city in all_cities():
        if normalize(city.name) in taken:
            continue
        near = min(current, key=lambda c, x=city: road_km(c, x))
        km = road_km(near, city)
        if km > MAX_LEG_KM or km < 1 or not road_connected(near, city):
            continue
        candidates.append((normalize(city.name) not in mates, km, city, near))
    candidates.sort(key=lambda t: (t[0], t[1]))

    suggestions: list[StopSuggestion] = []
    for not_mate, km, city, near in candidates:
        if len(suggestions) >= request.limit * 2:
            break
        nights = 1 if km < 150 else 2
        season = season_for(city.season_profile, window_end - timedelta(days=nights), window_end)
        if season.type is Season.OFF:
            continue
        quotes = transport_quotes(near, city, [], trip.comfort_level, trip.travellers_count, season,
                                  fx=fx.rate, round_trip=False)
        chosen = recommend(quotes, trip.comfort_level, trip.travellers_count, fx=fx.rate)
        stay, _ = stay_item(city, trip.comfort_level, nights, trip.travellers_count, season,
                            fx=fx.rate, currency=fx.quote)
        days = per_day_items_multi([DaySegment(city, nights, season)], trip.comfort_level, trip.travellers_count,
                                   "balanced", season, fx=fx.rate)
        extra = total_range([stay, *days])
        if chosen is not None:
            extra = _add(extra, chosen.option.cost)
        hours = chosen.option.duration_hours if chosen else money(km / ROAD_SPEED_KMH)
        reason = (f"Often combined with {near.name}" if not not_mate else f"About {km} km from {near.name}")
        suggestions.append(StopSuggestion(
            city=city.name, country_code=city.country, near_stop=near.name, distance_km=km,
            travel_hours=hours, suggested_nights=nights, extra_cost=extra, currency=fx.quote,
            season=season.type, reason=reason,
        ))

    ranked = await _rank(llm, [c.name for c in current], resolve_interests(trip), suggestions, limit)
    return SuggestResponse(suggestions=ranked[: request.limit])


async def _rank(llm: LlmProvider, stops: list[str], interests: list[str], items: list[StopSuggestion],
                limit: float | None) -> list[StopSuggestion]:
    """LLM may reorder and re-explain the deterministic candidates; unknown names are ignored."""
    if len(items) < 2:
        return items
    ctx = StopRankContext(stops=stops, interests=interests,
                          candidates=[f"{s.city} ({s.country_code}): {s.reason}" for s in items])
    try:
        ranking = await asyncio.wait_for(llm.rank_stops(ctx), limit)
    except (TimeoutError, LlmError):
        ranking = None
    if ranking is None:
        return items
    by_name = {normalize(s.city): s for s in items}
    ordered: list[StopSuggestion] = []
    for r in ranking.stops:
        item = by_name.pop(normalize(r.city), None)
        if item is not None:
            reason = r.reason.strip()[:160] or item.reason
            ordered.append(item.model_copy(update={"reason": reason}))
    return ordered + list(by_name.values())
