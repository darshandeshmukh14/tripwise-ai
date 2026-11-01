from datetime import date
from decimal import Decimal

from app.pricing import engine
from app.pricing.catalog import City, find_city, season_for
from app.schemas import BudgetStatus, ComfortLevel, Confidence, Season, TransportMode


def _season(start=date(2026, 12, 20), end=date(2026, 12, 24), profile="beach"):
    return season_for(profile, start, end)


def test_find_city_is_case_insensitive_and_strips_region():
    assert find_city("goa").name == "Goa"
    assert find_city("Mumbai, Maharashtra").name == "Mumbai"
    assert find_city("Atlantis") is None
    assert find_city("  ") is None


def test_season_peak_and_off():
    assert _season().type is Season.PEAK
    off = _season(date(2026, 7, 1), date(2026, 7, 5))
    assert off.type is Season.OFF
    assert off.multiplier < _season().multiplier


def test_haversine_mumbai_delhi_is_about_1150_km():
    km = engine.haversine_km(find_city("Mumbai"), find_city("Delhi"))
    assert 1100 < km < 1200


def test_flight_needs_minimum_distance():
    quotes = engine.transport_quotes(find_city("Mumbai"), find_city("Pune"), [TransportMode.FLIGHT],
                                     ComfortLevel.STANDARD, 2, _season())
    assert quotes == []


def test_island_allows_flight_only():
    island = City("Port Blair", 11.62, 92.73, 3, True, False, "beach", island=True)
    quotes = engine.transport_quotes(find_city("Chennai"), island, [], ComfortLevel.STANDARD, 2, _season())
    assert [q.option.mode for q in quotes] == [TransportMode.FLIGHT]


def test_unknown_city_lowers_confidence():
    guessed = City("Somewhere", 15.0, 74.0, 3, False, True, "beach", known=False)
    quotes = engine.transport_quotes(find_city("Mumbai"), guessed, [TransportMode.TRAIN], ComfortLevel.STANDARD, 2,
                                     _season())
    assert quotes[0].confidence is Confidence.LOW


def test_ranges_are_ordered_and_rounded():
    r = engine.amount_range(Decimal("1234.567"), Confidence.LOW)
    assert r.min <= r.expected <= r.max
    assert r.expected == Decimal("1234.57")


def test_peak_costs_more_than_off_season():
    goa = find_city("Goa")
    peak = engine.nightly_rate(goa.tier, ComfortLevel.STANDARD, _season())
    off = engine.nightly_rate(goa.tier, ComfortLevel.STANDARD, _season(date(2026, 7, 1), date(2026, 7, 5)))
    assert peak > off


def test_recommend_marks_exactly_one():
    quotes = engine.transport_quotes(find_city("Mumbai"), find_city("Goa"), [], ComfortLevel.STANDARD, 2, _season())
    best = engine.recommend(quotes, ComfortLevel.STANDARD, 2)
    assert best is not None
    assert sum(q.option.recommended for q in quotes) == 1


def test_budget_status_thresholds_match_spring():
    assert engine.budget_status(Decimal("100"), None) is BudgetStatus.UNKNOWN
    assert engine.budget_status(Decimal("101"), Decimal("100")) is BudgetStatus.OVER
    assert engine.budget_status(Decimal("95"), Decimal("100")) is BudgetStatus.NEAR
    assert engine.budget_status(Decimal("90"), Decimal("100")) is BudgetStatus.WITHIN


def test_downgrade_order():
    assert engine.downgrade(ComfortLevel.LUXURY) is ComfortLevel.PREMIUM
    assert engine.downgrade(ComfortLevel.BUDGET) is None
