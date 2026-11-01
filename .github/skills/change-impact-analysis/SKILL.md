---
name: change-impact-analysis
description: Find everything affected by changing a method, DTO/entity field, DB column, enum, config key or endpoint in TripWise AI. Use before planning/reviewing non-trivial changes or when asked "what will this break".
---
# Impact analysis
1. Grep usages under `src/main/java`, `src/test/java`, `src/main/resources` (symbol, field, column name in `db/migration/*.sql`, JSON property name, config key in `application.yml`, URL path).
2. Classify:
   - **Callers:** controller / service / repository / mapper / client.
   - **API contract:** request/response DTO fields, status codes, paths - breaking for the frontend?
   - **Schema:** column/table change -> new Flyway script + entity update (`ddl-auto: validate` breaks startup if they diverge). Renames/drops are destructive.
   - **JSONB shape:** `preferences` / `plan_data` structure changes affect existing rows and the FastAPI contract.
   - **Security/ownership:** does the change alter which user can see/modify data?
   - **Config/env:** `jwt.*`, `ai-service.*`, datasource env vars.
   - **Tests** to update.
3. Output: usages (file:line), contract impact, schema impact, tests, risk (low/med/high), recommended edit order.
