from datetime import date
from types import SimpleNamespace

import pytest

from app.config import Settings
from app.llm.base import DayPlanContext, LlmError
from app.llm.gemini import GeminiLlmProvider
from app.llm.models import CityGuess, DayPlanDraft, LlmActivity, LlmDay
from app.schemas import TimeOfDay

CTX = DayPlanContext(destination="Goa", origin="Mumbai", start_date=date(2026, 12, 20), days=2, travellers=2,
                     comfort_level="STANDARD", pace="balanced", interests=["beaches"], season="PEAK",
                     season_notes="", arrival_mode="TRAIN", budget_note="not specified")


def provider(result=None, error=None) -> GeminiLlmProvider:
    settings = Settings(internal_api_token="test-internal-token-123", llm_provider="gemini",
                        gemini_api_key="dummy-gemini-key", llm_timeout_seconds=1)
    p = GeminiLlmProvider(settings)

    async def generate_content(**_):
        if error:
            raise error
        return SimpleNamespace(parsed=result, text=None)

    p._client = SimpleNamespace(aio=SimpleNamespace(models=SimpleNamespace(generate_content=generate_content)))
    return p


def draft(days: int) -> DayPlanDraft:
    return DayPlanDraft(trip_title="Goa", area_suggestion="Calangute", days=[
        LlmDay(day=i + 1, title="d", summary="", activities=[
            LlmActivity(time_of_day=TimeOfDay.MORNING, title="a", description="")])
        for i in range(days)])


async def test_plan_days_returns_parsed_output():
    assert (await provider(draft(2)).plan_days(CTX)).trip_title == "Goa"


async def test_wrong_day_count_is_rejected():
    with pytest.raises(LlmError):
        await provider(draft(3)).plan_days(CTX)


async def test_sdk_errors_become_llm_error():
    with pytest.raises(LlmError):
        await provider(error=RuntimeError("quota")).plan_days(CTX)


async def test_estimate_city_swallows_errors_and_not_found():
    assert await provider(error=RuntimeError("x")).estimate_city("Nowhere") is None
    assert await provider(CityGuess(found=False)).estimate_city("Nowhere") is None


def test_gemini_requires_api_key():
    with pytest.raises(ValueError):
        Settings(internal_api_token="test-internal-token-123", llm_provider="gemini", gemini_api_key=None)
