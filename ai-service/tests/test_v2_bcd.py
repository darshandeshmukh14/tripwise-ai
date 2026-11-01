"""v2 phases B-D: currency, multi-city stops, fare rules, web hints, booking links and daily caps."""

import asyncio
from datetime import date
from decimal import Decimal

import pytest

from app.config import get_settings
from app.llm.base import CapReached, PriceHint, PriceQuery, StopRankContext
from app.llm.caps import CappedLlmProvider
from app.llm.factory import get_llm
from app.llm.fake import FakeLlmProvider
from app.main import app
from app.prices.fare_rules import irctc_fare
from app.prices.hints import accept, hint_cache
from app.prices.links import LegLink, StayLink, build_links
from app.pricing.catalog import find_city, season_for
from app.pricing.engine import transport_quotes
from app.pricing.fx import fx_quote, static_quote
from app.route import best_order, check_route
from app.schemas import ComfortLevel, TransportMode
from tests.conftest import HEADERS, trip_body
from tests.test_generate import assert_spring_contract, generate

ROUTE = "/internal/v1/stops/route"
SUGGEST = "/internal/v1/stops/suggest"


def city(name):
    found = find_city(name)
    assert found is not None, name
    return found


def multi_city_body(**overrides):
    body = trip_body(destination="Jaipur", stops=[{"city": "Jaipur", "nights": 2}, {"city": "Udaipur", "nights": 2}])
    body.update(overrides)
    return body


# ---------- B: currency + international ----------


def test_static_fx_and_pegs():
    usd = static_quote("USD")
    aed = static_quote("AED")
    assert usd.rate > 0
    assert abs(aed.rate / usd.rate - Decimal("3.6725")) < Decimal("0.0001")
    assert static_quote("NPR").rate == Decimal("1.6")
    assert static_quote("INR").rate == 1
    with pytest.raises(ValueError):
        static_quote("XXX")
    assert asyncio.run(fx_quote("usd")).source == "static"  # FX_PROVIDER=static in tests


def test_international_and_island_legs_are_flight_only():
    season = season_for("metro", date(2026, 12, 20), date(2026, 12, 24))
    for a, b in (("Mumbai", "Dubai"), ("Delhi", "Bangkok"), ("Chennai", "Port Blair"), ("Mumbai", "Male")):
        quotes = transport_quotes(city(a), city(b), [], ComfortLevel.STANDARD, 2, season)
        assert quotes, (a, b)
        assert {q.option.mode for q in quotes} == {TransportMode.FLIGHT}, (a, b)
    assert transport_quotes(city("Mumbai"), city("Dubai"), [TransportMode.TRAIN], ComfortLevel.STANDARD, 2,
                            season) == []


def test_road_neighbours_allow_bus_across_border():
    season = season_for("himalaya", date(2026, 10, 1), date(2026, 10, 5))
    modes = {q.option.mode for q in transport_quotes(city("Kathmandu"), city("Pokhara"), [], ComfortLevel.STANDARD,
                                                     2, season)}
    assert TransportMode.BUS in modes


def test_generate_in_foreign_currency(client):
    inr = generate(client)
    usd = generate(client, currency="usd")
    assert_spring_contract(usd)
    assert usd["currency"] == "USD" and usd["fx"]["quote"] == "USD" and usd["fx"]["base"] == "INR"
    rate = Decimal(str(usd["fx"]["rate"]))
    total = Decimal(str(usd["totalEstimatedCost"]))
    assert abs(total / rate - Decimal(str(usd["totalEstimatedCostInr"]))) < 1
    assert abs(Decimal(str(usd["totalEstimatedCostInr"])) - Decimal(str(inr["totalEstimatedCostInr"]))) < 50


def test_unsupported_currency_rejected(client):
    r = client.post("/internal/v1/itineraries/generate", json=trip_body(currency="XYZ"), headers=HEADERS)
    assert r.status_code == 422


def test_international_trip_adds_visa_note_and_flight(client):
    plan = generate(client, destination="Dubai", budgetAmount=None)
    assert_spring_contract(plan)
    assert any("Visa (" in w for w in plan["warnings"])
    assert {o["mode"] for o in plan["transportOptions"]} == {"FLIGHT"}


# ---------- D: IRCTC fare rules ----------


def test_irctc_fares_are_ordered_by_class():
    km = Decimal(500)
    sl, ac3, ac2, ac1 = (irctc_fare(km, c) for c in ("SL", "3A", "2A", "1A"))
    assert 0 < sl < ac3 < ac2 < ac1
    assert irctc_fare(Decimal(1000), "3A") > ac3
    assert irctc_fare(km, "XYZ") is None


def test_indian_train_uses_fare_rule():
    season = season_for("metro", date(2026, 12, 20), date(2026, 12, 24))
    quotes = transport_quotes(city("Mumbai"), city("Pune"), [TransportMode.TRAIN], ComfortLevel.STANDARD, 2, season)
    assert quotes and quotes[0].source == "FARE_RULE"


# ---------- C: multi-city ----------


def test_route_rule_and_order():
    assert all(leg.ok for leg in check_route([city("Jaipur"), city("Udaipur")]))
    far = check_route([city("Delhi"), city("Mumbai")])[0]
    assert not far.ok and "400" in far.reason
    stops = [city("Delhi"), city("Jaipur"), city("Agra")]
    assert [stops[i].name for i in best_order(stops)] == ["Delhi", "Agra", "Jaipur"]


def test_route_endpoint(client):
    ok = client.post(ROUTE, json={"stops": [{"city": "jaipur", "nights": 2}, {"city": "Udaipur", "nights": 1}]},
                     headers=HEADERS)
    assert ok.status_code == 200
    body = ok.json()
    assert body["valid"] and [s["city"] for s in body["stops"]] == ["Jaipur", "Udaipur"]
    assert body["stops"][0]["countryCode"] == "IN" and body["legs"][0]["withinLimit"]

    bad = client.post(ROUTE, json={"stops": [{"city": "Jaipur", "nights": 2}, {"city": "Goa", "nights": 2}]},
                      headers=HEADERS).json()
    assert not bad["valid"] and bad["violations"]

    unknown = client.post(ROUTE, json={"stops": [{"city": "Jaipur", "nights": 2}, {"city": "Nowhereville",
                                                                                      "nights": 2}]},
                          headers=HEADERS).json()
    assert not unknown["valid"] and unknown["legs"][0]["roadKm"] is None

    ordered = client.post(ROUTE, json={"optimizeOrder": True, "stops": [
        {"city": "Delhi", "nights": 1}, {"city": "Jaipur", "nights": 1}, {"city": "Agra", "nights": 1}]},
        headers=HEADERS).json()
    assert [s["city"] for s in ordered["stops"]] == ["Delhi", "Agra", "Jaipur"]

    seven = [{"city": "Jaipur", "nights": 1}] * 7
    assert client.post(ROUTE, json={"stops": seven}, headers=HEADERS).status_code == 422


def test_route_requires_token(client):
    r = client.post(ROUTE, json={"stops": [{"city": "Jaipur", "nights": 1}, {"city": "Udaipur", "nights": 1}]})
    assert r.status_code == 401


def test_multi_city_generate(client):
    r = client.post("/internal/v1/itineraries/generate", json=multi_city_body(), headers=HEADERS)
    assert r.status_code == 200, r.text
    plan = r.json()
    assert_spring_contract(plan)
    assert [s["city"] for s in plan["stops"]] == ["Jaipur", "Udaipur"]
    assert plan["stops"][0]["arrivalDate"] == "2026-12-20" and plan["stops"][1]["departureDate"] == "2026-12-24"
    assert [(leg["fromCity"], leg["toCity"]) for leg in plan["legs"]] == [
        ("Mumbai", "Jaipur"), ("Jaipur", "Udaipur"), ("Udaipur", "Mumbai")]
    stays = [i for i in plan["costBreakdown"] if i["category"] == "STAY"]
    assert sorted(i["stopSeq"] for i in stays) == [1, 2]
    transport = [i for i in plan["costBreakdown"] if i["category"] == "TRANSPORT"]
    assert sorted(i["legIndex"] for i in transport) == [0, 1, 2]
    assert any(d["title"].startswith("Travel to Udaipur") for d in plan["days"])
    assert {link["kind"] for link in plan["bookingLinks"]} == {"TRANSPORT", "STAY"}


def test_stop_nights_must_match_trip(client):
    body = multi_city_body(stops=[{"city": "Jaipur", "nights": 1}, {"city": "Udaipur", "nights": 1}])
    r = client.post("/internal/v1/itineraries/generate", json=body, headers=HEADERS)
    assert r.status_code == 422


def test_suggest_nearby_places(client):
    r = client.post(SUGGEST, json={"trip": trip_body(destination="Jaipur"), "limit": 5}, headers=HEADERS)
    assert r.status_code == 200, r.text
    items = r.json()["suggestions"]
    assert 0 < len(items) <= 5
    for s in items:
        assert s["city"] != "Jaipur" and s["distanceKm"] <= 400 and s["currency"] == "INR"
        assert s["suggestedNights"] in (1, 2) and s["extraCost"]["expected"] > 0
    assert "Agra" in {s["city"] for s in items}  # golden-triangle cluster mate


def test_suggest_returns_nothing_at_six_stops(client):
    stops = [{"city": c, "nights": 1} for c in ("Delhi", "Agra", "Jaipur", "Pushkar", "Udaipur", "Jodhpur")]
    body = trip_body(destination="Delhi", startDate="2026-12-20", endDate="2026-12-26", stops=stops)
    r = client.post(SUGGEST, json={"trip": body}, headers=HEADERS)
    assert r.status_code == 200 and r.json()["suggestions"] == []


# ---------- D: web hints, links, caps ----------


def test_hint_accept_band():
    query = PriceQuery(kind="STAY", month="2026-12", travel_class="STANDARD", city="Goa")
    hint = PriceHint(price_inr=5000, source_url="https://example.com", source_title="x", search_suggestions_html=None)
    assert accept(hint, Decimal(4000), Decimal(1), query) is not None
    assert accept(hint, Decimal(2000), Decimal(1), query) is None  # 2.5x baseline
    assert accept(hint, Decimal(20000), Decimal(1), query) is None  # 0.25x baseline
    assert accept(None, Decimal(4000), Decimal(1), query) is None


def _grounding(monkeypatch, provider):
    monkeypatch.setenv("GROUNDING_ENABLED", "true")
    get_settings.cache_clear()
    app.dependency_overrides[get_llm] = lambda: provider


def test_web_hint_replaces_stay_estimate(client, monkeypatch):
    fake = FakeLlmProvider()
    _grounding(monkeypatch, fake)
    baseline = generate(client, allowWebHints=True)
    assert fake.hint_calls > 0
    nightly = Decimal(str(baseline["stay"]["nightlyRate"]["expected"]))

    hint_cache.clear()
    fake.hint_prices = {PriceQuery(kind="STAY", month="2026-12", travel_class="STANDARD", city="Goa").cache_key():
                        float(nightly * Decimal("1.2"))}
    plan = generate(client, allowWebHints=True)
    assert_spring_contract(plan)
    stay = next(i for i in plan["costBreakdown"] if i["category"] == "STAY")
    assert stay["source"] == "WEB_HINT" and stay["sourceUrl"] == "https://example.com/fares"
    assert plan["webHints"] and plan["webHints"][0]["sourceUrl"] == "https://example.com/fares"

    calls = fake.hint_calls
    generate(client, allowWebHints=True)
    assert fake.hint_calls == calls  # cached (hits and misses)


def test_web_hints_skipped_when_not_allowed(client, monkeypatch):
    fake = FakeLlmProvider()
    _grounding(monkeypatch, fake)
    plan = generate(client, allowWebHints=False)
    assert plan["webHintsSkipped"] is True and plan["webHints"] == [] and fake.hint_calls == 0


def test_daily_caps():
    capped = CappedLlmProvider(FakeLlmProvider(), llm_cap=1, grounding_cap=0)
    ctx = StopRankContext(stops=["Jaipur"], interests=[], candidates=["Agra (IN): x"])
    assert asyncio.run(capped.rank_stops(ctx)) is None  # fake returns None but consumes the one call
    assert asyncio.run(capped.estimate_city("Somewhere")) is None
    with pytest.raises(CapReached):
        asyncio.run(capped.price_hint(PriceQuery(kind="STAY", month="2026-12", travel_class="STANDARD", city="Goa")))


def test_cap_reached_falls_back_to_template(client, monkeypatch):
    monkeypatch.setenv("LLM_DAILY_CAP", "1")
    get_settings.cache_clear()
    get_llm.cache_clear()
    first = generate(client)
    second = generate(client)
    assert_spring_contract(second)
    assert first["days"] and second["days"]
    assert any("simple outline" in w for w in second["warnings"])


def test_booking_links_and_affiliates(monkeypatch):
    legs = [LegLink(0, city("Mumbai"), city("Dubai"), TransportMode.FLIGHT, date(2026, 12, 20)),
            LegLink(1, city("Mumbai"), city("Pune"), TransportMode.TRAIN, date(2026, 12, 21)),
            LegLink(2, city("Mumbai"), city("Pune"), TransportMode.BUS, date(2026, 12, 22))]
    stays = [StayLink(1, city("Dubai"), date(2026, 12, 20), date(2026, 12, 22))]
    links = build_links(legs, stays, 2, 1)
    providers = {link.provider for link in links}
    assert providers == {"google_flights", "skyscanner", "irctc", "redbus", "booking", "airbnb"}
    assert all(link.url.startswith("https://") and not link.sponsored for link in links)
    sky = next(link for link in links if link.provider == "skyscanner")
    assert "/bom/dxb/261220/" in sky.url

    monkeypatch.setenv("AFFILIATE_BOOKING_AID", "12345")
    get_settings.cache_clear()
    booking = next(link for link in build_links([], stays, 2, 1) if link.provider == "booking")
    assert booking.sponsored and booking.url.endswith("&aid=12345")


def test_link_template_override_requires_https(monkeypatch):
    monkeypatch.setenv("LINK_TEMPLATES", '{"booking": "http://evil.example/{city_q}", "airbnb": '
                                         '"https://www.airbnb.com/s/{city_q}"}')
    get_settings.cache_clear()
    stays = [StayLink(None, city("Goa"), date(2026, 12, 20), date(2026, 12, 22))]
    links = {link.provider: link.url for link in build_links([], stays, 2, 1)}
    assert links["booking"].startswith("https://www.booking.com/")
    assert links["airbnb"] == "https://www.airbnb.com/s/Goa"
