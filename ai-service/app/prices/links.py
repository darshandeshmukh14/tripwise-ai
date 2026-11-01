"""Booking/search deep links (no live prices). Affiliate parameters are added only when configured, which
also marks the link as sponsored so the UI can disclose it. Templates can be overridden with LINK_TEMPLATES
(JSON object provider -> template); placeholders use str.format names below."""

import json
import logging
from dataclasses import dataclass
from datetime import date
from functools import lru_cache
from urllib.parse import quote, urlencode, urlsplit

from app.config import get_settings
from app.pricing.catalog import City
from app.schemas import BookingLink, TransportMode

log = logging.getLogger(__name__)

DEFAULT_TEMPLATES = {
    "google_flights": "https://www.google.com/travel/flights?q=Flights%20from%20{from_q}%20to%20{to_q}%20on%20{date}",
    "skyscanner": "https://www.skyscanner.co.in/transport/flights/{from_iata}/{to_iata}/{date_yymmdd}/?adultsv2={adults}",
    "irctc": "https://www.irctc.co.in/nget/train-search",
    "redbus": "https://www.redbus.in/bus-tickets/{from_slug}-to-{to_slug}",
    "12go": "https://12go.asia/en/travel/{from_slug}/{to_slug}",
    "booking": "https://www.booking.com/searchresults.html?ss={city_q}&checkin={checkin}&checkout={checkout}"
               "&group_adults={adults}&no_rooms={rooms}",
    "airbnb": "https://www.airbnb.co.in/s/{city_q}/homes?checkin={checkin}&checkout={checkout}&adults={adults}",
}
LABELS = {
    "google_flights": "Google Flights", "skyscanner": "Skyscanner", "irctc": "IRCTC", "redbus": "redBus",
    "12go": "12Go", "booking": "Booking.com", "airbnb": "Airbnb",
}
TWELVE_GO_COUNTRIES = {"TH", "VN", "MY", "SG", "ID"}


def _affiliates() -> dict[str, tuple[str, str]]:
    s = get_settings()
    # Parameter names follow each programme's public docs; verify them when joining (redBus is unverified).
    return {
        "skyscanner": ("associateid", s.affiliate_skyscanner_id),
        "booking": ("aid", s.affiliate_booking_aid),
        "airbnb": ("af", s.affiliate_airbnb_id),
        "redbus": ("affiliate_id", s.affiliate_redbus_id),
        "12go": ("z", s.affiliate_12go_id),
    }


@lru_cache
def _overrides(raw: str) -> dict[str, str]:
    if not raw.strip():
        return {}
    try:
        data = json.loads(raw)
    except ValueError:
        log.warning("LINK_TEMPLATES is not valid JSON; using the defaults")
        return {}
    if not isinstance(data, dict):
        return {}
    return {k: v for k, v in data.items() if isinstance(v, str) and v.startswith("https://") and k in DEFAULT_TEMPLATES}


def templates() -> dict[str, str]:
    return {**DEFAULT_TEMPLATES, **_overrides(get_settings().link_templates)}


def _slug(name: str) -> str:
    return "-".join(name.lower().split())


@dataclass(frozen=True)
class LegLink:
    index: int | None
    origin: City
    destination: City
    mode: TransportMode
    on: date


@dataclass(frozen=True)
class StayLink:
    seq: int | None
    city: City
    checkin: date
    checkout: date


def _link(provider: str, kind: str, label: str, values: dict[str, str], *, leg: int | None = None,
          stop: int | None = None) -> BookingLink | None:
    template = templates().get(provider)
    if not template:
        return None
    try:
        url = template.format(**values)
    except (KeyError, IndexError, ValueError):
        log.warning("Link template for %s has unknown placeholders", provider)
        return None
    param, value = _affiliates().get(provider, ("", ""))
    sponsored = bool(value)
    if sponsored:
        url += ("&" if urlsplit(url).query else "?") + urlencode({param: value})
    return BookingLink(provider=provider, label=label, url=url, kind=kind, leg_index=leg, stop_seq=stop,
                       sponsored=sponsored)


def build_links(legs: list[LegLink], stays: list[StayLink], travellers: int, rooms: int) -> list[BookingLink]:
    links: list[BookingLink | None] = []
    for leg in legs:
        a, b = leg.origin, leg.destination
        values = {
            "from_q": quote(a.name), "to_q": quote(b.name), "date": leg.on.isoformat(),
            "date_yymmdd": leg.on.strftime("%y%m%d"), "from_iata": (a.iata or "").lower(),
            "to_iata": (b.iata or "").lower(), "from_slug": quote(_slug(a.name)), "to_slug": quote(_slug(b.name)),
            "adults": str(travellers),
        }
        route = f"{a.name} to {b.name}"
        if leg.mode is TransportMode.FLIGHT:
            links.append(_link("google_flights", "TRANSPORT", f"Flights {route} on Google Flights", values,
                               leg=leg.index))
            if a.iata and b.iata:
                links.append(_link("skyscanner", "TRANSPORT", f"Flights {route} on Skyscanner", values,
                                   leg=leg.index))
        elif leg.mode is TransportMode.TRAIN and a.country == b.country == "IN":
            links.append(_link("irctc", "TRANSPORT", f"Trains {route} on IRCTC", values, leg=leg.index))
        elif leg.mode is TransportMode.BUS:
            if a.country == b.country == "IN":
                links.append(_link("redbus", "TRANSPORT", f"Buses {route} on redBus", values, leg=leg.index))
            elif a.country in TWELVE_GO_COUNTRIES and b.country in TWELVE_GO_COUNTRIES:
                links.append(_link("12go", "TRANSPORT", f"Buses {route} on 12Go", values, leg=leg.index))
    for stay in stays:
        values = {
            "city_q": quote(stay.city.name), "checkin": stay.checkin.isoformat(),
            "checkout": stay.checkout.isoformat(), "adults": str(travellers), "rooms": str(rooms),
        }
        for provider in ("booking", "airbnb"):
            links.append(_link(provider, "STAY", f"Stays in {stay.city.name} on {LABELS[provider]}", values,
                               stop=stay.seq))
    return [link for link in links if link is not None]
