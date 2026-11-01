from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from typing import TypedDict

from app.llm.base import LlmProvider
from app.llm.models import DayPlanDraft, LlmDay
from app.prices.hints import AcceptedHint
from app.pricing.catalog import City, SeasonEstimate
from app.pricing.engine import TransportQuote
from app.pricing.fx import FxQuote
from app.schemas import (
    AmountRange,
    BudgetStatus,
    ComfortLevel,
    CostItem,
    ItineraryResponse,
    Stay,
    TransportMode,
    TripRequest,
)


@dataclass(frozen=True)
class StopCtx:
    seq: int
    city: City  # a generic fallback when the place could not be located
    located: bool
    nights: int
    arrival: date
    departure: date
    season: SeasonEstimate
    last: bool

    @property
    def days(self) -> int:
        """Days whose daily costs are spent here (the last stop also gets the departure day)."""
        return self.nights + (1 if self.last else 0)


@dataclass
class LegCtx:
    index: int
    origin: City
    destination: City
    on: date
    round_trip: bool
    season: SeasonEstimate
    quotes: list[TransportQuote] = field(default_factory=list)
    chosen: TransportQuote | None = None


class PlanState(TypedDict, total=False):
    # inputs
    request: TripRequest
    llm: LlmProvider
    keep_days: list[LlmDay] | None  # reuse these days instead of calling the LLM (refine paths)
    keep_title: str | None
    keep_area: str | None
    max_loops: int
    deadline: float | None  # event-loop time by which the plan must be ready; None means no limit
    city_timeout: float | None
    # intake
    fx: FxQuote
    origin_city: City | None
    stops: list[StopCtx]
    dest_city: City  # first stop
    dest_located: bool
    season: SeasonEstimate  # season of the stop with the most nights
    pace: str
    interests: list[str]
    comfort: ComfortLevel
    modes: list[TransportMode]
    budget: Decimal | None
    intake_warnings: list[str]
    # pricing (recomputed on every budget-guard loop)
    legs: list[LegCtx]
    quotes: list[TransportQuote]  # first leg
    chosen: TransportQuote | None  # first leg
    transport_warnings: list[str]
    stay: Stay | None  # single-destination trips only
    stop_stays: dict[int, Stay]
    stay_items: list[CostItem]
    hints: dict[str, AcceptedHint]  # "leg:<i>" / "stay:<seq>"
    hint_lookups: int
    web_hints_skipped: bool
    items: list[CostItem]
    total: AmountRange
    status: BudgetStatus
    loops: int
    guard_warnings: list[str]
    guard_done: bool
    # output
    draft: DayPlanDraft
    itinerary_warnings: list[str]
    result: ItineraryResponse
