# Testing

## Backend (JUnit)

```bash
./gradlew test
```

Runs the full suite under `src/test/java/com/operion/`. Most service/repository tests
are `@DataJpaTest` slices against an in-memory H2 database (fast, no MySQL needed) —
see e.g. `PortalInviteLifecycleTest` for the pattern: `@DataJpaTest` slices don't get a
`PasswordEncoder` or JWT-secret bean autowired, so services that need them are
hand-constructed inside the test rather than `@Import`-ed.

To run a single test class or a targeted package (much faster while iterating):

```bash
./gradlew test --tests "com.operion.parent.*"
./gradlew test --tests "com.operion.identity.auth.RefreshTokenServiceTest"
```

`./gradlew build` (used in CI, see `.github/workflows/ci.yml`) runs the full test suite
as part of the build.

## Frontend (Vitest + Testing Library)

```bash
cd web
npm test           # single run (CI mode)
npm run test:watch # watch mode while developing
```

Configuration lives in `web/vite.config.ts` (`test` block) and `web/src/test/setup.ts`.
Environment is `jsdom`. Note: `web/src/test/setup.ts` installs an in-memory
`localStorage` polyfill — neither jsdom nor Node's own experimental `localStorage`
implementation work reliably together in this setup, so tests get a plain `Map`-backed
`Storage` instead.

Conventions:
- Pure modules (e.g. `web/src/api/tokenStore.ts`) get plain unit tests —
  `tokenStore.test.ts` is the reference example.
- Components that read from a context (e.g. `useAuth`) get their dependency mocked with
  `vi.spyOn` rather than wrapped in a real provider, when the real provider would pull
  in a network call — `web/src/auth/Can.test.tsx` is the reference example.

Type checking and lint (also run in CI):

```bash
npx tsc -b --noEmit
npm run lint
```

## CI

`.github/workflows/ci.yml` runs both suites (plus lint, type check, and a Docker image
build for each side) on every push and pull request.
