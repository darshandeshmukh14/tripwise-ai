# TripWise AI - Frontend

Next.js 16 (App Router) UI and BFF for TripWise AI. The browser only talks to this app; route handlers keep the JWT in an
httpOnly cookie and proxy an allowlist of Spring API paths (`/api/backend/trips/**`, `/api/backend/auth/me`).

## Run locally
1. Start Spring (`mvn spring-boot:run "-Dspring-boot.run.profiles=h2"`) and the ai-service (see `../ai-service/README.md`).
2. `cp .env.example .env.local` and adjust `BACKEND_URL` if Spring is not on `http://localhost:8080`.
3. `npm ci` then `npm run dev` and open http://localhost:3000.

## Scripts
| Command | Purpose |
| --- | --- |
| `npm run dev` / `npm run build` / `npm start` | Develop, build, serve (standalone output) |
| `npm run lint` / `npm run typecheck` | ESLint / route typegen + `tsc --noEmit` |
| `npm test` | Vitest unit, component and route-handler tests |
| `npm run test:e2e` | Playwright smoke tests (`npx playwright install chromium` once) |
| `npm run gen:api` | Regenerate `src/lib/api/schema.d.ts` from Spring `/v3/api-docs` (dev profile) |

## Structure
- `src/app/api/auth/*` - login/register/logout (sets/clears the `tw_session` cookie).
- `src/app/api/backend/[...path]` - authenticated proxy to Spring.
- `src/proxy.ts` - redirects for signed-out/signed-in users.
- `src/app/trips/*` - trip list, new-trip wizard, trip detail (status polling, transport comparison, stay, days, cost breakdown, chat).
- `src/lib/api` - typed client; `src/lib/format.ts`, `src/lib/validation.ts` - formatting and Zod schemas.

## Environment (server-side only)
`BACKEND_URL` (default `http://localhost:8080`), `BACKEND_TIMEOUT_MS` (default 75000), `TRUST_PROXY_HEADERS`
(`true` only behind a trusted reverse proxy, to forward `X-Forwarded-For` for Spring's rate limiter).

All prices shown are indicative estimates, not live quotes.
