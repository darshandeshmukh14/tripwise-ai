---
description: Scaffold a full REST feature (migration, entity, repository, DTOs, mapper, service, controller, exceptions, tests) for a TripWise resource.
agent: Coder
---
Use skill `api-feature-scaffolding` to build the feature below end-to-end. Work in order, keeping each step compiling (`mvn -q compile`):

1. Flyway `V{next}__*.sql` (only if schema changes) -> 2. entity -> 3. repository with user-scoped methods -> 4. request/response DTO records + mapper -> 5. service (`@Transactional`, ownership) -> 6. controller (`/api/v1/...`, `@Valid`) -> 7. exceptions mapped to ProblemDetail -> 8. tests (validation 400, 401, cross-user 404, happy path).

Ask before inventing business rules, status values or FastAPI contracts. Report changed files, commands run, open risks.

Feature:
