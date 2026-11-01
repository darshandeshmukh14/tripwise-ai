---
description: Security review of auth, JWT handling, ownership checks and secret usage.
agent: CodeReviewer
---
Use skill `security-jwt-ownership`. Audit the code (or current diff) for: unauthenticated endpoints that should be protected, JWT validation gaps (signature, expiry, algorithm), password hashing, user-enumeration leaks, IDOR (trip/itinerary/chat accessible across users), secrets in code/logs, `X-Internal-Token` handling, and error responses leaking internals. Report file:line, severity, and the minimal fix. Read-only.
