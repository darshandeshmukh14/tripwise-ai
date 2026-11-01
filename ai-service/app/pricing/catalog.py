"""Loads the YAML seed data once and resolves cities and seasons."""

from collections import Counter
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml

from app.schemas import Season

DATA_DIR = Path(__file__).parent / "data"


@dataclass(frozen=True)
class City:
    name: str
    lat: float
    lon: float
    tier: int
    airport: bool
    rail: bool
    season_profile: str
    island: bool = False
    # False when the city is not in the catalogue and its attributes were estimated (by the LLM)
    known: bool = True
    country: str = "IN"
    iata: str | None = None
    # Islands of the same group are connected by road/boat (e.g. Bali and Ubud)
    island_group: str | None = None


@dataclass(frozen=True)
class Country:
    code: str
    name: str
    currency: str
    price_index: Decimal
    season_profile: str
    road_neighbours: tuple[str, ...]
    visa_note: str


@dataclass(frozen=True)
class SeasonEstimate:
    type: Season
    multiplier: Decimal
    notes: str


def _load(name: str) -> dict[str, Any]:
    with (DATA_DIR / name).open(encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def normalize(name: str) -> str:
    return " ".join(name.lower().replace(",", " ").split())


@lru_cache
def rates() -> dict[str, Any]:
    return _load("rates.yaml")


@lru_cache
def seasons() -> dict[str, Any]:
    return _load("seasons.yaml")


@lru_cache
def _countries() -> dict[str, Country]:
    return {
        code: Country(
            code=code,
            name=raw["name"],
            currency=raw["currency"],
            price_index=Decimal(str(raw["priceIndex"])),
            season_profile=raw["seasonProfile"],
            road_neighbours=tuple(raw.get("roadNeighbours", [])),
            visa_note=raw.get("visaNote", ""),
        )
        for code, raw in _load("countries.yaml")["countries"].items()
    }


def country(code: str | None) -> Country:
    """Country by ISO code; unknown or missing codes fall back to India."""
    data = _countries()
    return data.get((code or "IN").upper(), data["IN"])


def country_codes() -> list[str]:
    return list(_countries().keys())


def is_supported_country(code: str | None) -> bool:
    return bool(code) and code.upper() in _countries()


@lru_cache
def all_cities() -> tuple[City, ...]:
    cities = []
    for raw in _load("cities.yaml")["cities"]:
        cities.append(
            City(
                name=raw["name"],
                lat=float(raw["lat"]),
                lon=float(raw["lon"]),
                tier=int(raw["tier"]),
                airport=bool(raw["airport"]),
                rail=bool(raw["rail"]),
                season_profile=raw["seasonProfile"],
                island=bool(raw.get("island", False)),
                country=raw.get("country", "IN"),
                iata=raw.get("iata"),
                island_group=raw.get("islandGroup"),
            )
        )
    return tuple(cities)


@lru_cache
def _city_index() -> dict[str, City]:
    index: dict[str, City] = {}
    raw_cities = _load("cities.yaml")["cities"]
    for raw, city in zip(raw_cities, all_cities(), strict=True):
        for key in [raw["name"], *raw.get("aliases", [])]:
            index[normalize(key)] = city
    return index


def find_city(name: str | None) -> City | None:
    """Exact (case-insensitive) match on name or alias; also tries the first comma-separated part."""
    if not name or not name.strip():
        return None
    index = _city_index()
    key = normalize(name)
    if key in index:
        return index[key]
    first = normalize(name.split(",")[0])
    return index.get(first)


def season_profiles() -> list[str]:
    return list(seasons()["profiles"].keys())


@lru_cache
def clusters() -> dict[str, tuple[str, ...]]:
    return {name: tuple(members) for name, members in _load("clusters.yaml")["clusters"].items()}


def season_for(profile: str, start: date, end: date) -> SeasonEstimate:
    """Season of the majority of trip days; multiplier averaged over all days."""
    data = seasons()
    prof = data["profiles"].get(profile) or data["profiles"]["metro"]
    month_to_season = {m: Season(s) for s in ("PEAK", "SHOULDER", "OFF") for m in prof[s]}
    multipliers = {Season(k): Decimal(str(v)) for k, v in data["multipliers"].items()}

    days = [start + timedelta(days=i) for i in range((end - start).days + 1)]
    per_day = [month_to_season[d.month] for d in days]
    majority = Counter(per_day).most_common(1)[0][0]
    avg = sum((multipliers[s] for s in per_day), Decimal(0)) / len(per_day)
    return SeasonEstimate(type=majority, multiplier=avg.quantize(Decimal("0.01")), notes=prof.get("notes", ""))
