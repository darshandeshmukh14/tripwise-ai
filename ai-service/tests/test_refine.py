import asyncio
import time

from app.config import get_settings
from app.llm.base import LlmError
from app.llm.factory import get_llm
from app.llm.fake import FakeLlmProvider
from app.llm.models import LlmActivity, LlmDay, RefineAction, RefineDecision
from app.main import app
from app.schemas import TimeOfDay
from tests.conftest import HEADERS, trip_body
from tests.test_generate import assert_spring_contract, generate

REFINE = "/internal/v1/itineraries/refine"


def refine(client, plan, message, trip=None):
    body = {"tripId": 1, "trip": trip or trip_body(), "currentPlan": plan,
            "history": [{"role": "USER", "content": "hi"}, {"role": "ASSISTANT", "content": "hello"}],
            "message": message}
    r = client.post(REFINE, json=body, headers=HEADERS)
    assert r.status_code == 200, r.text
    return r.json()


def test_question_leaves_plan_unchanged(client):
    reply = refine(client, generate(client), "What is the weather like?")
    assert reply["assistantMessage"]
    assert reply["updatedPlan"] is None


def test_cheaper_by_bus_replans_and_keeps_days(client):
    plan = generate(client)
    reply = refine(client, plan, "Make it cheaper and go by bus")
    updated = reply["updatedPlan"]
    assert_spring_contract(updated)
    assert updated["comfortLevel"] == "BUDGET"
    assert [o["mode"] for o in updated["transportOptions"]] == ["BUS"]
    assert updated["totalEstimatedCostInr"] < plan["totalEstimatedCostInr"]
    assert updated["days"] == plan["days"]


def test_explicit_upgrade_is_not_undone_by_budget_guard(client):
    plan = generate(client, budgetAmount=20000)
    updated = refine(client, plan, "Upgrade to luxury please", trip=trip_body(budgetAmount=20000))["updatedPlan"]
    assert updated["comfortLevel"] == "LUXURY"
    assert updated["budgetStatus"] == "OVER"


def test_invalid_stored_plan_falls_back_to_full_replan(client):
    updated = refine(client, {"legacy": True}, "go by train")["updatedPlan"]
    assert_spring_contract(updated)
    assert [o["mode"] for o in updated["transportOptions"]] == ["TRAIN"]


def test_edit_days_keeps_prices(client):
    plan = generate(client)

    class Editing(FakeLlmProvider):
        async def refine(self, ctx):
            days = [LlmDay(day=i + 1, title=f"New day {i + 1}", summary="",
                           activities=[LlmActivity(time_of_day=TimeOfDay.MORNING, title="Swim", description="")])
                    for i in range(ctx.trip_days)]
            return RefineDecision(assistant_message="Swapped activities", action=RefineAction.EDIT_DAYS, days=days)

    app.dependency_overrides[get_llm] = Editing
    updated = refine(client, plan, "More swimming")["updatedPlan"]
    assert updated["days"][0]["title"] == "New day 1"
    assert updated["costBreakdown"] == plan["costBreakdown"]
    assert updated["days"][0]["date"] == "2026-12-20"


def test_refine_llm_failure_returns_502(client):
    plan = generate(client)

    class Failing(FakeLlmProvider):
        async def refine(self, ctx):
            raise LlmError("secret detail")

    app.dependency_overrides[get_llm] = Failing
    body = {"tripId": 1, "trip": trip_body(), "currentPlan": plan, "history": [], "message": "hi"}
    r = client.post(REFINE, json=body, headers=HEADERS)
    assert r.status_code == 502
    assert "secret detail" not in r.text


def test_slow_refine_is_cut_off_by_deadline(client, monkeypatch):
    plan = generate(client)
    monkeypatch.setenv("PLAN_DEADLINE_SECONDS", "0.2")
    get_settings.cache_clear()

    class Slow(FakeLlmProvider):
        async def refine(self, ctx):
            await asyncio.sleep(5)

    app.dependency_overrides[get_llm] = Slow
    body = {"tripId": 1, "trip": trip_body(), "currentPlan": plan, "history": [], "message": "hi"}
    started = time.monotonic()
    r = client.post(REFINE, json=body, headers=HEADERS)
    assert r.status_code == 502
    assert time.monotonic() - started < 3
