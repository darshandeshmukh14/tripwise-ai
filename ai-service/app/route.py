"""Multi-city route rules: at most 6 stops and at most 400 km by road between consecutive stops.

Road distance is approximated as straight-line distance x ROAD_FACTOR. The legs from and back to the
origin are exempt (they can be flights). Islands of the same island group count as road-connected.
"""

from dataclasses import dataclass
from decimal import Decimal
from itertools import permutations

from app.pricing.catalog import City
from app.pricing.engine import haversine_km, road_connected

MAX_STOPS = 6
MAX_LEG_KM = Decimal(400)
ROAD_FACTOR = Decimal("1.2")


def road_km(a: City, b: City) -> Decimal:
    return (Decimal(str(haversine_km(a, b))) * ROAD_FACTOR).quantize(Decimal("1"))


@dataclass(frozen=True)
class LegCheck:
    a: City
    b: City
    km: Decimal
    ok: bool
    reason: str | None


def check_leg(a: City, b: City) -> LegCheck:
    km = road_km(a, b)
    if not road_connected(a, b):
        return LegCheck(a, b, km, False, f"{a.name} and {b.name} are not connected by road")
    if km > MAX_LEG_KM:
        return LegCheck(a, b, km, False, f"{a.name} to {b.name} is about {km} km by road (max {MAX_LEG_KM} km)")
    return LegCheck(a, b, km, True, None)


def check_route(stops: list[City]) -> list[LegCheck]:
    return [check_leg(a, b) for a, b in zip(stops, stops[1:], strict=False)]


def best_order(stops: list[City], origin: City | None = None) -> list[int]:
    """Visiting order (indices) with the fewest violations, then the shortest total distance.

    The first stop stays first when there is no origin (it is the trip's main destination)."""
    n = len(stops)
    if n <= 2 and origin is None:
        return list(range(n))
    fixed_first = origin is None
    rest = range(1, n) if fixed_first else range(n)
    best: tuple[int, Decimal] | None = None
    best_perm: list[int] = list(range(n))
    for perm in permutations(rest):
        order = [0, *perm] if fixed_first else list(perm)
        legs = check_route([stops[i] for i in order])
        violations = sum(1 for leg in legs if not leg.ok)
        total = sum((leg.km for leg in legs), Decimal(0))
        if origin is not None:
            total += road_km(origin, stops[order[0]]) + road_km(stops[order[-1]], origin)
        key = (violations, total)
        if best is None or key < best:
            best, best_perm = key, order
    return best_perm
