"""Global daily caps on Gemini usage (in memory, per process; resets at UTC midnight).

Over the cap every call degrades to the same fallback used for errors: template day plans, unknown cities,
deterministic stop ranking and no web price hints.
"""

import threading
from datetime import UTC, date, datetime

from app.llm.base import CapReached, DayPlanContext, LlmProvider, PriceHint, PriceQuery, RefineContext, StopRankContext
from app.llm.models import CityGuess, DayPlanDraft, RefineDecision, StopRanking


class DailyCounter:
    """cap <= 0 means unlimited when unlimited_when_zero, otherwise nothing is allowed."""

    def __init__(self, cap: int, *, unlimited_when_zero: bool) -> None:
        self.cap = cap
        self.unlimited_when_zero = unlimited_when_zero
        self._day: date | None = None
        self._used = 0
        self._lock = threading.Lock()

    def try_acquire(self) -> bool:
        if self.cap <= 0:
            return self.unlimited_when_zero
        with self._lock:
            today = datetime.now(UTC).date()
            if today != self._day:
                self._day, self._used = today, 0
            if self._used >= self.cap:
                return False
            self._used += 1
            return True


class CappedLlmProvider:
    def __init__(self, inner: LlmProvider, llm_cap: int, grounding_cap: int) -> None:
        self.inner = inner
        self.calls = DailyCounter(llm_cap, unlimited_when_zero=True)
        self.grounding = DailyCounter(grounding_cap, unlimited_when_zero=False)

    async def plan_days(self, ctx: DayPlanContext) -> DayPlanDraft:
        if not self.calls.try_acquire():
            raise CapReached("Daily LLM cap reached")
        return await self.inner.plan_days(ctx)

    async def estimate_city(self, name: str) -> CityGuess | None:
        if not self.calls.try_acquire():
            return None
        return await self.inner.estimate_city(name)

    async def refine(self, ctx: RefineContext) -> RefineDecision:
        if not self.calls.try_acquire():
            raise CapReached("Daily LLM cap reached")
        return await self.inner.refine(ctx)

    async def rank_stops(self, ctx: StopRankContext) -> StopRanking | None:
        if not self.calls.try_acquire():
            return None
        return await self.inner.rank_stops(ctx)

    async def price_hint(self, query: PriceQuery) -> PriceHint | None:
        if not self.grounding.try_acquire() or not self.calls.try_acquire():
            raise CapReached("Daily grounding cap reached")
        return await self.inner.price_hint(query)
