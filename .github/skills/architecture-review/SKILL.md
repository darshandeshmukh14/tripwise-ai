---
name: architecture-review
description: Review a plan or diff in TripWise AI for fit with Controller -> Service -> Repository layering, user ownership, Flyway-owned schema, JWT security, JSONB modelling and AI-service integration. Use for new classes/flows, external calls or cross-package changes.
---
# Checklist
1. **Layering:** controller (HTTP, `@Valid`, no logic) -> service (`@Transactional`, ownership, mapping) -> repository. No entity in API responses; no repository use in controllers; HTTP to FastAPI only from `client/`.
2. **Ownership:** every trip-scoped access goes through a `userId`-scoped query; current user from security context only; not-owned == 404.
3. **Schema:** Flyway is the only DDL source; entities match DDL (`validate`); new FKs indexed; JSONB only for flexible/semi-structured data (preferences, plan), relational columns for anything filtered, joined or constrained.
4. **Security:** stateless JWT (`SessionCreationPolicy.STATELESS`), CSRF disabled only because stateless; public paths limited to auth (+ actuator health if added); BCrypt passwords; secrets from config.
5. **Transactions:** no long-running external call inside a DB transaction; readOnly for queries; avoid N+1 (`@EntityGraph`, fetch join, DTO projections).
6. **Errors/contract:** single `@RestControllerAdvice` producing `ProblemDetail`; versioned paths `/api/v1`; pagination for lists.
7. **Config:** typed `@ConfigurationProperties` records; env-overridable; no new dependency without justification.
Report: violation -> why -> smaller alternative.
