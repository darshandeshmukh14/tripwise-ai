# Commit message instructions

Use Conventional Commits: `<type>(<scope>): <summary>`

- **type:** `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `chore`, `db` (Flyway migration), `security`.
- **scope:** feature area - `auth`, `user`, `trip`, `itinerary`, `chat`, `ai-client`, `config`, `migration`.
- **summary:** imperative, lower-case, no trailing period, max 72 chars.
- Body (optional): what and why, wrapped at 72 chars. Mention new Flyway versions (e.g. `Adds V2__add_trip_title.sql`) and any new env variables.
- Breaking API or schema changes: add a `BREAKING CHANGE:` footer.

Examples:
- `feat(trip): add create and list trip endpoints scoped to current user`
- `db(migration): add V2 index on trips(status)`
- `fix(auth): reject expired JWT with 401 problem detail`
