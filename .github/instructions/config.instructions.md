---
applyTo: "**/{application*.yml,application*.yaml,pom.xml,docker-compose*.yml}"
description: Rules for configuration, build and infrastructure files.
---
- `spring.jpa.hibernate.ddl-auto` stays `validate`; `spring.flyway.clean-disabled` stays `true`.
- Every secret/environment-specific value uses `${ENV_VAR:dev-default}`; never commit real production secrets. Document any new env var in the change summary.
- Custom properties go under existing roots (`jwt.*`, `ai-service.*`) or a new documented root, backed by a `@ConfigurationProperties` record.
- `pom.xml`: Boot parent manages versions - don't pin managed artifacts. New dependencies need approval and a one-line justification.
- `docker-compose.yml`: keep PostgreSQL on 16; keep DB name/user/password aligned with `application.yml` defaults.
- `application-h2.yml` is a temporary local fallback: keep `ddl-auto: validate` and `flyway.locations: classpath:db/h2`; never activate it in shared/prod environments.
