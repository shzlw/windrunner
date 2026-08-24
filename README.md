# Windrunner

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/shzlwio/windrunner?logo=docker&label=docker%20pulls)](https://hub.docker.com/r/shzlwio/windrunner)

> Manage the work. Ask the project.

Windrunner is a self-hosted project workspace that combines structured work
tracking with project-level AI. Teams can manage work directly or use natural
language to share updates, find blockers, understand progress, and make
decisions with less back-and-forth.

Work items, entries, and relationships keep project context connected so
complex work is easier to follow and discuss.

## What Windrunner provides

- Structured project graphs built from nested work items, meaningful entries,
  and typed relationships such as blockers, dependencies, answers, and
  supporting evidence.
- Project-level AI chat for questions about project context, progress, and
  blockers, with proposed changes available for review.
- AI review for work items and entry drafts. Suggested changes are explicit
  and must be accepted or rejected by a user.
- A complete manual workspace for creating, assigning, searching, filtering,
  following, and reviewing work without AI.
- AI usage and ROI metrics, including token usage, acceptance rates, and
  estimated time saved.
- OpenAI, Gemini, and Claude support with your own credentials, models, and
  limits.
- Self-hosted deployment with Docker Compose and PostgreSQL.
- A versioned REST API with scoped API keys for integrations and automation.

## Quick start with Docker Compose

Prerequisites: Docker with Compose v2.

```bash
mkdir windrunner && cd windrunner

curl -fsSL https://raw.githubusercontent.com/shzlw/windrunner/main/server/docker-compose.yml -o docker-compose.yml
curl -fsSL https://raw.githubusercontent.com/shzlw/windrunner/main/server/.env.example -o .env
```

Change the default database and bootstrap administrator passwords in `.env`,
then start Windrunner:

```bash
docker compose up -d
```

Open `http://localhost:8066` and sign in with the bootstrap administrator from
`.env`. AI is optional; set `WINDRUNNER_LLM_PROVIDER` and the matching provider
API key in `.env` when you want to enable it.

## Build from source

Prerequisites: Java 25, Maven 3.9+, Node.js 25+, npm, and PostgreSQL with the
`pg_trgm` extension.

```bash
./server/build.sh
./server/start-local.sh
```

See the [installation guide](docs/docs/getting-started/installation.mdx) for
configuration details and the [documentation source](docs/) for the rest of
the product guides.

## License

Windrunner is released under the MIT License. See [LICENSE](LICENSE) for the
full license text.
