# Pending work: v2 Phases B, C, D (handoff)

Living handoff file. Update it as items finish. Plan and spec: `.github/plan.md` (Phase B/C/D section).
Rules: `.github/copilot-instructions.md` and `.github/instructions/*.instructions.md`. Don't commit without approval.

## Status

| Tier | State |
|---|---|
| ai-service (`ai-service/`) | **Done.** `ruff` clean, `pytest` 73 passed |
| Spring backend | **Done.** `mvn test`: 156 run, 0 failures, 1 skipped (Postgres Testcontainers) |
| Frontend (`frontend/`) | **Done.** lint + typecheck clean, vitest 57 passed, `next build` OK, Playwright smoke 3 passed (`--workers=1`; parallel run timed out setting up the browser) |
| Docs | **Done.** `ai-service/.env.example`, `ai-service/README.md`, `.github/plan.md` (B–D as built), `.github/copilot-instructions.md` |

## Environment commands (Windows PowerShell 5, no `&&`)

- **Java:**
  ```
  $env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.13.0.2"; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -q compile
  ```
  Use `mvn test` for the full suite. Run `mvn clean test-compile` after record or constructor changes, because incremental builds leave stale test classes behind.
- **Node:** `$env:Path="$env:USERPROFILE\.tools\node;$env:Path"`, then from `frontend/`: `npm.cmd run lint|typecheck|test|build|test:e2e|gen:api`.
- **Python:** from `ai-service/`: `.venv\Scripts\ruff check app tests; .venv\Scripts\pytest -q`.
- **Backend for `gen:api` (in-memory H2):**
  ```
  $env:SPRING_DATASOURCE_URL="jdbc:h2:mem:genapi;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"; mvn -q spring-boot:run "-Dspring-boot.run.profiles=h2"
  ```
  Then `npm.cmd run gen:api` and stop the backend. Wait about 25 seconds for startup before running `gen:api`; an earlier run failed because the backend wasn't ready yet.

## Backend API now available (for the frontend)

- **`POST /api/v1/trips` and `PUT /api/v1/trips/{id}`** accept three new optional fields:
  - `currency`: one of 29 codes, blank means INR. Allowed: INR USD EUR GBP CHF AED OMR NPR BTN LKR MVR THB VND SGD MYR IDR JPY KRW KZT AZN GEL TRY EGP KES TZS ZAR MUR SCR MAD.
  - `stops`: `[{city, countryCode?, nights}]`. Either empty, or 2–6 stops whose `nights` add up to the number of days between `startDate` and `endDate`.
  - `optimizeRouteOrder`: boolean.
- **Validation and route errors:**
  - Bad currency → 400 with `errors.currencySupported`.
  - Bad stops → 400 with `errors.stopsConsistent`.
  - Route rule broken → **422** with `title: "Stops too far apart"` and `violations: string[]`. The rules are max 400 km by road between stops, plus unknown or unsupported places.
  - AI service down → 503.
- When stops are present, the backend sets `destination` to the canonical first stop.
- **`TripDetailResponse`** gains `currency` and `stops: [{seq, city, countryCode, nights}]`. **`TripSummaryResponse`** gains `currency`.
- **`ItineraryResponse`** gains `currency`, `fxRate` and `fxAsOf`.
  - `CostBreakdownResponse` gains `currency`.
  - Cost items gain `stopSeq`, `legIndex` and `sourceUrl`. `source` is one of ESTIMATE, FARE_RULE or WEB_HINT.
  - `budgetStatus` is UNKNOWN when the plan's currency differs from the trip's.
- **`plan` JSON** (`ai-service/app/schemas.py`) adds:
  - `currency`, `totalEstimatedCost` and `fx {base, quote, rate, asOf, source}`.
  - `stops` and `legs`.
  - `bookingLinks [{provider, label, url, kind, legIndex, stopSeq, sponsored}]`.
  - `webHints [{label, sourceUrl, sourceTitle, searchSuggestionsHtml}]` and `webHintsSkipped`.
  - All URLs are validated server-side as http(s).
- **New: `POST /api/v1/trips/{id}/stops/suggest`** returns `{suggestions: [{city, countryCode, nearStop, distanceKm, travelHours, suggestedNights, extraCostMin, extraCostExpected, extraCostMax, currency, season, reason}]}`. It is owner-scoped (404 for other users' trips), rate-limited as an AI endpoint, and returns an empty list once the trip has 6 stops.

## Remaining tasks

All planned B–D items are implemented. Optional follow-ups:
1. **Playwright multi-city flow** (plan "Tests"): needs Spring + ai-service (`LLM_PROVIDER=fake`) running; create a 2-stop trip, generate, assert route timeline, booking links, source badges.
2. **PostgreSQL:** run V6/V7 on Postgres 16 (`docker compose up -d`, then `PostgresMigrationTest`) once Docker is available.
3. **Production Docker stack** (`compose.prod.yml`, `Dockerfile.backend`, `Caddyfile`, `.env.prod.example`; guide in `DEPLOYMENT.md`): not yet built with Docker (no Docker on the dev machine). The same artefacts (jar, ai-service, Next standalone server) were smoke-tested end to end without Docker. On a Docker host, run `docker compose -f compose.prod.yml config --quiet`, then `build`.
4. **Before launch:** verify Gemini model/grounding quotas and each affiliate programme's parameter names (`app/prices/links.py`).
5. **OpenRouter live check:** `LLM_PROVIDER=openrouter` is unit-tested with a mocked HTTP transport only. Run one real generate + refine with your `OPENROUTER_API_KEY` and the chosen `OPENROUTER_MODEL`; the model must support structured outputs (`response_format`).

### Frontend files changed (B–D)
- `src/lib/api/schema.d.ts` (regenerated), `types.ts`, `client.ts` (`violations`, `suggestStops`).
- `src/lib/format.ts` (`formatMoney`, `CURRENCY_OPTIONS`, `SOURCE_META`), `validation.ts` (currency, stops, optimizeRouteOrder).
- `components/trip/trip-form.tsx` (currency select, stops editor, violations), `trip-detail-view.tsx`, `cost-summary.tsx` (FX note, webHintsSkipped), `cost-breakdown-table.tsx`, `stay-card.tsx`, `transport-comparison.tsx`, `app/trips/page.tsx`.
- New: `route-timeline.tsx`, `booking-links.tsx`, `web-hints.tsx`, `stop-suggestions.tsx`.
- Tests: `format.test.ts`, `validation.test.ts`, `trip-components.test.tsx`, `test/fixtures.ts`.
## Spring changes made this session (for review)

- **New files:**
  - `exception/StopsTooFarException`
  - `service/TripStopService`
  - `security/WebHintLimiter`
  - Tests: `TripStopsControllerTest` and `ItineraryWriterTest`
- **`GlobalExceptionHandler`:** 422 handler.
- **`AiServiceClient`:** `checkRoute` and `suggestStops`.
- **`AiResponseValidator`:**
  - Currency and total checks; http(s)-only URLs.
  - `stopSeq` / `legIndex` ranges.
  - Route and suggestion validation.
- **`TripService`:**
  - The route check runs outside the transaction; writes go through `TransactionTemplate`.
  - Stop replacement flushes first to avoid the unique (trip_id, seq) violation.
  - Ownership is checked before the AI call.
- **`TripMapper`:** currency, stops, FX and source fields.
- **`ItineraryWriter`:** currency, FX rate (scale 8), `fxAsOf`, `stopSeq`, `legIndex` and `sourceUrl`.
- **Web hints and rate limiting:**
  - `TripGenerationService` and `ChatService` set `allowWebHints` from the limiter.
  - `RateLimitProperties` adds the `webHints` rule.
  - `application.yml` adds `rate-limit.web-hints` (20 per day).
  - `RateLimitFilter.AI_PATH` now includes `stops/suggest`.
  - `TripController` adds the suggest endpoint.

## Open risks

- **Unverified external setup:** Gemini grounding, model choice and quotas; affiliate parameter names (especially redBus).
- **Approximate data:** coordinates, price indices, FX fallback rates and IRCTC fare slabs. There are 296 Indian cities against a target of about 300. The road distance factor is 1.2.
- **Single instance only:** caps, caches and rate limits live in memory.
- **AI dependency:** creating or updating a multi-stop trip needs the ai-service up; otherwise it returns 503.
- **Database:** still H2 only; the Postgres V6/V7 migrations haven't been run on Postgres.
