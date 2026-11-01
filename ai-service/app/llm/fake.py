"""Deterministic offline provider for tests and local development without a Gemini key (LLM_PROVIDER=fake)."""

import re

from app.llm.base import DayPlanContext, PriceHint, PriceQuery, RefineContext, StopRankContext
from app.llm.models import (
    CityGuess,
    DayPlanDraft,
    RefineAction,
    RefineChanges,
    RefineDecision,
    StopRanking,
)
from app.llm.template import template_days
from app.schemas import ComfortLevel, TransportMode


class FakeLlmProvider:
    """Offline provider. Set `hint_prices` (cache key -> INR) to simulate grounded web price hints."""

    def __init__(self) -> None:
        self.hint_prices: dict[str, float] = {}
        self.hint_calls = 0

    async def plan_days(self, ctx: DayPlanContext) -> DayPlanDraft:
        return template_days(ctx)

    async def estimate_city(self, name: str) -> CityGuess | None:
        return None

    async def rank_stops(self, ctx: StopRankContext) -> StopRanking | None:
        return None

    async def price_hint(self, query: PriceQuery) -> PriceHint | None:
        self.hint_calls += 1
        price = self.hint_prices.get(query.cache_key())
        if price is None:
            return None
        return PriceHint(price_inr=price, source_url="https://example.com/fares", source_title="example.com",
                         search_suggestions_html="<div>search</div>")

    async def refine(self, ctx: RefineContext) -> RefineDecision:
        text = ctx.message.lower()
        changes = RefineChanges()
        if re.search(r"\b(cheaper|budget|save|lower cost)\b", text):
            changes.comfort_level = ComfortLevel.BUDGET
        elif re.search(r"\b(luxury|upgrade|premium)\b", text):
            changes.comfort_level = ComfortLevel.LUXURY if "luxury" in text else ComfortLevel.PREMIUM
        modes = [m for m in TransportMode if re.search(rf"\b{m.value.lower()}\b", text)]
        if modes:
            changes.transport_modes = modes
        if re.search(r"\b(relaxed|slower)\b", text):
            changes.pace = "relaxed"
        elif re.search(r"\b(packed|more activities)\b", text):
            changes.pace = "packed"

        if changes.model_dump(exclude_none=True):
            return RefineDecision(assistant_message="I've updated your plan with those changes.",
                                  action=RefineAction.REPLAN, changes=changes)
        return RefineDecision(assistant_message=f"Noted: {ctx.message.strip()[:200]}", action=RefineAction.NONE)
