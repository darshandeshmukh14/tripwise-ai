# TripWise AI – Implementation Plan

## Decisions
- Monorepo: Spring Boot at root (unchanged), `frontend/` (Next.js), `ai-service/` (Python).
- LLM: Google Gemini (model names via env; verify current models).
- Pricing v1: **estimates only** (ranges + confidence), no paid price APIs.
- New dependencies approved as needed.
- Frontend talks only to Spring; only Spring talks to ai-service (X-Internal-Token).

## Architecture
Browser -> Next.js (BFF: JWT kept in an httpOnly cookie, proxies /api) -> Spring Boot (auth, ownership, persistence, budget checks) -> ai-service (FastAPI + LangGraph + Gemini, stateless).
- ai-service never touches the DB; Spring persists results.
- LLM never does arithmetic; pricing is deterministic Python code.

## Phase 1 – Backend gap fixes
1. V2 migration (db/migration + db/h2): `trips.preferences` default `[]`.
2. Stuck-generation sweeper: `@Scheduled`, GENERATING older than 10 min -> FAILED.
3. Profiles: dev JWT/API-key defaults only in h2/dev profile; prod fails fast without env; base log level INFO.
4. Trip `PUT`/`DELETE /api/v1/trips/{id}` (owner-scoped, 409 while GENERATING); `GET /api/v1/auth/me`.
5. Graceful shutdown; consistent `CurrentUserProvider`; CORS via `cors.allowed-origins` (dev only, BFF makes it mostly unnecessary).
6. Add actuator (health), springdoc-openapi (frontend types source).

## Phase 2 – Trip input & schema
- V3: `trips` add `origin VARCHAR(255)`, `comfort_level VARCHAR(20)` (BUDGET|STANDARD|PREMIUM|LUXURY), `transport_modes JSONB DEFAULT '[]'` (TRAIN|FLIGHT|BUS|CAR), `currency CHAR(3) DEFAULT 'INR'`.
- V4: `trip_cost_items` (id, trip_id FK cascade, itinerary_version, category TRANSPORT|STAY|FOOD|ACTIVITY|LOCAL_TRANSPORT|MISC, mode, label, amount_min/expected/max NUMERIC(12,2), season PEAK|SHOULDER|OFF, source ESTIMATE, confidence LOW|MEDIUM|HIGH, assumptions TEXT, created_at); index (trip_id, itinerary_version).
- `CreateTripRequest`/`UpdateTripRequest` gain origin, comfortLevel, transportModes; detail response gains costBreakdown.

## Phase 3 – AI service contract (camelCase, Pydantic alias_generator=to_camel)
- `POST /internal/v1/preferences/clarify` -> `{questions:[{id, text, type, options}]}` (only for missing/conflicting inputs).
- `POST /internal/v1/itineraries/generate` (existing, extended request: origin, comfortLevel, transportModes, currency, answers).
  Response adds: `transportOptions[{mode, travelClass, durationHours, cost{min,expected,max}, comfortScore, recommended}]`,
  `stay{tier, nightlyRate{min,expected,max}, nights, areaSuggestion}`, `season{type, multiplier, notes}`,
  `costBreakdown[{category, mode, label, amount{min,expected,max}, season, source, confidence, assumptions}]`,
  `totalEstimatedCostInr`, `budgetStatus` (WITHIN|NEAR|OVER), `warnings[]`, `days[]`.
- `POST /internal/v1/itineraries/refine` (chat): {tripId, currentPlan, history[], message} -> {assistantMessage, updatedPlan?}.
- Errors: non-2xx with `{detail}`; Spring maps to FAILED / 503.

## Phase 4 – ai-service (Python 3.12, uv)
- Deps: fastapi, uvicorn, pydantic v2, pydantic-settings, langgraph, langchain-google-genai (or google-genai), httpx, pytest, ruff.
- LangGraph flow: intake -> transport -> stay -> pricing (deterministic) -> itinerary (Gemini, structured output) -> budget_guard (deterministic; downgrade tier/mode, max 2 loops) -> output.
- Pricing engine (v1 estimates): seed data files (`data/cities.yaml` coords + cost tier, `data/seasons.yaml` month->season multiplier, `data/rates.yaml` per-km rates by mode/class, nightly rates by comfort tier × city tier, daily food/activity by tier).
    - Distance: haversine × road/rail factor (≈1.3); car = distance/mileage × fuel price + toll estimate.
    - Cost = base × comfort multiplier × season multiplier × travellers/rooms × nights; return min/expected/max.
    - Unknown city: Gemini estimates tier/coords -> confidence LOW.
- Middleware: validate X-Internal-Token; timeouts; tests with a fake LLM.

## Phase 5 – Spring integration
- Extend AI client DTOs; persist `trip_cost_items` per itinerary version; validate totals (sum check, non-negative, budget status recomputed in Java).
- Clarify endpoint: `POST /api/v1/trips/{id}/clarify`.
- Chat: `GET/POST /api/v1/trips/{id}/messages` (owner-scoped, paged) -> refine -> new itinerary version.
- Progress: polling `/status` in v1; SSE in v1.1.
- Rate limit login/generate/chat (Bucket4j).

## Phase 6 – Frontend (`frontend/`)
- Next.js (App Router), TypeScript, Tailwind, shadcn/ui, TanStack Query, React Hook Form + Zod, openapi-typescript (types from springdoc).
- Pages: login/register; trips list; new-trip wizard (destination/origin/dates/travellers -> budget -> transport modes -> comfort -> clarifying questions); trip detail (status polling, transport comparison, stay, day-by-day plan, cost breakdown with "estimate" + confidence badges, season note); chat panel; regenerate/retry.
- Tests: Vitest + Playwright.

## Phase 7 – PostgreSQL switch & ops
- docker-compose: postgres, ai-service, frontend. Run Flyway on PG; Testcontainers for Spring integration tests.
- Then remove H2 profile, `db/h2/`, H2 dep; update `.github` docs and add `ai-service`/`frontend` instructions.
- Status: compose services, Dockerfiles and docs done. **Deferred:** running Flyway/Testcontainers on PG and removing H2 – blocked until Docker is available locally.

## Risks
- Estimates may differ from real prices -> always show ranges, confidence and "estimate" labels.
- Gemini cost/quota/latency -> timeouts, max loops, token limits, caching later (Redis in v2).
- Contract drift Spring <-> Python -> contract tests on both sides.
- Real price APIs (v2): check licensing (trains/buses have no open public API that I know of).

---

# TripWise AI – v2 Plan (approved)

## Decisions
- Default currency INR; any supported currency selectable per trip.
- Coverage (30): India (all states/UTs), Nepal, Bhutan, Sri Lanka, Maldives, Thailand, Vietnam, Singapore, Malaysia, Indonesia, Japan, South Korea, UAE (Dubai/Abu Dhabi), Oman, Kazakhstan, Azerbaijan, Georgia, Turkey, Egypt, Kenya, Tanzania, South Africa, Mauritius, Seychelles, Morocco, UK, France, Switzerland, Italy, USA.
- Multi-city: max 6 stops; consecutive stops ≤ 400 km by road (origin → first stop exempt).
- Auth: access JWT 15 min + rotating refresh token 7 days.
- Prices: no paid APIs. Deterministic estimates + official fare rules + capped Gemini web hints + booking links (with optional affiliate IDs).
- Order: A → B → (C, D). Each Flyway `V{n}` mirrored in `db/h2/V{n}`; entities updated in the same step.

## Phase A – Review gap fixes
1. **Chat history:** load newest-first (`createdAt DESC`); client reverses and offers "load older".
2. **Planning answers:** V5 `trips.planning_answers` (JSONB / JSON on H2). Saved in `startGeneration`, reused by generate/refine/regenerate. `budget` answer applied server-side (remove frontend PUT workaround).
3. **Login rate limit:** per-email limit (`RATE_LIMIT_LOGIN_EMAIL_CAPACITY`, default 5/min) + looser per-IP limit. As built: `TRUST_PROXY_HEADERS` kept in the BFF; the per-email limit is the main brute-force guard.
4. **ai-service latency:** parallel city lookups (`asyncio.gather`), per-LLM-call timeout, overall ~50 s deadline (< Spring 60 s read timeout), template day-plan fallback.
5. **Concurrent edits:** refine carries `expectedVersion`; mismatch → 409 ProblemDetail "Itinerary changed".
6. **Edit trip:** shared `TripForm`, `/trips/[id]/edit`, "plan outdated" banner when trip changed after last itinerary.
7. **Restart recovery:** on `ApplicationReadyEvent` mark `GENERATING` trips `FAILED` (single-instance assumption).
8. **Short-lived tokens:**
   - `JWT_EXPIRATION_MS` default 900000 (15 min); `JWT_REFRESH_EXPIRATION` default 7d; `JWT_REFRESH_REUSE_GRACE` default 10s (parallel refresh race).
   - V5 `refresh_tokens` (user_id, family_id, token_hash SHA-256, expires_at, revoked_at, created_at). Rotate on every use; reuse of a rotated token revokes the whole family.
   - `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` (revokes family).
   - BFF: `tw_access` + `tw_refresh` (path `/`, so `proxy.ts` and `/api/backend` see it) HttpOnly cookies; refresh ahead of expiry or on 401 once and retry (in-process dedupe), else clear both. `proxy.ts` uses refresh cookie for session presence.
9. **As built (A4):** ai-service env `PLAN_DEADLINE_SECONDS` (default 50, max 58) and `CITY_LOOKUP_TIMEOUT_SECONDS` (default 10). Day-plan LLM failure/timeout -> 200 with template days + warning; refine failure/timeout -> 502.

**Status:** Phase A done.

## Phase B – International & currency
- `ai-service/app/pricing/data/countries.yaml`: ISO code, currency, price index vs India, climate/hemisphere season profile, visa note ("check official sources").
- `cities.yaml`: add `countryCode`; ~300 Indian cities, 5–10 per other country; coordinates from GeoNames (CC-BY, attribution in README/UI).
- Pricing in each city's local currency, converted to trip currency.
- FX: open.er-api.com (free, daily, attribution) with 24 h cache; pegged fallbacks (AED 3.6725/USD, OMR 0.3845/USD, BTN = INR, NPR 1.6/INR). Plan stores `fx {base, quote, rate, asOf}`.
- Transport rules: sea/international crossing → flight only, except India–Nepal and India–Bhutan by road.
- V7: `trip_itineraries.currency`, `fx_rate NUMERIC(18,8)`, `fx_as_of`; `trip_cost_items.currency`.
- Frontend: currency selector; `Intl.NumberFormat` for any currency; FX "as of" note.

## Phase C – Multi-city ("club nearby places")
- V6 `trip_stops` (id, trip_id FK, seq, city, country_code, nights, created_at); unique/index (trip_id, seq). ≤ 6 stops; sum of nights = trip nights.
- Distance: haversine × road factor in ai-service; Spring maps violation → 422 "Stops too far apart".
- `POST /api/v1/trips/{id}/stops/suggest` (owner-scoped) → ai-service `POST /internal/v1/stops/suggest`:
  - Deterministic candidates within 400 km from `clusters.yaml` (Golden Triangle, Kerala backwaters, Goa–Gokarna, Bangkok–Pattaya, Phuket–Krabi, Dubai–Abu Dhabi, Almaty region, Colombo–Kandy–Galle, Kathmandu–Pokhara, …), filtered by season fit.
  - LLM only ranks and explains; each candidate returns extra cost, travel time, suggested nights.
- Route order: brute-force best order (n ≤ 6) or keep user order. Per-leg transport options, per-stop stay.
- Contract: request gains `stops[]`; `destination` = first stop for backward compatibility. V7 `trip_cost_items.stop_seq`, `leg_index`.
- Frontend: "Add nearby places" wizard step, route timeline on trip detail.

## Phase D – Prices, links, affiliates, Gemini cap
- **Provider findings** (no free public API; link-out only): Skyscanner (partner only), Airbnb (no API; scraping violates ToS), IRCTC (authorised partners only), redBus (no API), Google Flights/Hotels (no API; SerpApi paid), Booking/Expedia/Duffel/Amadeus (approval or paid). *Verify before launch.*
- **Fare rules:** `app/prices/fare_rules/irctc.yaml` – distance slabs by class + reservation, superfast, GST → cost `source = FARE_RULE`.
- **Web hints:** Gemini Google Search grounding; accept only within [0.5×, 2×] of deterministic estimate; always keep source URL; 24 h cache per route+month; `source = WEB_HINT`. UI must show Google Search Suggestions as required by Google's terms.
- **Links:** `app/prices/links.py` with URL templates in config (Skyscanner, Google Flights, Airbnb, Booking.com, IRCTC, redBus, 12Go for SE Asia buses/trains). Links live in plan JSON.
- **Affiliate layer:** optional per-provider IDs from env (`AFFILIATE_SKYSCANNER_ID`, `AFFILIATE_BOOKING_AID`, `AFFILIATE_AIRBNB_ID`, `AFFILIATE_REDBUS_ID`, `AFFILIATE_12GO_ID`); appended only when set, links unchanged otherwise. UI: `rel="sponsored noopener noreferrer"` + "We may earn a commission" disclosure.
- **Gemini cap:**
  - Spring: per-user daily cap on web-hint generations (Bucket4j, `RATE_LIMIT_WEB_HINTS_CAPACITY`, default 20/day); sends `allowWebHints` to ai-service (no user identity leaves Spring).
  - ai-service: global daily cap `GROUNDING_DAILY_CAP` + overall `LLM_DAILY_CAP`; counters in memory (single instance).
  - Over cap → deterministic estimates + links only; response flags `webHintsSkipped`.
- V7: `trip_cost_items.source` CHECK widened to `ESTIMATE | FARE_RULE | WEB_HINT`; add `source_url`.

**As built (B–D):**
- ai-service: `POST /internal/v1/stops/route` (resolve, reorder, rule check) and `/stops/suggest`; road factor 1.2; 296 Indian cities + 29 countries; FX attribution "Rates by Exchange Rate API" shown only for live rates; `GROUNDING_ENABLED` default false.
- Spring: route check runs before the DB transaction on create/update (multi-stop create/update returns 503 if ai-service is down); `destination` becomes the canonical first stop; `budgetStatus` is `UNKNOWN` when itinerary and trip currencies differ; web-hint cap via `WebHintLimiter`; `/stops/suggest` is rate-limited as an AI endpoint and returns `[]` at 6 stops.
- Frontend: currency select + `formatMoney`; multi-city stops editor (nights total, optimise order, 422 `violations` list); route timeline with per-leg transport and per-stop stay; source badges + `sourceUrl`; booking links (`rel="sponsored"` + disclosure); web hints with sandboxed Search Suggestions iframe; FX note; `webHintsSkipped` notice; "Add nearby places" suggestions. Playwright multi-city flow not added (needs Spring + ai-service running).

**Status:** Phases B, C, D done (H2 only; Postgres V6/V7 unverified).

## Flyway summary (each mirrored in `db/h2`)
- V5: `trips.planning_answers`, `refresh_tokens`.
- V6: `trip_stops`.
- V7: itinerary `currency`/`fx_rate`/`fx_as_of`; cost item `currency`/`stop_seq`/`leg_index`/`source_url`, widened `source` CHECK.

## New config / env
- Spring: `JWT_EXPIRATION_MS` (900000), `JWT_REFRESH_EXPIRATION`, `RATE_LIMIT_WEB_HINTS_CAPACITY`, `server.tomcat.remoteip.internal-proxies`.
- ai-service: `FX_PROVIDER`, `GROUNDING_ENABLED`, `PRICE_HINT_CACHE_TTL`, `GROUNDING_DAILY_CAP`, `LLM_DAILY_CAP`, `LINK_TEMPLATES`, `AFFILIATE_*`.

## Tests
- Spring: refresh rotation + reuse detection, logout revoke, per-email login limit, cross-user 404 on stops/suggest, 409 version conflict, 422 distance, startup recovery, web-hint cap flag.
- ai-service: 400 km + 6-stop rules, route ordering, FX conversion + peg fallback, IRCTC fare samples, hint clamping, link + affiliate generation, global cap fallback, deadline fallback.
- Frontend: refresh-retry in BFF, currency formatting, stops step, source/affiliate badges; Playwright multi-city flow with fake LLM.

## v2 Risks
- Gemini model name, grounding quotas and pricing unverified.
- Provider/API availability from memory, not live-checked.
- Prices indicative only; FX daily.
- Still on H2; PostgreSQL/Testcontainers unverified until Docker is available.
- In-memory rate limits, caps and async queue are single-instance only.