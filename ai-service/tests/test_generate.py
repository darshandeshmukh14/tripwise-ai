import asyncio
import time
from decimal import Decimal

from fastapi.testclient import TestClient

from app.config import get_settings
from app.llm.base import LlmError
from app.llm.factory import get_llm
from app.llm.fake import FakeLlmProvider
from app.main import app
from tests.conftest import HEADERS, trip_body

GENERATE = "/internal/v1/itineraries/generate"
TOP_LEVEL_KEYS = {
    "tripTitle", "destination", "origin", "comfortLevel", "totalEstimatedCostInr", "totalCost", "budgetStatus",
    "season", "transportOptions", "stay", "costBreakdown", "warnings", "days",
    "currency", "totalEstimatedCost", "fx", "stops", "legs", "bookingLinks", "webHints", "webHintsSkipped",
}


def generate(client, **overrides):
    r = client.post(GENERATE, json=trip_body(**overrides), headers=HEADERS)
    assert r.status_code == 200, r.text
    return r.json()


def assert_spring_contract(plan):
    """Mirrors com.tripwise.client.AiResponseValidator."""
    assert set(plan) == TOP_LEVEL_KEYS
    assert plan["days"]
    assert plan["costBreakdown"]
    for item in plan["costBreakdown"]:
        assert item["category"] and item["label"] and item["season"] and item["confidence"]
        a = item["amount"]
        assert 0 <= a["min"] <= a["expected"] <= a["max"]
    total = sum(Decimal(str(i["amount"]["expected"])) for i in plan["costBreakdown"])
    assert abs(total - Decimal(str(plan["totalEstimatedCost"]))) < Decimal("0.01")
    assert plan["totalCost"]["expected"] == plan["totalEstimatedCost"]
    if plan["currency"] == "INR":
        assert plan["totalEstimatedCostInr"] == plan["totalEstimatedCost"]


def test_generate_matches_contract(client):
    plan = generate(client)
    assert_spring_contract(plan)
    assert plan["destination"] == "Goa"
    assert plan["season"]["type"] == "PEAK"
    assert len(plan["days"]) == 5
    assert [d["date"] for d in plan["days"]][:2] == ["2026-12-20", "2026-12-21"]
    assert {"timeOfDay", "title", "description"} <= set(plan["days"][0]["activities"][0])
    assert sum(o["recommended"] for o in plan["transportOptions"]) == 1
    assert plan["stay"]["nights"] == 4 and plan["stay"]["rooms"] == 1
    assert [i["category"] for i in plan["costBreakdown"]][:2] == ["TRANSPORT", "STAY"]


def test_budget_guard_downgrades_comfort(client):
    plan = generate(client, budgetAmount=15000, comfortLevel="PREMIUM")
    assert_spring_contract(plan)
    assert plan["comfortLevel"] != "PREMIUM"
    assert any("Comfort lowered" in w for w in plan["warnings"])


def test_budget_guard_respects_keep(client):
    plan = generate(client, budgetAmount=5000, comfortLevel="PREMIUM", answers={"budgetConflict": "keep"})
    assert plan["comfortLevel"] == "PREMIUM"
    assert plan["budgetStatus"] == "OVER"
    assert any("exceeds your budget" in w for w in plan["warnings"])


def test_no_budget_is_unknown(client):
    assert generate(client, budgetAmount=None)["budgetStatus"] == "UNKNOWN"


def test_unknown_destination_still_plans_with_warnings(client):
    plan = generate(client, destination="Atlantis")
    assert_spring_contract(plan)
    assert plan["transportOptions"] == []
    assert any("Could not locate Atlantis" in w for w in plan["warnings"])


def test_no_origin_skips_transport(client):
    plan = generate(client, origin=None)
    assert_spring_contract(plan)
    assert all(i["category"] != "TRANSPORT" for i in plan["costBreakdown"])
    assert any("starting city" in w for w in plan["warnings"])


def test_day_trip_has_no_stay(client):
    plan = generate(client, origin="Mumbai", destination="Pune", startDate="2026-12-20", endDate="2026-12-20")
    assert plan["stay"] is None
    assert all(i["category"] != "STAY" for i in plan["costBreakdown"])


def test_transport_modes_are_respected(client):
    plan = generate(client, transportModes=["CAR"])
    assert [o["mode"] for o in plan["transportOptions"]] == ["CAR"]


def test_overnight_no_excludes_long_bus_and_train(client):
    plan = generate(client, origin="Delhi", answers={"overnightTravel": "no"})
    for o in plan["transportOptions"]:
        assert not (o["mode"] in ("BUS", "TRAIN") and o["durationHours"] > 10)


def test_llm_failure_falls_back_to_template_days(client):
    class Failing(FakeLlmProvider):
        async def plan_days(self, ctx):
            raise LlmError("boom")

    app.dependency_overrides[get_llm] = Failing
    plan = generate(client)
    assert_spring_contract(plan)
    assert len(plan["days"]) == 5
    assert any("simple outline" in w for w in plan["warnings"])
    assert "boom" not in str(plan)


def test_slow_day_plan_is_cut_off_by_deadline(client, monkeypatch):
    monkeypatch.setenv("PLAN_DEADLINE_SECONDS", "0.3")
    get_settings.cache_clear()

    class Slow(FakeLlmProvider):
        async def plan_days(self, ctx):
            await asyncio.sleep(5)
            return await super().plan_days(ctx)

    app.dependency_overrides[get_llm] = Slow
    started = time.monotonic()
    plan = generate(client)
    assert time.monotonic() - started < 3
    assert_spring_contract(plan)
    assert any("simple outline" in w for w in plan["warnings"])


def test_unknown_cities_are_looked_up_in_parallel(client):
    class Tracking(FakeLlmProvider):
        active = peak = 0

        async def estimate_city(self, name):
            Tracking.active += 1
            Tracking.peak = max(Tracking.peak, Tracking.active)
            await asyncio.sleep(0.1)
            Tracking.active -= 1
            return None

    app.dependency_overrides[get_llm] = Tracking
    generate(client, origin="Atlantis", destination="El Dorado")
    assert Tracking.peak == 2


def test_slow_city_lookup_times_out_as_unknown(client, monkeypatch):
    monkeypatch.setenv("CITY_LOOKUP_TIMEOUT_SECONDS", "0.1")
    get_settings.cache_clear()

    class SlowLookup(FakeLlmProvider):
        async def estimate_city(self, name):
            await asyncio.sleep(5)
            return None

    app.dependency_overrides[get_llm] = SlowLookup
    started = time.monotonic()
    plan = generate(client, destination="Atlantis")
    assert time.monotonic() - started < 3
    assert any("Could not locate Atlantis" in w for w in plan["warnings"])


def test_unexpected_error_returns_generic_500():
    class Broken(FakeLlmProvider):
        async def plan_days(self, ctx):
            raise RuntimeError("secret internals")

    app.dependency_overrides[get_llm] = Broken
    with TestClient(app, raise_server_exceptions=False) as c:
        r = c.post(GENERATE, json=trip_body(), headers=HEADERS)
    assert r.status_code == 500
    assert r.json() == {"detail": "Internal error"}
