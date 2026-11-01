---
name: minimal-diff-engineering
description: Implement a change in TripWise AI with the smallest correct diff. Use when editing Java, application.yml, Flyway or pom.xml files, and when cleaning a diff before review.
---
# Minimal diff
- Reuse existing classes/utilities; extend before creating. New class only when the target package layout calls for it.
- No reformatting, import reshuffles, renames or unrelated cleanups; keep existing Lombok/record style.
- No new dependency, `pom.xml`, or `application.yml` change without approval (exception: a new property the approved plan requires).
- Schema changes only via a new Flyway version - never edit existing `V*` files.
- Fix the root cause, plus only tightly-coupled bugs.
- Before finishing: `mvn -q compile`, `git diff` - remove stray edits, debug logs, commented code; ensure tests cover the changed branch.
