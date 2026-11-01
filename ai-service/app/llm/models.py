"""Internal LLM I/O models (not part of the Spring contract). Kept flat and simple for Gemini structured output."""

from enum import StrEnum

from pydantic import BaseModel

from app.schemas import ComfortLevel, TimeOfDay, TransportMode


class LlmActivity(BaseModel):
    time_of_day: TimeOfDay
    title: str
    description: str


class LlmDay(BaseModel):
    day: int
    title: str
    summary: str
    activities: list[LlmActivity]


class DayPlanDraft(BaseModel):
    trip_title: str
    area_suggestion: str
    days: list[LlmDay]


class CityGuess(BaseModel):
    # Validated by the caller (ranges, profile name); kept constraint-free for structured-output schemas.
    found: bool
    canonical_name: str = ""
    lat: float = 0
    lon: float = 0
    tier: int = 2
    airport: bool = False
    rail: bool = False
    island: bool = False
    season_profile: str = "metro"
    country_code: str = "IN"


class RankedStop(BaseModel):
    city: str
    reason: str


class StopRanking(BaseModel):
    stops: list[RankedStop]


class RefineAction(StrEnum):
    NONE = "NONE"          # answer only, plan unchanged
    REPLAN = "REPLAN"      # change trip inputs and rebuild the plan (prices recomputed)
    EDIT_DAYS = "EDIT_DAYS"  # rewrite the day-by-day activities only (prices unchanged)


class RefineChanges(BaseModel):
    comfort_level: ComfortLevel | None = None
    transport_modes: list[TransportMode] | None = None
    budget_amount: float | None = None
    pace: str | None = None
    interests: list[str] | None = None


class RefineDecision(BaseModel):
    assistant_message: str
    action: RefineAction
    changes: RefineChanges | None = None
    days: list[LlmDay] | None = None
