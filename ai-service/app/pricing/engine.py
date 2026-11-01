"""Deterministic cost estimation. The LLM never does arithmetic: every number in a plan comes from here.

Base rates are INR for India. Other countries are scaled by their price index, and every amount is multiplied
by `fx` (units of the trip currency per 1 INR), so results are already in the trip currency.
"""

import math
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from app.prices.fare_rules import irctc_fare
from app.pricing.catalog import City, SeasonEstimate, country, rates
from app.schemas import (
    AmountRange,
    BudgetStatus,
    ComfortLevel,
    Confidence,
    CostCategory,
    CostItem,
    TransportMode,
    TransportOption,
)

CENT = Decimal("0.01")
ONE = Decimal(1)
COMFORT_ORDER = [ComfortLevel.BUDGET, ComfortLevel.STANDARD, ComfortLevel.PREMIUM, ComfortLevel.LUXURY]
OVERNIGHT_HOURS = Decimal("10")
ESTIMATE, FARE_RULE, WEB_HINT = "ESTIMATE", "FARE_RULE", "WEB_HINT"


def money(value: Decimal | float | int) -> Decimal:
    return Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP)


def amount_range(expected: Decimal, confidence: Confidence) -> AmountRange:
    spread = Decimal(str(rates()["spread"][confidence.value]))
    exp = money(expected)
    return AmountRange(min=money(exp * (1 - spread)), expected=exp, max=money(exp * (1 + spread)))


def haversine_km(a: City, b: City) -> float:
    r = 6371.0
    p1, p2 = math.radians(a.lat), math.radians(b.lat)
    dp, dl = p2 - p1, math.radians(b.lon - a.lon)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def seasonal(multiplier: Decimal, key: str) -> Decimal:
    """Applies only the season-sensitive share of the multiplier (e.g. train tariffs are fixed)."""
    sensitivity = Decimal(str(rates()["seasonSensitivity"][key]))
    return 1 + (multiplier - 1) * sensitivity


def downgrade(level: ComfortLevel) -> ComfortLevel | None:
    idx = COMFORT_ORDER.index(level)
    return COMFORT_ORDER[idx - 1] if idx > 0 else None


def price_index(city: City) -> Decimal:
    return country(city.country).price_index


def same_island_group(a: City, b: City) -> bool:
    return bool(a.island_group) and a.island_group == b.island_group


def is_international(a: City, b: City) -> bool:
    return a.country != b.country


def road_connected(a: City, b: City) -> bool:
    """Bus/car possible: same country (and not split by sea) or a land-border neighbour."""
    if (a.island or b.island) and not same_island_group(a, b):
        return False
    return a.country == b.country or b.country in country(a.country).road_neighbours


@dataclass(frozen=True)
class TransportQuote:
    option: TransportOption
    confidence: Confidence
    assumptions: str
    source: str = ESTIMATE
    round_trip: bool = True
    international: bool = False
    per_unit: Decimal = Decimal(0)  # expected per-person (per-vehicle for CAR) one-way price, trip currency


def transport_quotes(
    origin: City,
    destination: City,
    modes: list[TransportMode],
    comfort: ComfortLevel,
    travellers: int,
    season: SeasonEstimate,
    *,
    fx: Decimal = ONE,
    round_trip: bool = True,
) -> list[TransportQuote]:
    """Quotes for each feasible mode (all modes when `modes` is empty); round trip or one way."""
    straight = haversine_km(origin, destination)
    if straight < 1:
        return []
    candidates = modes or list(TransportMode)
    quotes = []
    for mode in candidates:
        quote = _quote(mode, origin, destination, straight, comfort, travellers, season, fx, round_trip)
        if quote:
            quotes.append(quote)
    return quotes


def _quote(
    mode: TransportMode,
    origin: City,
    destination: City,
    straight_km: float,
    comfort: ComfortLevel,
    travellers: int,
    season: SeasonEstimate,
    fx: Decimal,
    round_trip: bool,
) -> TransportQuote | None:
    cfg = rates()["transport"][mode.value]
    international = is_international(origin, destination)
    if mode is TransportMode.FLIGHT:
        if not (origin.airport and destination.airport) or straight_km < cfg["minDistanceKm"]:
            return None
    elif not road_connected(origin, destination):
        return None
    if mode is TransportMode.TRAIN and (international or not (origin.rail and destination.rail)):
        return None

    distance = Decimal(str(straight_km * cfg["routeFactor"])).quantize(Decimal("1"))
    if distance > cfg["maxDistanceKm"]:
        return None

    travel_class = cfg["classByComfort"][comfort.value]
    cls = cfg["classes"][travel_class]
    hours = (distance / Decimal(str(cfg["speedKmh"])) + Decimal(str(cfg["overheadHours"]))).quantize(
        Decimal("0.1")
    )
    if mode is TransportMode.FLIGHT:
        local = ONE if international else price_index(origin)
    else:
        local = (price_index(origin) + price_index(destination)) / 2
    factor = local * fx
    confidence = Confidence(cfg["confidence"])
    source = ESTIMATE

    if mode is TransportMode.CAR:
        vehicles = math.ceil(travellers / cls["seats"])
        fuel = distance / Decimal(str(cls["mileageKmpl"])) * Decimal(str(cfg["fuelPricePerLitre"]))
        tolls = distance * Decimal(str(cfg["tollPerKm"]))
        per_unit = (fuel + tolls) * factor
        one_way = per_unit * vehicles
        assumptions = (
            f"{vehicles} {travel_class.lower().replace('_', ' ')}(s), ~{distance} km each way; "
            "fuel and tolls only (own or pre-arranged car)"
        )
    else:
        fare = irctc_fare(distance, travel_class) if mode is TransportMode.TRAIN and origin.country == "IN" else None
        if fare is not None:
            per_person = fare
            source, confidence = FARE_RULE, Confidence.HIGH
        else:
            per_person = Decimal(str(cls["base"])) + distance * Decimal(str(cls["perKm"]))
            if mode is TransportMode.FLIGHT and international:
                per_person += Decimal(str(cfg["internationalSurcharge"][travel_class]))
        per_unit = per_person * factor
        one_way = per_unit * travellers
        assumptions = f"{travel_class} class, {travellers} traveller(s), ~{distance} km each way"
        if source == FARE_RULE:
            assumptions += "; Indian Railways fare rules (reservation, superfast and GST included)"

    if not (origin.known and destination.known):
        confidence = Confidence.LOW
        source = ESTIMATE
        assumptions += "; city location estimated"

    season_factor = seasonal(season.multiplier, mode.value)
    expected = one_way * (2 if round_trip else 1) * season_factor
    notes = None
    if mode in (TransportMode.BUS, TransportMode.TRAIN) and hours > OVERNIGHT_HOURS:
        notes = "Likely an overnight journey"
    elif international and mode is TransportMode.FLIGHT:
        notes = "International flight; check visa and baggage rules"
    option = TransportOption(
        mode=mode,
        travel_class=travel_class,
        distance_km=distance,
        duration_hours=hours,
        cost=amount_range(expected, confidence),
        comfort_score=int(cls["comfort"]),
        notes=notes,
    )
    return TransportQuote(
        option=option,
        confidence=confidence,
        assumptions=assumptions + (", round trip" if round_trip else ", one way"),
        source=source,
        round_trip=round_trip,
        international=international,
        per_unit=per_unit * season_factor,
    )


def recommend(
    quotes: list[TransportQuote], comfort: ComfortLevel, travellers: int, *, fx: Decimal = ONE
) -> TransportQuote | None:
    """Lowest cost + value-of-time score; marks the winner as recommended."""
    if not quotes:
        return None
    vot = Decimal(str(rates()["valueOfTimePerHour"][comfort.value])) * fx

    def score(q: TransportQuote) -> Decimal:
        trips = 2 if q.round_trip else 1
        return q.option.cost.expected + q.option.duration_hours * trips * travellers * vot

    best = min(quotes, key=score)
    for q in quotes:
        q.option.recommended = q is best
    return best


def transport_item(
    quote: TransportQuote, origin: str, destination: str, season: SeasonEstimate, *, leg_index: int | None = None
) -> CostItem:
    o = quote.option
    route = f"{origin} to {destination} and back" if quote.round_trip else f"{origin} to {destination}"
    return CostItem(
        category=CostCategory.TRANSPORT,
        mode=o.mode,
        label=f"{route} by {o.mode.value.lower()} ({o.travel_class})",
        amount=o.cost,
        season=season.type,
        source=quote.source,
        confidence=quote.confidence,
        assumptions=quote.assumptions,
        leg_index=leg_index,
    )


def rooms_for(travellers: int) -> int:
    return math.ceil(travellers / rates()["travellersPerRoom"])


def nightly_rate(tier: int, comfort: ComfortLevel, season: SeasonEstimate, factor: Decimal = ONE) -> Decimal:
    base = Decimal(str(rates()["stayNightly"][tier][comfort.value]))
    return base * seasonal(season.multiplier, "STAY") * factor


def stay_item(
    city: City,
    comfort: ComfortLevel,
    nights: int,
    travellers: int,
    season: SeasonEstimate,
    *,
    fx: Decimal = ONE,
    currency: str = "INR",
    stop_seq: int | None = None,
) -> tuple[CostItem, AmountRange]:
    rooms = rooms_for(travellers)
    confidence = Confidence.MEDIUM if city.known else Confidence.LOW
    rate = nightly_rate(city.tier, comfort, season, price_index(city) * fx)
    where = f" in {city.name}" if stop_seq else ""
    item = CostItem(
        category=CostCategory.STAY,
        label=f"{comfort.value.title()} stay{where}, {nights} night(s) x {rooms} room(s)",
        amount=amount_range(rate * nights * rooms, confidence),
        season=season.type,
        confidence=confidence,
        assumptions=f"~{currency} {money(rate)} per room per night ({season.type.value.lower()} season)",
        stop_seq=stop_seq,
    )
    return item, amount_range(rate, confidence)


@dataclass(frozen=True)
class DaySegment:
    city: City
    days: int
    season: SeasonEstimate


def per_day_items(
    city: City, comfort: ComfortLevel, days: int, travellers: int, pace: str, season: SeasonEstimate,
    *, fx: Decimal = ONE,
) -> list[CostItem]:
    return per_day_items_multi([DaySegment(city, days, season)], comfort, travellers, pace, season, fx=fx)


def per_day_items_multi(
    segments: list[DaySegment], comfort: ComfortLevel, travellers: int, pace: str, season: SeasonEstimate,
    *, fx: Decimal = ONE,
) -> list[CostItem]:
    """Food, activities and local transport summed over all stops (one line each)."""
    r = rates()
    pace_factor = Decimal(str(r["paceFactor"].get(pace, 1.0)))
    vehicles = math.ceil(travellers / r["travellersPerLocalVehicle"])
    food = activity = local = Decimal(0)
    total_days = 0
    for seg in segments:
        factor = Decimal(str(r["tierFactor"][seg.city.tier])) * price_index(seg.city) * fx
        food += Decimal(str(r["foodPerDay"][comfort.value])) * factor * seg.days * travellers
        activity += (
            Decimal(str(r["activityPerDay"][comfort.value])) * factor * pace_factor * seg.days * travellers
            * seasonal(seg.season.multiplier, "ACTIVITY")
        )
        local += (
            Decimal(str(r["localTransportPerDay"][comfort.value])) * factor * seg.days * vehicles
            * seasonal(seg.season.multiplier, "LOCAL_TRANSPORT")
        )
        total_days += seg.days
    where = "at each stop" if len(segments) > 1 else "at destination"
    return [
        CostItem(category=CostCategory.FOOD, label=f"Food for {travellers} x {total_days} day(s)",
                 amount=amount_range(food, Confidence.MEDIUM), season=season.type,
                 confidence=Confidence.MEDIUM, assumptions=f"{comfort.value.lower()} restaurants"),
        CostItem(category=CostCategory.ACTIVITY, label="Sightseeing, entry fees and activities",
                 amount=amount_range(activity, Confidence.LOW), season=season.type,
                 confidence=Confidence.LOW, assumptions=f"{pace} pace"),
        CostItem(category=CostCategory.LOCAL_TRANSPORT, label=f"Local transport {where}",
                 amount=amount_range(local, Confidence.LOW), season=season.type,
                 confidence=Confidence.LOW, assumptions=f"{vehicles} vehicle(s) per day: cabs, autos, transfers"),
    ]


def misc_item(items: list[CostItem], season: SeasonEstimate) -> CostItem:
    subtotal = sum((i.amount.expected for i in items), Decimal(0))
    share = Decimal(str(rates()["miscShare"]))
    return CostItem(category=CostCategory.MISC, label="Buffer for tips, shopping and surprises",
                    amount=amount_range(subtotal * share, Confidence.LOW), season=season.type,
                    confidence=Confidence.LOW, assumptions=f"{int(share * 100)}% of other costs")


def total_range(items: list[CostItem]) -> AmountRange:
    return AmountRange(
        min=sum((i.amount.min for i in items), Decimal(0)),
        expected=sum((i.amount.expected for i in items), Decimal(0)),
        max=sum((i.amount.max for i in items), Decimal(0)),
    )


def budget_status(total: Decimal, budget: Decimal | None) -> BudgetStatus:
    """Same thresholds as Spring's BudgetStatusCalculator."""
    if budget is None or budget <= 0:
        return BudgetStatus.UNKNOWN
    if total > budget:
        return BudgetStatus.OVER
    if total > budget * Decimal("0.9"):
        return BudgetStatus.NEAR
    return BudgetStatus.WITHIN
