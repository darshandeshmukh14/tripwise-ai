---
name: security-jwt-ownership
description: Implement or review TripWise authentication (register/login, BCrypt, jjwt 0.12 tokens, stateless Spring Security filter chain) and per-user ownership of trips, itineraries and chat. Use for auth, SecurityConfig, JWT, current-user or access-control changes.
---
# Authentication
- `JwtProperties(secret, expiration, refreshExpiration, refreshReuseGrace)` from `jwt.*`; access token 15 min; refresh tokens are opaque, stored as SHA-256 in `refresh_tokens`, rotated on every `/auth/refresh` (reuse outside the grace window revokes the family), revoked by `/auth/logout`; secret is Base64 (>= 256-bit) -> `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))`.
- `JwtService` (jjwt 0.12 API): `Jwts.builder().subject(userId.toString()).claim("email", email).issuedAt(now).expiration(now + expiration).signWith(key).compact()`; parse with `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`. Catch `JwtException` -> unauthenticated (never 500).
- `JwtAuthenticationFilter extends OncePerRequestFilter`: read `Authorization: Bearer`, validate, set `UsernamePasswordAuthenticationToken` with an `AuthenticatedUser(Long id, String email)` principal. Skip when header absent.
- `SecurityConfig`: `SecurityFilterChain` with `csrf.disable()`, `sessionManagement(STATELESS)`, `permitAll` for `/api/v1/auth/**` (and `/actuator/health` if added), `anyRequest().authenticated()`; add filter before `UsernamePasswordAuthenticationFilter`; `AuthenticationEntryPoint` + `AccessDeniedHandler` returning ProblemDetail JSON (401/403); `PasswordEncoder` = `BCryptPasswordEncoder`; disable default form login/httpBasic.
- Register: normalise email (trim + lowercase), reject duplicates with 409, hash password, never return `password_hash`. Login: same 401 message for unknown email and wrong password (no user enumeration).

# Ownership
- Controllers obtain the user via `@AuthenticationPrincipal AuthenticatedUser user` (or a `CurrentUser` helper) and pass `user.id()` to services.
- Never trust `userId` from path/body/query.
- Repository: `findByIdAndUserId`, `findAllByUserId`, child access via `trip.user.id` (e.g. `findByTripIdAndTripUserId`). Missing or foreign -> `ResourceNotFoundException` -> 404.
- Writes to itinerary/chat first verify trip ownership in the same service call.

# Tests
Expired/tampered token -> 401; missing token -> 401; user B on user A's trip -> 404; register duplicate email -> 409; password never in responses/logs.
