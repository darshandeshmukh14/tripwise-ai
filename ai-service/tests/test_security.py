import pytest

from tests.conftest import HEADERS, trip_body

PATHS = ["/internal/v1/itineraries/generate", "/internal/v1/preferences/clarify", "/internal/v1/itineraries/refine"]


@pytest.mark.parametrize("path", PATHS)
def test_missing_token_is_rejected(client, path):
    assert client.post(path, json=trip_body()).status_code == 401


@pytest.mark.parametrize("path", PATHS)
def test_wrong_token_is_rejected(client, path):
    assert client.post(path, json=trip_body(), headers={"X-Internal-Token": "wrong-token-value-xx"}).status_code == 401


def test_health_needs_no_token(client):
    assert client.get("/health").json() == {"status": "UP"}


def test_docs_are_disabled(client):
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404


def test_invalid_body_returns_422(client):
    r = client.post("/internal/v1/itineraries/generate", json=trip_body(endDate="2026-12-01"), headers=HEADERS)
    assert r.status_code == 422
