"""OpenRouter provider: any model available on OpenRouter (OpenAI-compatible chat completions API) via httpx.

Uses the same prompts and output models as Gemini. Structured output is requested with response_format json_schema;
models that ignore it still get the schema in the system prompt, and every reply is validated with pydantic.
Web price hints need Gemini Google Search grounding, so price_hint always returns None here.
"""

import json
import logging
import re
from typing import TypeVar

import httpx
from pydantic import BaseModel, ValidationError

from app.config import Settings
from app.llm.base import DayPlanContext, LlmError, PriceHint, PriceQuery, RefineContext, StopRankContext
from app.llm.models import CityGuess, DayPlanDraft, RefineDecision, StopRanking
from app.llm.prompts import SYSTEM, city_prompt, day_plan_prompt, rank_stops_prompt, refine_prompt

log = logging.getLogger(__name__)
T = TypeVar("T", bound=BaseModel)

_FENCE = re.compile(r"^```(?:json)?\s*|\s*```$")


class OpenRouterLlmProvider:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None) -> None:
        assert settings.openrouter_api_key is not None
        self._model = settings.openrouter_model
        self._temperature = settings.llm_temperature
        headers = {"Authorization": f"Bearer {settings.openrouter_api_key.get_secret_value()}"}
        # Optional attribution headers documented by OpenRouter
        if settings.openrouter_site_url:
            headers["HTTP-Referer"] = settings.openrouter_site_url
        if settings.openrouter_app_name:
            headers["X-Title"] = settings.openrouter_app_name
        self._client = client or httpx.AsyncClient(
            base_url=settings.openrouter_base_url.rstrip("/"),
            headers=headers,
            timeout=httpx.Timeout(settings.llm_timeout_seconds, connect=5.0),
        )

    async def _call(self, prompt: str, schema: type[T]) -> T:
        json_schema = schema.model_json_schema()
        body = {
            "model": self._model,
            "temperature": self._temperature,
            "messages": [
                {"role": "system", "content": f"{SYSTEM}\nJSON schema:\n{json.dumps(json_schema)}"},
                {"role": "user", "content": prompt},
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": schema.__name__, "strict": False, "schema": json_schema},
            },
        }
        try:
            response = await self._client.post("/chat/completions", json=body)
        except httpx.TimeoutException as exc:
            raise LlmError("LLM call timed out") from exc
        except httpx.HTTPError as exc:
            log.warning("OpenRouter call failed: %s", type(exc).__name__)
            raise LlmError("LLM call failed") from exc
        if response.status_code != 200:
            log.warning("OpenRouter call failed: HTTP %s", response.status_code)
            raise LlmError("LLM call failed")
        try:
            content = response.json()["choices"][0]["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            raise LlmError("LLM returned an unexpected response") from exc
        if not isinstance(content, str):
            raise LlmError("LLM returned an unexpected response")
        try:
            return schema.model_validate_json(_FENCE.sub("", content.strip()))
        except ValidationError as exc:
            raise LlmError("LLM returned invalid JSON") from exc

    async def plan_days(self, ctx: DayPlanContext) -> DayPlanDraft:
        draft = await self._call(day_plan_prompt(ctx), DayPlanDraft)
        if len(draft.days) != ctx.days:
            raise LlmError("LLM returned the wrong number of days")
        return draft

    async def estimate_city(self, name: str) -> CityGuess | None:
        try:
            guess = await self._call(city_prompt(name), CityGuess)
        except LlmError:
            return None
        return guess if guess.found else None

    async def rank_stops(self, ctx: StopRankContext) -> StopRanking | None:
        try:
            return await self._call(rank_stops_prompt(ctx), StopRanking)
        except LlmError:
            return None

    async def price_hint(self, query: PriceQuery) -> PriceHint | None:
        return None

    async def refine(self, ctx: RefineContext) -> RefineDecision:
        decision = await self._call(refine_prompt(ctx), RefineDecision)
        if decision.action == "EDIT_DAYS" and (not decision.days or len(decision.days) != ctx.trip_days):
            raise LlmError("LLM returned the wrong number of days")
        return decision
