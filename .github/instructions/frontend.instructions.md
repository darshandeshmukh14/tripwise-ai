---
applyTo: "frontend/**"
description: Rules for the Next.js frontend / BFF.
---
- Next.js 16 App Router (Turbopack, `cacheComponents`, `proxy.ts` instead of middleware), React 19, TypeScript strict, Tailwind v4, TanStack Query, React Hook Form + Zod v4. Read `frontend/node_modules/next/dist/docs/` before using a Next API (breaking changes vs older versions; see `frontend/AGENTS.md`).
- **BFF security:** the browser never sees the JWT. Login/register route handlers store it in the httpOnly `tw_session` cookie (`src/lib/server/bff.ts`); browser code calls only `/api/auth/*` and `/api/backend/*`. The catch-all proxy forwards an **allowlist** of Spring paths (`trips/**`, `auth/me`), rejects cross-origin writes, copies only safe response headers and clears the cookie on 401. Never widen the allowlist to actuator/docs/auth endpoints; never import `src/lib/server/*` from client components.
- `src/proxy.ts` only does optimistic redirects; Spring enforces auth and ownership.
- **Types:** API types come from `src/lib/api/schema.d.ts` (`npm run gen:api` against Spring springdoc) wrapped in `src/lib/api/types.ts`; the itinerary `plan` JSON types mirror `ai-service/app/schemas.py`. Don't hand-edit `schema.d.ts`.
- **UI:** shadcn-style primitives in `src/components/ui`; costs are always shown as estimates with ranges and confidence (`Estimate` badge). Format INR with `src/lib/format.ts`; dates are calendar dates (format in UTC).
- `cacheComponents`: client hooks reading URL data (`useParams`, `useSearchParams`) need a `<Suspense>` boundary; don't read `new Date()` during prerender.
- Server env (never `NEXT_PUBLIC_`): `BACKEND_URL`, `BACKEND_TIMEOUT_MS`, `TRUST_PROXY_HEADERS`. No secrets in the frontend.
- Tests: Vitest + Testing Library (`src/**/*.test.ts(x)`; route handlers with `// @vitest-environment node` and a stubbed `fetch`), Playwright smoke in `e2e/`. Run `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`. On Windows use `npm.cmd`/`npx.cmd` if script execution is blocked.
