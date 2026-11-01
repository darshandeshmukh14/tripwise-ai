import os

os.environ["INTERNAL_API_TOKEN"] = "test-internal-token-123"
os.environ["LLM_PROVIDER"] = "fake"
os.environ["FX_PROVIDER"] = "static"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.llm.factory import get_llm  # noqa: E402
from app.main import app  # noqa: E402
from app.prices.hints import hint_cache  # noqa: E402
from app.pricing import fx  # noqa: E402

TOKEN = "test-internal-token-123"
HEADERS = {"X-Internal-Token": TOKEN}


@pytest.fixture(autouse=True)
def _fresh_settings():
    get_settings.cache_clear()
    get_llm.cache_clear()
    hint_cache.clear()
    fx.reset_cache()
    yield
    app.dependency_overrides.clear()


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


def trip_body(**overrides):
    """Shape Spring's AiGenerateTripRequest serialises to (including explicit nulls)."""
    body = {
        "tripId": 1,
        "origin": "Mumbai",
        "destination": "Goa",
        "startDate": "2026-12-20",
        "endDate": "2026-12-24",
        "travellersCount": 2,
        "budgetAmount": 60000,
        "currency": "INR",
        "travelStyle": "balanced",
        "comfortLevel": "STANDARD",
        "transportModes": None,
        "preferences": ["beaches"],
        "answers": None,
    }
    body.update(overrides)
    return body
