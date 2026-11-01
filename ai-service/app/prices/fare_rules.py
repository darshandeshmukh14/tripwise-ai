"""Rule-based Indian Railways fares (see data/irctc.yaml). Higher confidence than the generic per-km estimate."""

from decimal import ROUND_HALF_UP, Decimal
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml

_DATA = Path(__file__).parent / "data" / "irctc.yaml"


@lru_cache
def _rules() -> dict[str, Any]:
    with _DATA.open(encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def irctc_fare(distance_km: Decimal, travel_class: str) -> Decimal | None:
    """Per-person one-way fare in INR for a reserved class, or None if the class is not modelled."""
    rules = _rules()
    cls = rules["classes"].get(travel_class)
    if cls is None or distance_km <= 0:
        return None
    km = max(distance_km, Decimal(str(rules["minDistanceKm"][travel_class])))
    base, lower = Decimal(0), Decimal(0)
    for slab in cls["slabs"]:
        upper = Decimal(str(slab["upToKm"])) if slab["upToKm"] is not None else km
        if km <= lower:
            break
        base += (min(km, upper) - lower) * Decimal(str(slab["perKm"]))
        lower = upper
    fare = base + Decimal(str(cls["reservation"]))
    if km >= rules["superfastMinKm"]:
        fare += Decimal(str(cls["superfast"]))
    if cls["ac"]:
        fare *= 1 + Decimal(str(rules["gstAc"]))
    return fare.quantize(Decimal("1"), rounding=ROUND_HALF_UP)
