# Windrunner e2e tests

## Setup

```bash
npm install
cp .env.local.example .env.local   # then fill in credentials
```

`.env.local` is git-ignored. Two mutually exclusive auth modes:

| Mode | Keys | Behavior |
|---|---|---|
| **API key** | `E2E_API_KEY`, optional `E2E_USER_ID` | Skips login entirely; use a key created under Account → API keys |
| **Session login** | `E2E_LOGIN`, `E2E_PASSWORD` | Logs in per test, mints a scoped key, revokes it afterwards |

Other settings:

| Key | Default |
|---|---|
| `WINDRUNNER_BASE_URL` | `http://localhost:8066` |

## Run the tests

The server must be running (`./start-local.sh` from the repo's `server/`
folder works well):

```bash
npx playwright test --project=api
```

Run a single spec:

```bash
npx playwright test --project=api -g "projects"
```

## Performance seeding

Seeds two projects with ~2000 work items each (hierarchy, entries,
relationships), 50 users, and 20 teams (including SRE, Development, Product,
Sales, and Support) — useful for testing search,
pagination, and UI behavior at realistic volume. Data choices are deterministic
and the run namespace is unique by default; it takes several minutes.

```bash
SEED_PERF=1 E2E_LOGIN=<you> E2E_PASSWORD=<pass> \
  npx playwright test --project=api -g "Seed"
```

Use an `ADMIN` or `SUPERADMIN` account for the seed login.

Tunables: `SEED_PROJECTS` (2), `SEED_ITEMS` (2000), `SEED_USERS` (50),
`SEED_TEAMS` (20, minimum 5), `SEED_CONCURRENCY` (4), and `SEED_RUN_ID` (a unique
timestamp by default). The run id namespaces generated users, teams, and
projects so rerunning after a partial failure does not collide with old data.
The default concurrency is sized for the server's default Hikari pool of 10;
if you raise it, also raise `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` on the
server (allowing for audited writes that may briefly use a second connection).
For Docker Compose, set the pool size before starting the app, for example:
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=32 docker compose up -d`.

## Writing new specs

- Put specs in `tests/*.spec.ts`; shared fixtures live in `tests/helpers.ts`.
- The `authenticated` fixture provides `{ api, apiKey, userId }` — use it for
  anything that needs authorization.
- Tests create real data on the target server; failed tests leave their data
  behind on purpose so failures can be inspected.
