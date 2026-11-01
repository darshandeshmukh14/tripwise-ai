"""Exchange rates (INR base). All prices are computed in INR-equivalent rates and converted once per plan.

Live rates come from the free ExchangeRate-API open endpoint (daily updates, attribution required); the bundled
fx_fallback.yaml table (with fixed pegs) is used when the provider is disabled, slow or unavailable.
"""

import asyncio
import logging
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from decimal import Decimal
from functools import lru_cache

import httpx

from app.config import get_settings
from app.pricing.catalog import _load
from app.schemas import SUPPORTED_CURRENCIES

log = logging.getLogger(__name__)

LIVE_URL = "https://open.er-api.com/v6/latest/INR"
LIVE_SOURCE = "open.er-api.com"
STATIC_SOURCE = "static"
RETRY_AFTER_FAILURE_SECONDS = 600
__all__ = ["SUPPORTED_CURRENCIES", "FxQuote", "INR", "fx_quote", "static_quote", "reset_cache"]


@dataclass(frozen=True)
class FxQuote:
    quote: str
    rate: Decimal  # units of `quote` per 1 INR
    as_of: datetime
    source: str
    base: str = "INR"


INR = FxQuote(quote="INR", rate=Decimal(1), as_of=datetime(2000, 1, 1, tzinfo=UTC), source=STATIC_SOURCE)


@lru_cache
def static_rates() -> tuple[dict[str, Decimal], datetime]:
    data = _load("fx_fallback.yaml")
    per_usd = {k: Decimal(str(v)) for k, v in data["perUsd"].items()}
    inr_per_usd = per_usd["INR"]
    rates = {k: v / inr_per_usd for k, v in per_usd.items()}
    for code, peg in data.get("pegs", {}).items():
        value = Decimal(str(peg["rate"]))
        rates[code] = value / inr_per_usd if peg["anchor"] == "USD" else value
    as_of = datetime.fromisoformat(str(data["asOf"]).replace("Z", "+00:00"))
    return rates, as_of


class _LiveCache:
    def __init__(self) -> None:
        self.rates: dict[str, Decimal] | None = None
        self.as_of: datetime | None = None
        self.expires = 0.0
        self.lock = asyncio.Lock()

    def reset(self) -> None:
        self.rates, self.as_of, self.expires = None, None, 0.0
        self.lock = asyncio.Lock()


_cache = _LiveCache()


def reset_cache() -> None:
    _cache.reset()


async def _fetch_live(limit_seconds: float) -> tuple[dict[str, Decimal], datetime]:
    async with httpx.AsyncClient(timeout=limit_seconds) as client:
        response = await client.get(LIVE_URL)
        response.raise_for_status()
        body = response.json()
    if body.get("result") != "success" or not isinstance(body.get("rates"), dict):
        raise ValueError("unexpected FX payload")
    rates = {str(k): Decimal(str(v)) for k, v in body["rates"].items() if isinstance(v, int | float) and v > 0}
    as_of = datetime.fromtimestamp(int(body.get("time_last_update_unix", time.time())), tz=UTC)
    return rates, as_of


async def _live_rates() -> tuple[dict[str, Decimal], datetime] | None:
    settings = get_settings()
    now = time.monotonic()
    if now < _cache.expires:
        return (_cache.rates, _cache.as_of) if _cache.rates else None
    async with _cache.lock:
        if time.monotonic() < _cache.expires:
            return (_cache.rates, _cache.as_of) if _cache.rates else None
        try:
            rates, as_of = await _fetch_live(settings.fx_timeout_seconds)
            _cache.rates, _cache.as_of = rates, as_of
            _cache.expires = time.monotonic() + settings.fx_cache_ttl_seconds
        except Exception as exc:  # network/format errors: fall back to the static table, retry later
            log.warning("Live FX rates unavailable (%s); using the static table", type(exc).__name__)
            _cache.expires = time.monotonic() + RETRY_AFTER_FAILURE_SECONDS
            if _cache.rates is None:
                return None
        return _cache.rates, _cache.as_of


def static_quote(currency: str) -> FxQuote:
    code = currency.upper()
    if code == "INR":
        return INR
    rates, as_of = static_rates()
    if code not in rates:
        raise ValueError(f"Unsupported currency {code}")
    return FxQuote(quote=code, rate=rates[code], as_of=as_of, source=STATIC_SOURCE)


@lru_cache
def _pegs() -> dict[str, tuple[str, Decimal]]:
    pegs = _load("fx_fallback.yaml").get("pegs", {})
    return {code: (p["anchor"], Decimal(str(p["rate"]))) for code, p in pegs.items()}


async def fx_quote(currency: str) -> FxQuote:
    """Rate INR -> currency. Unknown currencies raise ValueError; any provider failure uses the static table."""
    code = currency.upper()
    if code not in SUPPORTED_CURRENCIES:
        raise ValueError(f"Unsupported currency {code}")
    if code == "INR":
        return INR
    fallback = static_quote(code)
    peg = _pegs().get(code)
    if get_settings().fx_provider == "static" or (peg and peg[0] == "INR"):
        return fallback
    live = await _live_rates()
    if live is None:
        return fallback
    rates, as_of = live
    if peg:  # USD-pegged: derive from the live USD rate so the peg holds exactly
        usd = rates.get("USD")
        return FxQuote(quote=code, rate=peg[1] * usd, as_of=as_of, source=LIVE_SOURCE) if usd else fallback
    if code not in rates:
        return fallback
    return FxQuote(quote=code, rate=rates[code], as_of=as_of, source=LIVE_SOURCE)
