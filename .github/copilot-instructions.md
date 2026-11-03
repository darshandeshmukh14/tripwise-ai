# Copilot instructions - TripWise AI

AI-assisted travel planning platform. Users register/login (JWT), create trips, get an AI-generated itinerary with cost estimates (from an internal Python FastAPI service) and refine it through chat.

**Monorepo:** Spring Boot backend at the root (`src/`, `pom.xml`) · `ai-service/` (Python 3.12 FastAPI + LangGraph + Gemini, deterministic pricing; see `.github/instructions/ai-service.instructions.md`) · `frontend/` (Next.js 16 App Router BFF + UI; see `.github/instructions/frontend.instructions.md`). Flow: Browser -> Next.js BFF (JWT in httpOnly cookie) -> Spring -> ai-service. Only Spring talks to ai-service; only Spring touches the DB.

**Backend stack:** Java 21 LTS, Spring Boot 3.5.x (Web, Data JPA, Security, Validation), Maven, PostgreSQL 16 (relational + JSONB), Flyway, Lombok, jjwt 0.12.x, JUnit 5 + Mockito + AssertJ.
**Package root:** `com.tripwise`. Entry point: `TripWiseApplication` (`@ConfigurationPropertiesScan`).

## Target package layout (create as features land)
`config/` (Spring config, `@ConfigurationProperties` records) · `security/` (JWT service, filter, `SecurityConfig`, current-user helper) · `controller/` · `service/` · `repository/` · `entity/` · `dto/` (request/response records) · `mapper/` · `client/` (AI service client) · `exception/` (domain exceptions + `@RestControllerAdvice`).

## Key files
- `pom.xml` - dependencies (no Maven wrapper; use `mvn`).
- `docker-compose.yml` - PostgreSQL 16 (`tripwise_db` / `tripwise_user` / `tripwise_password`, port 5432), `ai-service` (loopback port 8000) and `frontend` (port 3000, `BACKEND_URL` -> Spring on the host).
- `src/main/resources/application.yml` - datasource, `ddl-auto: validate`, Flyway, `jwt.secret|expiration|refresh-expiration|refresh-reuse-grace`, `rate-limit.*` (incl. `rate-limit.web-hints`, env `RATE_LIMIT_WEB_HINTS_CAPACITY`, default 20/user/day; `rate-limit.guest-generate`, env `RATE_LIMIT_GUEST_GENERATE_CAPACITY`, default 5/IP/day for public `/api/v1/guest/**` planning without an account), `ai-service.base-url|api-key` (all env-overridable).
- `src/main/resources/db/migration/V*__*.sql` - schema source of truth (PostgreSQL). Tables: `users`, `trips` (`preferences` JSONB, `status` default `DRAFT`), `trip_itineraries` (1:1 with trip, `plan_data` JSONB, `version`), `chat_messages` (`sender_role` in `USER|ASSISTANT|SYSTEM`).
- **Temporary H2 fallback** (until Docker PostgreSQL is available): profile `h2` (`application-h2.yml`, file DB `./data/`), migrations mirrored in `src/main/resources/db/h2/` (JSON instead of JSONB, `ON UPDATE CURRENT_TIMESTAMP` instead of triggers). Every new `db/migration/V{n}` needs an equivalent `db/h2/V{n}` with the same version and name.

## Non-negotiable rules
- **Layering:** Controller -> Service -> Repository. Controllers: HTTP + `@Valid` only. Services: business logic + `@Transactional`. Never return entities from controllers; map to DTO records.
- **Ownership (multitenancy):** every trip/itinerary/chat read or write is scoped to the authenticated user id from the JWT (`findByIdAndUserId`, `existsByIdAndUserId`, `trip.user.id = :userId`). Never accept `userId` from the request body/path. Not found and not owned both return 404.
- **Schema:** only via new Flyway scripts (`V{n}__description.sql`). Never edit an applied migration. Never set `ddl-auto` to `update`/`create`. Entities must match the schema (`validate` fails startup otherwise).
- **Errors:** throw domain exceptions; a single `@RestControllerAdvice` maps them to `ProblemDetail` (RFC 9457). No stack traces or internal messages in responses.
- **Secrets:** `jwt.secret` and `ai-service.api-key` come from config/env only; never log, hard-code elsewhere, or commit real values. Never log passwords, tokens or `password_hash`.
- **AI service:** call FastAPI via `RestClient` with header `X-Internal-Token: ${ai-service.api-key}`, explicit timeouts, non-2xx handling and response validation. Do not invent FastAPI endpoints or payloads - ask if the contract is unknown.
- **Money:** `BigDecimal` (`NUMERIC(12,2)`), compare with `compareTo`.
- **Time:** `TIMESTAMPTZ` columns <-> `Instant`/`OffsetDateTime`; `DATE` <-> `LocalDate`.
- Constructor injection via Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- Smallest complete change. No new dependency, build change or unrelated refactor without approval.

## Build & run
- JDK 21 required (`JAVA_HOME` must point to it; system default may be Java 8).
- DB: `docker compose up -d`, or without Docker use the H2 profile: `mvn spring-boot:run "-Dspring-boot.run.profiles=h2"` (IntelliJ: Active profiles = `h2`). Compile: `mvn -q compile`. Test: `mvn test` (single: `mvn test -Dtest=ClassName`). Run: `mvn spring-boot:run`.
- Keep code DB-portable while H2 is in use: no PostgreSQL-only SQL in JPQL/native queries unless also verified on H2 (or flagged).
- ai-service: `cd ai-service; python -m venv .venv; .\.venv\Scripts\pip install -e ".[dev]"`; test `.\.venv\Scripts\pytest`; lint `.\.venv\Scripts\ruff check .`; run `.\.venv\Scripts\uvicorn app.main:app --port 8000` (env `INTERNAL_API_TOKEN` = Spring `AI_SERVICE_API_KEY`, `LLM_PROVIDER=fake|gemini|openrouter`).
- frontend: `cd frontend; npm ci`; `npm run dev` (port 3000, `BACKEND_URL`), `npm test` (Vitest), `npm run lint`, `npm run typecheck`, `npm run build`, `npm run test:e2e` (Playwright), `npm run gen:api` (types from Spring `/v3/api-docs`, needs `OPENAPI_ENABLED` / dev profile).
- Report real command output; if something cannot run (no Docker/DB), say so instead of claiming success.

## Output
Concise. List changed files with one-line reasons, commands actually run with results, and open risks. Separate facts from assumptions. Don't commit or push without explicit approval.
