"""Wire contract with Spring Boot (camelCase JSON). Mirrors com.tripwise.client.dto.* — change both sides together."""

import datetime as dt
from datetime import date
from decimal import Decimal
from enum import StrEnum
from typing import Annotated, Any

from pydantic import BaseModel, ConfigDict, Field, PlainSerializer, model_validator
from pydantic.alias_generators import to_camel

# Money is computed with Decimal and emitted as a JSON number with at most 2 decimals.
Money = Annotated[Decimal, PlainSerializer(lambda v: float(v), return_type=float, when_used="json")]
Number = Money  # non-money decimals (distance, hours, multipliers) also go out as JSON numbers
MAX_TRIP_DAYS = 60
MAX_STOPS = 6
SUPPORTED_CURRENCIES = (
    "INR", "USD", "EUR", "GBP", "CHF", "AED", "OMR", "NPR", "BTN", "LKR", "MVR", "THB", "VND", "SGD", "MYR",
    "IDR", "JPY", "KRW", "KZT", "AZN", "GEL", "TRY", "EGP", "KES", "TZS", "ZAR", "MUR", "SCR", "MAD",
)


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")


class ComfortLevel(StrEnum):
    BUDGET = "BUDGET"
    STANDARD = "STANDARD"
    PREMIUM = "PREMIUM"
    LUXURY = "LUXURY"


class TransportMode(StrEnum):
    TRAIN = "TRAIN"
    FLIGHT = "FLIGHT"
    BUS = "BUS"
    CAR = "CAR"


class CostCategory(StrEnum):
    TRANSPORT = "TRANSPORT"
    STAY = "STAY"
    FOOD = "FOOD"
    ACTIVITY = "ACTIVITY"
    LOCAL_TRANSPORT = "LOCAL_TRANSPORT"
    MISC = "MISC"


class Season(StrEnum):
    PEAK = "PEAK"
    SHOULDER = "SHOULDER"
    OFF = "OFF"


class Confidence(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class BudgetStatus(StrEnum):
    WITHIN = "WITHIN"
    NEAR = "NEAR"
    OVER = "OVER"
    UNKNOWN = "UNKNOWN"


class TimeOfDay(StrEnum):
    MORNING = "MORNING"
    AFTERNOON = "AFTERNOON"
    EVENING = "EVENING"


# ---------- requests ----------


class StopRequest(CamelModel):
    city: str = Field(min_length=1, max_length=255)
    country_code: str | None = Field(default=None, min_length=2, max_length=2)
    nights: int = Field(ge=1, le=MAX_TRIP_DAYS)


class TripRequest(CamelModel):
    """Body of /generate and /clarify, and the `trip` part of /refine."""

    trip_id: int | None = None
    origin: str | None = Field(default=None, max_length=255)
    destination: str = Field(min_length=1, max_length=255)
    start_date: date
    end_date: date
    travellers_count: int = Field(ge=1, le=50)
    budget_amount: Decimal | None = Field(default=None, ge=0, max_digits=12, decimal_places=2)
    currency: str = Field(default="INR", min_length=3, max_length=3)
    travel_style: str | None = Field(default=None, max_length=50)
    comfort_level: ComfortLevel = ComfortLevel.STANDARD
    transport_modes: list[TransportMode] = Field(default_factory=list, max_length=4)
    preferences: list[Annotated[str, Field(max_length=100)]] = Field(default_factory=list, max_length=50)
    answers: dict[str, Annotated[str, Field(max_length=500)]] = Field(default_factory=dict, max_length=20)
    # Multi-city: used only with 2+ stops (in visiting order; nights must add up to the trip's nights)
    stops: list[StopRequest] = Field(default_factory=list, max_length=MAX_STOPS)
    # Spring decides per request whether web price hints may be fetched (per-user daily cap)
    allow_web_hints: bool = False

    @model_validator(mode="before")
    @classmethod
    def _nulls_to_defaults(cls, data: Any) -> Any:
        # Spring sends explicit nulls for empty optional fields
        if isinstance(data, dict):
            data = dict(data)
            for key in ("comfortLevel", "currency", "allowWebHints"):
                if key in data and data[key] is None:
                    data.pop(key)
            for key in ("transportModes", "preferences", "stops"):
                if key in data and data[key] is None:
                    data[key] = []
            if "answers" in data and data["answers"] is None:
                data["answers"] = {}
        return data

    @model_validator(mode="after")
    def _check_dates(self) -> "TripRequest":
        if self.end_date < self.start_date:
            raise ValueError("endDate must be on or after startDate")
        if (self.end_date - self.start_date).days + 1 > MAX_TRIP_DAYS:
            raise ValueError(f"trip can be at most {MAX_TRIP_DAYS} days long")
        self.currency = self.currency.upper()
        if self.currency not in SUPPORTED_CURRENCIES:
            raise ValueError("unsupported currency")
        if len(self.stops) >= 2 and sum(s.nights for s in self.stops) != self.nights:
            raise ValueError("stop nights must add up to the trip's nights")
        return self

    @property
    def multi_city(self) -> bool:
        return len(self.stops) >= 2

    @property
    def days(self) -> int:
        return (self.end_date - self.start_date).days + 1

    @property
    def nights(self) -> int:
        return self.days - 1


class ChatTurn(CamelModel):
    role: str = Field(pattern="^(USER|ASSISTANT)$")
    content: str = Field(max_length=4000)


class RefineRequest(CamelModel):
    trip_id: int | None = None
    trip: TripRequest
    current_plan: dict[str, Any] | None = None
    history: list[ChatTurn] = Field(default_factory=list, max_length=40)
    message: str = Field(min_length=1, max_length=2000)


# ---------- responses ----------


class AmountRange(CamelModel):
    min: Money
    expected: Money
    max: Money


class SeasonInfo(CamelModel):
    type: Season
    multiplier: Number
    notes: str | None = None


class TransportOption(CamelModel):
    mode: TransportMode
    travel_class: str
    distance_km: Number
    duration_hours: Number
    cost: AmountRange
    comfort_score: int = Field(ge=1, le=5)
    recommended: bool = False
    notes: str | None = None


class Stay(CamelModel):
    tier: ComfortLevel
    nightly_rate: AmountRange
    nights: int
    rooms: int
    area_suggestion: str | None = None


class CostItem(CamelModel):
    category: CostCategory
    mode: TransportMode | None = None
    label: str
    amount: AmountRange
    season: Season
    source: str = "ESTIMATE"  # ESTIMATE | FARE_RULE | WEB_HINT
    confidence: Confidence
    assumptions: str | None = None
    stop_seq: int | None = Field(default=None, ge=1, le=MAX_STOPS)
    leg_index: int | None = Field(default=None, ge=0, le=MAX_STOPS)
    source_url: str | None = None


class FxInfo(CamelModel):
    base: str
    quote: str
    rate: Number  # units of `quote` per 1 `base`
    as_of: dt.datetime
    source: str


class StopPlan(CamelModel):
    seq: int = Field(ge=1, le=MAX_STOPS)
    city: str
    country_code: str
    nights: int
    arrival_date: date
    departure_date: date
    season: Season
    stay: Stay | None = None


class LegPlan(CamelModel):
    index: int = Field(ge=0, le=MAX_STOPS)
    from_city: str
    to_city: str
    international: bool = False
    options: list[TransportOption] = Field(default_factory=list)


class BookingLink(CamelModel):
    provider: str
    label: str
    url: str
    kind: str  # TRANSPORT | STAY
    leg_index: int | None = None
    stop_seq: int | None = None
    sponsored: bool = False


class WebHint(CamelModel):
    label: str
    source_url: str
    source_title: str | None = None
    # Google Search Suggestions chip HTML; must be displayed as-is (grounding terms of service)
    search_suggestions_html: str | None = None


class Activity(CamelModel):
    time_of_day: TimeOfDay
    title: str = Field(max_length=200)
    description: str = Field(default="", max_length=1000)


class DayPlan(CamelModel):
    day: int = Field(ge=1)
    date: dt.date | None = None
    title: str = Field(max_length=200)
    summary: str = Field(default="", max_length=1000)
    activities: list[Activity] = Field(default_factory=list, max_length=10)


class ItineraryResponse(CamelModel):
    trip_title: str
    destination: str
    origin: str | None
    comfort_level: ComfortLevel
    total_estimated_cost_inr: Money
    total_cost: AmountRange
    budget_status: BudgetStatus
    season: SeasonInfo
    transport_options: list[TransportOption]
    stay: Stay | None
    cost_breakdown: list[CostItem]
    warnings: list[str]
    days: list[DayPlan]
    # v2 (defaults keep plans stored before v2 parseable)
    currency: str = "INR"
    total_estimated_cost: Money | None = None  # in `currency`; equals totalCost.expected
    fx: FxInfo | None = None
    stops: list[StopPlan] = Field(default_factory=list)
    legs: list[LegPlan] = Field(default_factory=list)
    booking_links: list[BookingLink] = Field(default_factory=list)
    web_hints: list[WebHint] = Field(default_factory=list)
    web_hints_skipped: bool = False


class ClarifyQuestion(CamelModel):
    id: str
    text: str
    type: str  # number | single_choice | multi_choice | text
    options: list[str] = Field(default_factory=list)


class ClarifyResponse(CamelModel):
    questions: list[ClarifyQuestion]


class RefineResponse(CamelModel):
    assistant_message: str = Field(max_length=4000)
    updated_plan: ItineraryResponse | None = None


# ---------- multi-city stops ----------


class RouteRequest(CamelModel):
    origin: str | None = Field(default=None, max_length=255)
    stops: list[StopRequest] = Field(min_length=2, max_length=MAX_STOPS)
    optimize_order: bool = False


class RouteStop(CamelModel):
    seq: int
    city: str
    country_code: str
    nights: int
    known: bool


class RouteLeg(CamelModel):
    from_city: str
    to_city: str
    road_km: Number | None  # None when a location could not be resolved
    within_limit: bool


class RouteResponse(CamelModel):
    stops: list[RouteStop]
    legs: list[RouteLeg]
    valid: bool
    violations: list[str]


class SuggestRequest(CamelModel):
    trip: TripRequest
    limit: int = Field(default=5, ge=1, le=10)


class StopSuggestion(CamelModel):
    city: str
    country_code: str
    near_stop: str
    distance_km: Number
    travel_hours: Number
    suggested_nights: int
    extra_cost: AmountRange
    currency: str
    season: Season
    reason: str


class SuggestResponse(CamelModel):
    suggestions: list[StopSuggestion]
