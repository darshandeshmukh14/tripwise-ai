---
description: Small, clear change in one pass (1-3 files) - lowest credit use.
agent: QuickFix
---
Make this small change in one pass: locate via grep, read only the needed range, minimal edit in surrounding style, add/update a test if logic is pure, then `mvn -q compile` + `git diff` self-check.
If it needs >3 files, a dependency, a Flyway migration, a security-rule change or a business decision, stop and say so.

Reply in <=6 lines: file:line changed, why, test status, risk.

Change:
