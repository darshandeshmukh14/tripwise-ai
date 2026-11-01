---
name: repo-cartography
description: Locate code fast in TripWise AI - where an endpoint, security rule, service, repository query, entity, migration, config key or AI-service call lives; trace a flow end-to-end.
---
# Map (root `src/main/java/com/tripwise`)
| Need | Look in |
|---|---|
| App entry | `TripWiseApplication` |
| REST endpoints (`/api/v1/auth`, `/trips`, `/trips/{id}/itinerary`, `/trips/{id}/chat`) | `controller/` (grep `@RequestMapping`, `@GetMapping`, `@PostMapping`) |
| Security chain, public paths, JWT filter, token create/parse | `security/` (`SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`) |
| Business logic, transactions, ownership | `service/` |
| Queries | `repository/` (grep `ByIdAndUserId`, `@Query`) |
| Entities / DTOs / mappers | `entity/`, `dto/`, `mapper/` |
| Error -> ProblemDetail | `exception/GlobalExceptionHandler` |
| FastAPI calls | `client/AiServiceClient`, `config/AiServiceClientConfig` |
| Typed properties | `config/*Properties` |
| Schema | `src/main/resources/db/migration/V*.sql` |
| Config | `src/main/resources/application.yml`, `docker-compose.yml`, `pom.xml` |

Trace: HTTP -> security filter -> controller -> service (tx + ownership) -> repository / AI client -> mapper -> response. Grep a symbol, then read only that range. The project is early-stage: if a class doesn't exist yet, say so.
