from tests.conftest import HEADERS, trip_body

CLARIFY = "/internal/v1/preferences/clarify"


def ids(client, **overrides):
    r = client.post(CLARIFY, json=trip_body(**overrides), headers=HEADERS)
    assert r.status_code == 200, r.text
    questions = r.json()["questions"]
    for q in questions:
        assert q["id"] and q["text"] and q["type"]
    return [q["id"] for q in questions]


def test_complete_trip_asks_nothing(client):
    assert ids(client, origin="Mumbai", destination="Pune", answers={"overnightTravel": "yes"}) == []


def test_missing_budget_interests_and_pace(client):
    asked = ids(client, budgetAmount=None, preferences=None, travelStyle=None)
    assert {"budget", "interests", "pace"} <= set(asked)


def test_budget_conflict_when_below_minimum(client):
    assert "budgetConflict" in ids(client, budgetAmount=1000)


def test_overnight_question_for_long_journeys(client):
    assert "overnightTravel" in ids(client, origin="Delhi")


def test_answered_questions_are_skipped(client):
    asked = ids(client, budgetAmount=1000, preferences=None,
                answers={"budgetConflict": "keep", "interests": "Food", "overnightTravel": "no"})
    assert not {"budgetConflict", "interests", "overnightTravel"} & set(asked)
