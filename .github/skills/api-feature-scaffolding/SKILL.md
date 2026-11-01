---
name: api-feature-scaffolding
description: Build a complete TripWise REST feature (entity, repository, DTO records, mapper, service, controller, exceptions, tests) following Controller -> Service -> Repository with user ownership. Use when adding a new resource or endpoint set (trips, itineraries, chat, users).
---
# Feature scaffolding (resource `X`, e.g. Trip)

| Layer | Class | Rules |
|---|---|---|
| Migration | `V{n}__create_x.sql` | Only if schema changes; see skill `flyway-migrations`. |
| Entity | `entity/X` | Matches DDL; LAZY `@ManyToOne User user`; JSONB via skill `jpa-jsonb-mapping`. |
| Repository | `repository/XRepository` | `Optional<X> findByIdAndUserId(Long id, Long userId)`, `Page<X> findAllByUserId(Long userId, Pageable p)`, `boolean existsByIdAndUserId(...)`. For children (itinerary/chat): `findByTripIdAndTripUserId(...)`. |
| DTOs | `dto/CreateXRequest`, `dto/UpdateXRequest`, `dto/XResponse` | `record`s; Bean Validation on requests; responses never expose entities, `password_hash` or other users' data. |
| Mapper | `mapper/XMapper` | `@Component` with plain methods (no MapStruct unless approved). |
| Service | `service/XService` | `@Transactional` / `@Transactional(readOnly = true)`; signature takes `Long currentUserId`; load via user-scoped repo methods; throw `ResourceNotFoundException` when absent or not owned. |
| Controller | `controller/XController` | `@RequestMapping("/api/v1/xs")`; current user from security context (see skill `security-jwt-ownership`); `POST` -> 201 + `Location`; `GET` list paginated; `PUT/PATCH` -> 200; `DELETE` -> 204. |
| Errors | `exception/*` + `GlobalExceptionHandler` | `ProblemDetail` with `title`, `detail`, `status`, optional `errors` map for field validation. |
| Tests | see skill `spring-testing` | 400, 401, cross-user 404, happy path. |

# Trip specifics (from V1 schema)
- `trips`: destination, start/end date (`end >= start`), `travellersCount > 0`, `budgetAmount >= 0` (BigDecimal), `travelStyle`, `status` (default `DRAFT`), `preferences` JSONB.
- `trip_itineraries`: one per trip (unique `trip_id`); `version` increments on regeneration; `plan_data` JSONB required.
- `chat_messages`: append-only; `senderRole` enum `USER|ASSISTANT|SYSTEM`; list ordered by `createdAt` (index exists).
- Status values beyond `DRAFT` and travel-style values are not defined yet - ask before inventing an enum.
