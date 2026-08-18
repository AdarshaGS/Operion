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
mysql -uroot -e "CREATE DATABASE operion;"
```

Schema is Flyway-managed — migrations in `src/main/resources/db/migration/` run
automatically on backend startup. Nothing to run by hand.

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
