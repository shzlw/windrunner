# Windrunner

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/shzlwio/windrunner?logo=docker&label=docker%20pulls)](https://hub.docker.com/r/shzlwio/windrunner)

> Talk to your work.

Windrunner is a self-hosted, AI-native Work Hub for teams that shouldn't need to learn a tool to manage work.

Work enters however you work — chat, API, MCP — and becomes a structured Work Graph: teams, projects, work items, decisions and their relationships, linked so humans and agents share one truth. Every operation is AI-native: you describe intent, AI proposes the structured change, you review.

Chat by default, workspace when you need control — inspect, troubleshoot and manually alter any detail when you need to, not because you have to.

## Features

- **Work Graph, not tickets** — typed work items and relationships keep blockers, dependencies and decisions consistent.
- **AI-native operations** — all reads and writes go through AI with auditable proposals, not bolted-on chat.
- **Chat as the interface** — create, assign, link and query work in natural language.
- **Workspace when you need it** — traditional tree + inspector for triage and troubleshooting, secondary by design.
- **Open and agentic** — REST API, OpenAPI, CLI and MCP for any tool or agent to feed or query work.
- **Private and extensible** — self-hosted on Docker + PostgreSQL, with access controls, audit logs, and i18n customization.
- **Model-agnostic** — works with OpenAI, OpenRouter, Ollama, Groq, Gemini, Claude and more.

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
