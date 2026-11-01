import json
from datetime import date

import httpx
import pytest

from app.config import Settings
from app.llm.base import DayPlanContext, LlmError, PriceQuery
from app.llm.openrouter import OpenRouterLlmProvider

CTX = DayPlanContext(destination="Goa", origin="Mumbai", start_date=date(2026, 12, 20), days=2, travellers=2,
                     comfort_level="STANDARD", pace="balanced", interests=["beaches"], season="PEAK",
                     season_notes="", arrival_mode="TRAIN", budget_note="not specified")

DRAFT = {"trip_title": "Goa", "area_suggestion": "Calangute", "days": [
    {"day": d, "title": "t", "summary": "", "activities": [
        {"time_of_day": "MORNING", "title": "a", "description": ""}]} for d in (1, 2)]}


def settings(**overrides) -> Settings:
    values = {"internal_api_token": "test-internal-token-123", "llm_provider": "openrouter",
              "openrouter_api_key": "dummy-openrouter-key", "openrouter_model": "vendor/model",
              "llm_timeout_seconds": 1} | overrides
    return Settings(**values)


def provider(handler) -> tuple[OpenRouterLlmProvider, list[httpx.Request]]:
    seen: list[httpx.Request] = []

    def record(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return handler(request)

    s = settings()
    client = httpx.AsyncClient(base_url=s.openrouter_base_url, transport=httpx.MockTransport(record),
                               headers={"Authorization": "Bearer dummy-openrouter-key"})
    return OpenRouterLlmProvider(s, client=client), seen


def reply(content: str, status: int = 200) -> httpx.Response:
    return httpx.Response(status, json={"choices": [{"message": {"role": "assistant", "content": content}}]})


async def test_plan_days_parses_structured_output_and_sends_schema():
    p, seen = provider(lambda _: reply(json.dumps(DRAFT)))
    draft = await p.plan_days(CTX)
    assert draft.trip_title == "Goa"
    body = json.loads(seen[0].content)
    assert seen[0].url.path.endswith("/chat/completions")
    assert body["model"] == "vendor/model"
    assert body["response_format"]["json_schema"]["name"] == "DayPlanDraft"
    assert seen[0].headers["Authorization"] == "Bearer dummy-openrouter-key"


async def test_code_fenced_json_is_accepted():
    p, _ = provider(lambda _: reply("```json\n" + json.dumps(DRAFT) + "\n```"))
    assert len((await p.plan_days(CTX)).days) == 2


@pytest.mark.parametrize("response", [
    reply("not json"),
    reply(json.dumps(DRAFT), status=429),
    httpx.Response(200, json={"unexpected": True}),
    reply(json.dumps(DRAFT | {"days": DRAFT["days"][:1]})),
])
async def test_bad_responses_become_llm_error(response):
    p, _ = provider(lambda _: response)
    with pytest.raises(LlmError):
        await p.plan_days(CTX)


async def test_transport_errors_become_llm_error():
    def boom(request):
        raise httpx.ConnectError("down", request=request)

    p, _ = provider(boom)
    with pytest.raises(LlmError):
        await p.plan_days(CTX)


async def test_estimate_city_swallows_errors_and_not_found():
    p, _ = provider(lambda _: reply(json.dumps({"found": False})))
    assert await p.estimate_city("Nowhere") is None
    p, _ = provider(lambda _: httpx.Response(500))
    assert await p.estimate_city("Nowhere") is None


async def test_price_hint_is_not_supported():
    p, seen = provider(lambda _: reply("{}"))
    assert await p.price_hint(PriceQuery(kind="STAY", month="2026-12", travel_class="STANDARD", city="Goa")) is None
    assert not seen


def test_openrouter_requires_api_key_and_https():
    with pytest.raises(ValueError):
        settings(openrouter_api_key=None)
    with pytest.raises(ValueError):
        settings(openrouter_base_url="http://openrouter.ai/api/v1")


def test_web_hints_are_skipped_for_openrouter():
    assert not settings(grounding_enabled=True).web_hints_available
    assert Settings(internal_api_token="test-internal-token-123", llm_provider="fake",
                    grounding_enabled=True).web_hints_available
