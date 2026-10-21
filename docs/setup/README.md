# Setup

Two ways to get Operion running locally: Docker Compose (fastest, no local Java/Node/MySQL
needed) or running each piece natively (better for active development — hot reload on
both backend and frontend).

## Option A — Docker Compose

Requires Docker only.

```bash
docker compose up --build
```

This starts three services, wired together (see `docker-compose.yml` at the repo root):

| Service | URL | Notes |
|---|---|---|
| `mysql` | `localhost:3306` | database `operion`, root password `operion` (override with `DB_ROOT_PASSWORD`) |
| `backend` | `http://localhost:8080` | waits for MySQL's healthcheck before starting; Flyway migrations run automatically |
| `frontend` | `http://localhost:5173` | nginx serving the production build, proxies `/api` to `backend:8080` |

Override secrets for anything beyond local testing via env vars before `up`:
`DB_ROOT_PASSWORD`, `JWT_SECRET`, `PLATFORM_JWT_SECRET`, `SPRING_PROFILES_ACTIVE`
(see `docker-compose.yml`).

To stop and remove containers (data persists in the `operion-mysql-data` volume):

```bash
docker compose down
```

To also wipe the database volume:

```bash
docker compose down -v
```

## Option B — Native (backend + frontend run directly on your machine)

### Prerequisites

- Java 17 (JDK)
- MySQL 8+, running locally
- Node.js 18+ and npm

### 1. Database

```bash
mysql -uroot -e "CREATE DATABASE operion_platform; CREATE DATABASE operion_school;"
```

Operion uses two databases (never one per school): **`operion_platform`** (organisations, plans,
billing, feature flags, platform admins, CRM, support) and **`operion_school`** (all school data for
every school, isolated by `organisation_id`). Override with `PLATFORM_DB_URL` / `DB_URL`.

Schema is Flyway-managed — `src/main/resources/db/migration/platform/` and `.../school/` run
automatically on backend startup, one Flyway per database. Nothing to run by hand.

Upgrading an existing single-database install: create both databases, start the app once to build
the schemas, stop it, then run `scripts/split-single-database.sh <old_db> operion_school operion_platform`.

### 2. Backend

Configuration lives in `src/main/resources/application.properties`, overridable via
environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`SPRING_PROFILES_ACTIVE`, etc — see that file for the full list).

```bash
./gradlew bootRun
```

The API comes up on `http://localhost:8080`. Health check: `curl http://localhost:8080/actuator/health`.

### 3. Frontend

```bash
cd web
npm install
npm run dev
```

Opens on `http://localhost:5173`, talks directly to the backend at `http://localhost:8080`
(see the proxy config in `web/vite.config.ts`).

### First login

Every organisation is a tenant, and its first admin account is created together with
it — there's no separate signup flow.

```bash
curl -X POST http://localhost:8080/api/v1/organisations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo School",
    "legalName": "Demo School Trust",
    "slug": "demo-school",
    "adminEmail": "admin@demo-school.test",
    "adminPassword": "changeme123",
    "adminFirstName": "Admin",
    "adminLastName": "User"
  }'
```

Then log in with that organisation slug/email/password, either via `POST
/api/v1/auth/login` or through the frontend's login page.

See also: [docs/development](../development) for day-to-day workflow, [docs/testing](../testing)
for running the test suites, [docs/architecture](../architecture) for how the system fits together.

## Production secrets

Every secret below ships with a development value that is published in this repository. With
`SPRING_PROFILES_ACTIVE=prod` the backend **refuses to start** while any of them is still a built-in
default, empty, or too short, and the error lists the variables to set (never their values). Outside
production the same findings are logged as warnings, and the platform console shows them under
**System health > Secrets**.

| Variable | Requirement |
|---|---|
| `JWT_SECRET` | at least 32 bytes, random |
| `PLATFORM_JWT_SECRET` | at least 32 bytes, random, different from `JWT_SECRET` |
| `INTEGRATION_SECRET_KEY` | at least 32 bytes, random (encrypts stored integration credentials - keep it stable) |
| `DB_PASSWORD` / `PLATFORM_DB_PASSWORD` | not empty or a well-known default (`root`, `mysql`, `operion`, ...) |
| `REPORTING_DB_USERNAME` | `reporting_ro`, never `root` - saved-report SQL runs as this user |
| `REPORTING_DB_PASSWORD` | not `mysql` or `changeme-reporting-ro`; rotate the user first: `ALTER USER 'reporting_ro'@'%' IDENTIFIED BY '...'` |
| `PLATFORM_ADMIN_PASSWORD` | needed while a platform admin still has the seeded password `ChangeMe123!`; at least 12 characters. At startup it replaces the seeded password only - a password that was already changed is never touched. |

Generate a key with `openssl rand -base64 48`. `SECRETS_GUARD=enforce` turns the check on outside the
`prod` profile (e.g. a staging server); `SECRETS_GUARD=warn` turns it off in production - avoid that.
A warning (not a failure) is also logged while scheduled backups are off (`BACKUP_ENABLED`).

## Backup and recovery

See [backup-and-recovery.md](backup-and-recovery.md) for the production backup policy, restore steps, and the recovery-drill schedule.
