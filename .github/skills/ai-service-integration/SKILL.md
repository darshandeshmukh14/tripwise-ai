---
name: ai-service-integration
description: Implement or review the Spring Boot client for the internal Python FastAPI AI service (itinerary generation, chat replies) using RestClient and the X-Internal-Token header. Use for any change in client/, AiServiceProperties, or flows that call the AI service.
---
# Design
- Config: `ai-service.base-url`, `ai-service.api-key` (env `AI_SERVICE_BASE_URL`, `AI_SERVICE_API_KEY`) bound to `config/AiServiceProperties` record (`@Validated`, `@NotBlank`). Add timeout properties (e.g. `connect-timeout`, `read-timeout`) only with approval.
- One `RestClient` bean (`config/AiServiceClientConfig`): base URL, `defaultHeader("X-Internal-Token", apiKey)`, JSON content type, `JdkClientHttpRequestFactory` / `SimpleClientHttpRequestFactory` with connect + read timeouts (AI generation is slow - read timeout ~60s).
- `client/AiServiceClient` is the only class that talks HTTP to FastAPI. Methods take/return dedicated records in `client/dto` (never JPA entities).
- Errors: `onStatus(HttpStatusCode::isError, ...)` -> `AiServiceException` (maps to 502 Bad Gateway / 503 via ProblemDetail). Timeouts/IO -> same exception. Validate response (non-null required fields, `plan_data` is a JSON object) before persisting.
- **Contract (known, source of truth `ai-service/app/schemas.py`):** `POST /internal/v1/itineraries/generate` (request `client/dto/AiGenerateTripRequest`, response `AiItineraryResponse`: tripTitle, destination, origin, comfortLevel, totalEstimatedCostInr, totalCost, budgetStatus, season, transportOptions, stay, costBreakdown, warnings, days), `POST /internal/v1/preferences/clarify` (`{questions:[{id,text,type,options}]}`), `POST /internal/v1/itineraries/refine` (`{tripId, trip, currentPlan, history, message}` -> `{assistantMessage, updatedPlan?}`). camelCase, ISO dates, money as JSON numbers; unknown fields ignored. Implemented in `client/AiServiceClient`, validated by `AiResponseValidator`; async pipeline in `service/TripGenerationService` + `TripGenerationWorker`. Change the contract on both sides together (`ai-service/tests/test_generate.py::assert_spring_contract` mirrors the Java validator). Never invent other endpoints.
- The JDK `HttpClient` is pinned to HTTP/1.1: uvicorn rejects the default h2c upgrade.

# Transaction pattern
Do not hold a DB transaction open while waiting for the AI call:
1. Read + ownership check (short tx) -> build request DTO.
2. Call AI service (no tx).
3. Persist result (short tx): upsert `trip_itineraries` (increment `version`), append `chat_messages` (`USER` then `ASSISTANT`), update `trips.status`.

# Safety
- Never log the token or full prompts/plan payloads containing user data; log trip id, latency, status code.
- Send only the data the AI needs (no email, password hash, JWT).
- Tests: `MockRestServiceServer` (bind to the `RestClient.Builder`) or a stubbed `AiServiceClient`; assert header present, error mapping, malformed response handling. Never call the real service.
