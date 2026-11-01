---
description: Create the next Flyway migration and align the JPA entity.
agent: Coder
---
Use skill `flyway-migrations`. Find the highest existing `V*` in `src/main/resources/db/migration`, create `V{next}__<snake_case>.sql` for the change below (named constraints, FK indexes, `updated_at` trigger for new tables, safe NOT NULL rollout), and update the matching entity so `ddl-auto: validate` passes. Never edit applied migrations. Flag destructive changes before writing them.

Schema change:
