---
name: flyway-migrations
description: Write or review Flyway SQL migrations for TripWise AI on PostgreSQL 16 and keep JPA entities in sync with ddl-auto=validate. Use for any table, column, index, constraint or JSONB schema change.
---
# Rules
- **H2 mirror (temporary):** each PostgreSQL migration needs a same-named twin in `src/main/resources/db/h2/` used by profile `h2` (see `flyway.instructions.md` for the type translation). Verify with `mvn spring-boot:run "-Dspring-boot.run.profiles=h2"` - Flyway must apply and Hibernate `validate` must pass.
- Location `src/main/resources/db/migration`; name `V{n}__snake_case.sql` (next integer after the highest existing). Never edit an applied script; fix forward with a new version.
- Existing (V1): function `set_updated_at()`; tables `users`, `trips`, `trip_itineraries`, `chat_messages`; indexes `idx_trips_user_id`, `idx_chat_messages_trip_created_at`; FKs cascade on delete.
- Conventions: `BIGSERIAL` PK, `BIGINT` FK, `TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `NUMERIC(12,2)` money, `JSONB` (default `'{}'::jsonb` when optional), explicit constraint names (`fk_`, `uk_`, `ck_`, `idx_`).
- New table with `updated_at` -> add `trg_<table>_updated_at` trigger.
- Every FK and ownership/filter column indexed. GIN index on JSONB only when queried with `@>`/`?` operators.
- Safe changes: add nullable column -> backfill -> `SET NOT NULL`; `CREATE INDEX CONCURRENTLY` cannot run inside a transaction - put it alone in its own migration with per-script `executeInTransaction=false` (verify support in the Boot-managed Flyway version) or accept a plain `CREATE INDEX` lock on small tables; flag it either way.
- Destructive (drop/rename/type narrowing) -> ask first; prefer expand/contract over two releases.

# Entity sync
After the SQL, update the entity: column name, nullability (`nullable = false`), length, precision/scale (`precision = 12, scale = 2`), JSONB (`@JdbcTypeCode(SqlTypes.JSON)`), `TIMESTAMPTZ` -> `Instant`/`OffsetDateTime`. Verify by starting the app against Docker Postgres (`docker compose up -d`; `mvn spring-boot:run`) or a Testcontainers test; report if not possible.
