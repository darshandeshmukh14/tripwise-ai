---
applyTo: "src/test/java/**/*.java"
description: Testing rules for TripWise AI.
---
- JUnit 5 + Mockito + AssertJ; mirror main packages under `src/test/java/com/tripwise`; class `<Subject>Test`; method names `method_condition_expected`.
- **Services:** `@ExtendWith(MockitoExtension.class)`, mocked repositories/clients; no Spring context.
- **Controllers:** `@WebMvcTest` + `MockMvc` + `spring-security-test` (`@WithMockUser` or a JWT request post-processor); assert status, `ProblemDetail` body on 400/401/404, and JSON shape.
- **Repositories / migrations:** until Docker is available, `@DataJpaTest` + `@ActiveProfiles("h2")` + `@AutoConfigureTestDatabase(replace = NONE)` (runs the `db/h2` migrations; use an in-memory URL override `jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`). Once Docker exists, switch to Testcontainers PostgreSQL (needs approval for deps). PostgreSQL-only behaviour (JSONB operators, triggers) is not covered by H2 - flag it.
- **AI client:** never call the real service; use `MockRestServiceServer` (or a stubbed client) and verify `X-Internal-Token`, timeouts/error mapping.
- Always cover ownership: user B gets 404 on user A's trip/itinerary/chat.
- BigDecimal assertions with `isEqualByComparingTo`.
- Run `mvn test -Dtest=ClassName` and report the real result.
