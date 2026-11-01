---
name: spring-testing
description: Write or review JUnit 5 / Mockito / Spring Boot tests for TripWise AI (services, controllers with security, repositories on PostgreSQL, AI-service client). Use when adding regression tests or deciding what to test.
---
# Layout & run
- `src/test/java/com/tripwise/<same package>/<Class>Test.java`.
- Run with JDK 21: `mvn test -Dtest=TripServiceTest` (all: `mvn test`).

# By layer
- **Service:** `@ExtendWith(MockitoExtension.class)`, `@Mock` repositories/clients, `@InjectMocks` service. Verify ownership lookup uses `userId`; not-owned -> `ResourceNotFoundException`.
- **Controller:** `@WebMvcTest(XController.class)` + `@Import(SecurityConfig.class, ...)` + `@MockitoBean` service/JwtService. Use `spring-security-test` (`with(authentication(...))` or `@WithMockUser`). Assert 201/200/204, 400 with field errors (ProblemDetail), 401 without token, 404 for foreign resources.
- **Repository / migrations:** now: `@DataJpaTest` + `@ActiveProfiles("h2")` + `@AutoConfigureTestDatabase(replace = NONE)` with `spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE` (Flyway runs `db/h2`, `validate` checks entities). Later (Docker available): Testcontainers PostgreSQL 16 via `@ServiceConnection` - needs `spring-boot-testcontainers` + `org.testcontainers:postgresql` (ask first). H2 does not cover JSONB operators or PL/pgSQL triggers - flag such gaps.
- **AI client:** `MockRestServiceServer.bindTo(restClientBuilder)`; assert `X-Internal-Token` header, request body, mapping of 4xx/5xx/timeout to `AiServiceException`, malformed JSON.

# Must-have cases
Validation (blank destination, `endDate < startDate`, `travellersCount <= 0`, negative budget), duplicate email 409, bad credentials 401, cross-user 404, itinerary version increment on regeneration, chat ordering by `createdAt`.
- BigDecimal: `assertThat(actual).isEqualByComparingTo("1500.00")`.
- Report real test output only; if Docker/DB is unavailable, say which tests could not run.
