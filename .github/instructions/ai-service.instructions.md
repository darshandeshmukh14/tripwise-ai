---
applyTo: "ai-service/**"
description: Rules for the internal Python AI service (FastAPI + LangGraph + Gemini, deterministic pricing).
---
- Python 3.12, FastAPI, Pydantic v2 (`CamelModel`: camelCase aliases), pydantic-settings, LangGraph, `google-genai`. Ruff (line length 120) + pytest. No new dependency without approval.
- **Internal only:** every router requires `X-Internal-Token` (`app/security.py`, constant-time compare); only `/health` is open; OpenAPI docs stay disabled. Never log the token, prompts or plan payloads with user data.
- **Stateless:** no database, no user identity. Spring owns persistence, ownership and the final budget status.
- **Contract:** `app/schemas.py` is the source of truth for `/internal/v1/itineraries/generate`, `/internal/v1/itineraries/refine` and `/internal/v1/preferences/clarify`. Any change must be mirrored in Spring `client/dto` + `AiResponseValidator` and in `tests/test_generate.py::assert_spring_contract`.
- **Money:** `Decimal` internally, serialised as JSON numbers (`Money`/`Number` types); `totalEstimatedCostInr` = sum of rounded expected item amounts. The LLM never does arithmetic or prices: pricing is `app/pricing/engine.py` + YAML data in `app/pricing/data/` (quote YAML keys such as `"OFF"` - YAML 1.1 booleans).
- **LLM:** only via the `LlmProvider` protocol (`app/llm/base.py`); structured output models in `app/llm/models.py`; timeouts and `LlmError` on failure (mapped to 502 with a generic detail). Model name comes from `GEMINI_MODEL`.
- **Graph:** `app/graph/builder.py` - intake -> transport -> stay -> costs -> budget_guard (bounded loops) -> itinerary -> output. Keep nodes pure apart from LLM calls.
- **Tests:** use the fake provider (`LLM_PROVIDER=fake`, `tests/conftest.py`); never call Gemini in tests (stub the SDK as in `tests/test_gemini.py`). Run `.\.venv\Scripts\pytest` and `.\.venv\Scripts\ruff check .`.
- Pricing figures are indicative estimates; always return ranges, season and confidence.
