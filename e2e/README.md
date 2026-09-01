# Windrunner e2e tests

## Setup

```bash
npm install
cp .env.example .env.local   # then fill in credentials
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

The complete external API suite uses every public API scope. Session-login
mode mints a key with those scopes automatically. For pre-existing key mode,
create the key with all external scopes and set `E2E_USER_ID`; set
`E2E_SECOND_USER_ID` for membership mutation coverage. Team write and audit-log
tests require an admin or superadmin owner (`E2E_GLOBAL_ROLE=ADMIN` when using
a pre-existing key).

## Run the tests

The server must be running (`../server/start-local.sh` from the repository's
`e2e/` folder works well):

```bash
npx playwright test --project=api
```

Run a single spec:

```bash
npx playwright test --project=api -g "projects"
```

## CLI E2E tests

The CLI tests build the current CLI and execute it against the same running
server. They use the same `E2E_API_KEY` or session-login credentials described
above.

```bash
npm run test:cli
```

This command must be run from `e2e`. Set `WINDRUNNER_BASE_URL` in
`.env.local` when the server is not at `http://localhost:8066`.

The suite exercises every CLI command. With session-login mode, the test
automatically selects another active user for membership coverage. With a
pre-existing API key, set `E2E_SECOND_USER_ID` and use an admin-like key owner.

To run the CLI suite without rebuilding it:

```bash
npx playwright test --project=cli
```

The server is not started by Playwright; start it first with
`../server/start-local.sh` from the repository root's `e2e/` folder.

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
the four scenarios defined in `tests/support/seed-scenarios.ts`. A scenario may require
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

- Put API specs in `tests/api`, CLI specs in `tests/cli`, and shared fixtures in
  `tests/support`.
- The `authenticated` fixture provides `{ api, apiKey, userId }` — use it for
  anything that needs authorization.
- Tests create real data on the target server; failed tests leave their data
  behind on purpose so failures can be inspected.
