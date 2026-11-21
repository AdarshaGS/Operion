# Operion — project context for agents

Agents are language-neutral; this file holds everything repo-specific they need.

## Layout
- `src/main/java/com/operion/**` Spring Boot backend (Gradle, Java, JUnit 5, H2 for tests)
- `web/` React + Vite + TypeScript frontend (vitest, oxlint)
- `e2e/` Playwright API/UI tests
- `docs/` product, setup and stabilisation docs; `ai-context/` long-form context (load on request only)
- `.codegraph/` exists: use `codegraph explore "<symbol or question>"` before grep/Read

## Build and test (quiet forms — always pipe through `| tail -40`)
| Purpose | Command |
|---|---|
| Compile backend | `./gradlew compileJava compileTestJava -q` |
| One backend test class | `./gradlew test --tests 'com.operion.<pkg>.<ClassName>' -q` |
| Full backend tests (only if the brief says so) | `./gradlew test -q` |
| Frontend typecheck + build | `cd web && npm run build` |
| Frontend unit tests | `cd web && npm test` |
| Frontend lint | `cd web && npm run lint` |
| E2E (needs backend on :8080) | `cd e2e && npm test` |

Run the backend locally: `DB_URL="jdbc:mysql://localhost:3306/operion" DB_USERNAME=root DB_PASSWORD=mysql ./gradlew bootRun` (MySQL on 3306).

## Conventions
- Backend layering: `api/` request/response records + controller → service → repository (Spring Data JPA). Keep new code in the same package as the feature it belongs to.
- Multi-tenant: every query is scoped by `organisationId`; never add an unscoped repository method.
- DB changes are Flyway migrations in `src/main/resources/db/migration/` (`V<n>__<name>.sql`); never edit an existing migration.
- Tests: one `*Test` class per service, H2 in-memory; no live MySQL in unit tests.

## Current constraints
- Until 2026-11-14: product walkthrough only. Log issues in `docs/stabilisation/`; no product-code fixes.
- Branch `feature/two-database-split` is the working branch; PRs go to `main`.
