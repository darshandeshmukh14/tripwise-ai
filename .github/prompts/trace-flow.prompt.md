---
description: Trace a flow end-to-end (endpoint -> security filter -> controller -> service -> repository / AI service).
---
Trace this flow using skill `repo-cartography`: HTTP entry -> JWT filter / security rule -> controller -> service (transaction, ownership check) -> repository query / AI-service call -> response DTO / ProblemDetail. List classes with file:line. Read-only; be concise. If a piece doesn't exist yet, say so.

Flow:
