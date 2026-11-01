# TripWise AI service

Internal FastAPI service called only by the Spring Boot backend (header `X-Internal-Token`).
A LangGraph pipeline plans trips: **all prices come from the deterministic pricing engine** (`app/pricing`,
seed data in `app/pricing/data/*.yaml`); Gemini only writes the day-by-day itinerary, resolves unknown cities
and interprets chat refinements. v1 prices are indicative estimates, not live quotes.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/internal/v1/itineraries/generate` | Full plan: transport options, stay, cost breakdown (min/expected/max), season, days |
| POST | `/internal/v1/itineraries/refine` | Chat turn: answer, replan (prices recomputed) or edit days |
| POST | `/internal/v1/preferences/clarify` | Questions for missing/conflicting inputs (budget, interests, pace, overnight travel) |
| POST | `/internal/v1/stops/route` | Resolve multi-city stops, optionally reorder, check the 400 km road / 6-stop rules |
| POST | `/internal/v1/stops/suggest` | Nearby places (≤400 km) to add to a trip, with extra cost, season and reason |
| GET | `/health` | Liveness (no token) |

The JSON contract mirrors `com.tripwise.client.dto.*` in the backend; change both sides together.
Errors: 401 bad token, 422 invalid body, 502 LLM failure, 500 `{"detail":"Internal error"}`.

## Pipeline

`intake -> transport -> stay -> costs -> budget_guard -(over budget: lower comfort, then cheapest mode; max N loops)-> itinerary (LLM) -> output`

Clarify answers used by the graph: `budgetConflict` (`downgrade`|`keep`), `pace`, `interests`, `overnightTravel` (`yes`|`no`).

## International, multi-city and prices (v2)

- **Coverage:** India plus 29 countries (`countries.yaml`, `cities.yaml`); coordinates are approximate city centres.
  Other countries scale India rates by `priceIndex`.
- **Currency:** everything is priced in INR and converted once per plan to the trip currency (29 codes).
  Rates come from [open.er-api.com](https://www.exchangerate-api.com) (free, daily, attribution required; the
  frontend shows "Rates by Exchange Rate API") with a 24 h cache, falling back to `fx_fallback.yaml` and pegs.
- **Multi-city:** 2–6 stops, each at most 400 km by road (haversine × 1.2) from the previous one; sea or
  international crossings are flight-only, except India–Nepal and India–Bhutan by road. Suggestions come from
  `clusters.yaml`; the LLM only ranks and explains them.
- **Price sources** on each cost item: `ESTIMATE` (rates model), `FARE_RULE` (indicative IRCTC fare slabs,
  `app/prices/fare_rules/irctc.yaml`), `WEB_HINT` (Gemini Google Search grounding, accepted only within
  0.5×–2× of the estimate, always with `sourceUrl`). Grounding is off by default; Google's Search Suggestions
  HTML is passed through as `searchSuggestionsHtml` and must be displayed (the frontend uses a sandboxed iframe).
- **Booking links:** search links (Skyscanner, Google Flights, Airbnb, Booking.com, IRCTC, redBus, 12Go) built from
  templates; affiliate IDs are appended only when the matching `AFFILIATE_*` variable is set (`sponsored: true`).
  Parameter names are unverified: confirm with each programme before launch.
- **Caps:** Spring decides per user whether web hints are allowed (`allowWebHints`); this service enforces
  `GROUNDING_DAILY_CAP` and `LLM_DAILY_CAP` globally (in memory, single instance). Over a cap the plan keeps
  estimates and links and sets `webHintsSkipped`.

See `.env.example` for every setting.

## Run locally

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
Copy-Item .env.example .env   # LLM_PROVIDER=fake works without a Gemini key
.\.venv\Scripts\uvicorn app.main:app --port 8000
```

Use `LLM_PROVIDER=gemini` with `GEMINI_API_KEY` for real plans. Verify `GEMINI_MODEL` against the current
Gemini model list.

Or use `LLM_PROVIDER=openrouter` with `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` (any OpenRouter model id that
supports structured outputs). Both providers share the same prompts (`app/llm/prompts.py`), and replies are validated
the same way. Web price hints need Gemini Google Search grounding, so they are off with OpenRouter.

## Test & lint

```powershell
.\.venv\Scripts\python -m pytest -q
.\.venv\Scripts\ruff check .
```

## Docker

```powershell
docker build -t tripwise-ai-service .
docker run --env-file .env -p 8000:8000 tripwise-ai-service
```
