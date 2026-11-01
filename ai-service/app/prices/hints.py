"""Web price hints: a grounded LLM lookup that may replace a deterministic estimate when it is plausible.

A hint is accepted only within [ACCEPT_LOW, ACCEPT_HIGH] x the deterministic per-unit estimate; it then becomes
a WEB_HINT item with MEDIUM confidence and a source link. Results are cached (also misses) per
kind + route/city + class + month.
"""

import time
from dataclasses import dataclass
from decimal import Decimal

from app.llm.base import PriceHint, PriceQuery

ACCEPT_LOW = Decimal("0.5")
ACCEPT_HIGH = Decimal("2")


@dataclass(frozen=True)
class AcceptedHint:
    hint: PriceHint
    unit_price: Decimal  # trip currency, per person one way (transport) or per room night (stay)
    label: str


class HintCache:
    def __init__(self) -> None:
        self._entries: dict[str, tuple[float, PriceHint | None]] = {}

    def get(self, key: str) -> tuple[bool, PriceHint | None]:
        entry = self._entries.get(key)
        if entry is None or entry[0] < time.monotonic():
            return False, None
        return True, entry[1]

    def put(self, key: str, hint: PriceHint | None, ttl: float) -> None:
        if len(self._entries) > 5000:
            self._entries.clear()
        self._entries[key] = (time.monotonic() + ttl, hint)

    def clear(self) -> None:
        self._entries.clear()


hint_cache = HintCache()


def plausible(hint: PriceHint, baseline_inr: Decimal) -> bool:
    if baseline_inr <= 0:
        return False
    price = Decimal(str(hint.price_inr))
    return baseline_inr * ACCEPT_LOW <= price <= baseline_inr * ACCEPT_HIGH


def accept(hint: PriceHint | None, baseline_inr: Decimal, fx: Decimal, query: PriceQuery) -> AcceptedHint | None:
    if hint is None or not plausible(hint, baseline_inr):
        return None
    return AcceptedHint(hint=hint, unit_price=Decimal(str(hint.price_inr)) * fx, label=query.describe())
