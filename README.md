# Windrunner

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/shzlwio/windrunner?logo=docker&label=docker%20pulls)](https://hub.docker.com/r/shzlwio/windrunner)

> Work with your team and AI. Keep every project moving.

Windrunner is a self-hosted project workspace for coordinating work and
tracking progress. Keep work items, entries, decisions, blockers,
relationships, and evidence together, then ask AI questions with the right
project context.

## Features

- Shared project workspaces with structured work items, entries, and typed relationships.
- Project-context AI for finding blockers, understanding progress, and proposing updates for review.
- Manual workflows for assigning, searching, filtering, following, and reviewing work.
- Teams, access controls, notifications, subscriptions, and audit logs.
- REST API, OpenAPI, CLI, and MCP integrations for tools and agents.
- Configurable OpenAI, OpenRouter, Gemini, and Claude providers.
- Docker Compose and PostgreSQL deployment.
- English and Simplified Chinese web interfaces.

## Quick start

### Docker Compose

Requires Docker with Compose v2.

```bash
git clone https://github.com/shzlw/windrunner.git
cd windrunner/server
```

Create a `.env` file next to `docker-compose.yml` and set secure passwords:

```dotenv
POSTGRES_PASSWORD=windrunner
WINDRUNNER_BOOTSTRAP_SUPERADMIN_PASSWORD=changeme
```

Start Windrunner:

```bash
docker compose up -d
```

Open [http://localhost:8066](http://localhost:8066) and sign in with the
bootstrap administrator from `.env`. Data is stored in the `pgdata` Docker
volume. AI is disabled by default in the Compose setup.

## Documentation

See the full installation, configuration, CLI, API, MCP, and development
guides at [https://shzlw.github.io/windrunner/](https://shzlw.github.io/windrunner/).

## License

Windrunner is released under the MIT License. See [LICENSE](LICENSE).
