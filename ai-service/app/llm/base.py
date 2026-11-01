from dataclasses import dataclass, field
from datetime import date
from typing import Protocol

from app.llm.models import CityGuess, DayPlanDraft, RefineDecision, StopRanking


class LlmError(Exception):
    """The LLM call failed, timed out or returned unusable output."""


class CapReached(LlmError):
    """A daily LLM or grounding cap was reached; callers fall back to deterministic output."""


@dataclass(frozen=True)
class DayPlanContext:
    destination: str
    origin: str | None
    start_date: date
    days: int
    travellers: int
    comfort_level: str
    pace: str
    interests: list[str]
    season: str
    season_notes: str
    arrival_mode: str | None
    budget_note: str
    # Multi-city: (city, first day, last day) in visiting order; empty for single-destination trips
    stops: tuple[tuple[str, int, int], ...] = ()


@dataclass(frozen=True)
class RefineContext:
    trip_summary: str
    plan_summary: str
    days_json: str
    history: list[tuple[str, str]] = field(default_factory=list)
    message: str = ""
    trip_days: int = 1


@dataclass(frozen=True)
class StopRankContext:
    stops: list[str]
    interests: list[str]
    candidates: list[str]  # "City (CC): note" lines; the model may only reorder and explain these


@dataclass(frozen=True)
class PriceQuery:
    kind: str  # FLIGHT | BUS | STAY
    month: str  # YYYY-MM
    travel_class: str
    from_city: str | None = None
    to_city: str | None = None
    city: str | None = None

    def cache_key(self) -> str:
        return "|".join([self.kind, self.from_city or "", self.to_city or "", self.city or "",
                         self.travel_class, self.month]).lower()

    def describe(self) -> str:
        if self.kind == "STAY":
            return f"{self.travel_class.lower()} hotel room per night in {self.city} ({self.month})"
        return (f"one-way {self.kind.lower()} ticket {self.from_city} to {self.to_city}, "
                f"{self.travel_class} ({self.month})")


@dataclass(frozen=True)
class PriceHint:
    price_inr: float  # per person one way (FLIGHT/BUS) or per room per night (STAY)
    source_url: str
    source_title: str | None = None
    search_suggestions_html: str | None = None


class LlmProvider(Protocol):
    async def plan_days(self, ctx: DayPlanContext) -> DayPlanDraft: ...

    async def estimate_city(self, name: str) -> CityGuess | None: ...

    async def refine(self, ctx: RefineContext) -> RefineDecision: ...

    async def rank_stops(self, ctx: StopRankContext) -> StopRanking | None: ...

    async def price_hint(self, query: PriceQuery) -> PriceHint | None: ...
