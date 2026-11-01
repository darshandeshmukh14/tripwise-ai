---
applyTo: "src/main/java/**/*.java"
description: Java/Spring Boot coding rules for TripWise AI production code.
---
- Java 21 / Spring Boot 3.5. Constructor injection with Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- **DTOs:** Java `record`s in `dto/` (`CreateTripRequest`, `TripResponse`, ...) with Bean Validation (`@NotBlank`, `@Email`, `@Size`, `@Positive`, `@FutureOrPresent`, `@Valid` on nested). Cross-field rules (e.g. `endDate >= startDate`) via a class-level constraint or service check.
- **Entities:** in `entity/`; Lombok `@Getter @Setter @NoArgsConstructor` (no `@Data`, no `@ToString`/`@EqualsAndHashCode` over lazy relations). `@ManyToOne(fetch = LAZY)`. IDs `Long` with `GenerationType.IDENTITY`. Column names/types must match Flyway DDL exactly. JSONB with `@JdbcTypeCode(SqlTypes.JSON)`. Enums as `@Enumerated(EnumType.STRING)`.
- **Controllers:** `@RestController` under `/api/v1/...`; take `@Valid @RequestBody` DTOs; get the current user from the security context (never from request params); return DTOs / `ResponseEntity`; `201 Created` + `Location` for creates; no business logic.
- **Services:** own `@Transactional` boundaries (`readOnly = true` for queries); enforce ownership via repository methods taking `userId`; map entities -> DTOs before returning.
- **Repositories:** Spring Data interfaces. Trip-scoped queries always include `userId` (`findByIdAndUserId`, `findAllByUserId(Long, Pageable)`). Use `@EntityGraph`/fetch joins to avoid N+1.
- **Errors:** throw domain exceptions (`ResourceNotFoundException`, `DuplicateEmailException`, `AiServiceException`, ...); map them in one `@RestControllerAdvice` to `ProblemDetail`. 404 for not-found-or-not-owned.
- **Config:** typed `@ConfigurationProperties` records (`JwtProperties` for `jwt.*`, `AiServiceProperties` for `ai-service.*`) with `@Validated`; no scattered `@Value`.
- **HTTP clients:** `RestClient` bean with base URL, `X-Internal-Token` default header, connect/read timeouts; translate errors to `AiServiceException`.
- **Logging:** SLF4J (`@Slf4j`); never log secrets, JWTs, passwords or full AI payloads containing user data.
- Money `BigDecimal`; timestamps `Instant`/`OffsetDateTime`; dates `LocalDate`.
