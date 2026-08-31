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

## Scenario and performance seeding

Seeds coherent, real-world programs rather than synthetic `e2e` records. The
default run creates **Enterprise SSO Launch** and **Billing Accuracy and Invoice
Recovery**, with ~2000 work items each. Additional scenarios cover notification
reliability and SOC 2 audit readiness.

The scenario catalog defines professional teams, workstreams, objectives,
domain-specific work, and blocker reasons. The performance seed expands that
backbone deterministically, keeping these fields consistent:

- Work item type, status, priority, and due date
- Team or user assignment and project access
- Blocked status and `BLOCKED_BY` relationships
- Resolution, answer, proposal, evidence, and progress entries
- Dependencies within a workstream and limited cross-workstream links

The logged-in seed operator becomes a project owner and receives a deterministic
share of assignments. This makes My Work, notifications, and subscriptions useful
immediately after seeding. With `kc` as `E2E_LOGIN`, for example, the generated
workspace includes work assigned directly to `kc`.

```bash
SEED_PERF=1 E2E_LOGIN=<you> E2E_PASSWORD=<pass> \
  npx playwright test --project=api -g "Seed"
```

Use an `ADMIN` or `SUPERADMIN` account for the seed login. The default visible
names are stable and contain no test prefix or generated run ID, so reset the
database before reseeding.

Tunables: `SEED_PROJECTS` (2), `SEED_ITEMS` (2000), `SEED_USERS` (50),
`SEED_TEAMS` (20), and `SEED_CONCURRENCY` (4). `SEED_PROJECTS` can select up to
the four scenarios defined in `tests/seed-scenarios.ts`. A scenario may require
more teams than a lower `SEED_TEAMS` value; required teams are always included.

When multiple seed sets must coexist, provide an intentional label such as
`SEED_NAME_SUFFIX="Demo Blue"`. The suffix is added to team and project names
and normalized for usernames and email addresses. It is never generated
automatically.

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
