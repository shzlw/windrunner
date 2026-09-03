#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Local database. Override these before running if your Postgres differs.
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/windrunner}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${USER:-postgres}}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-test}"

# Local direct HTTP testing. Override to true if you are testing through HTTPS.
export WINDRUNNER_AUTH_COOKIE_SECURE="${WINDRUNNER_AUTH_COOKIE_SECURE:-false}"

# First-login bootstrap superadmin. Only used while no users exist.
export WINDRUNNER_BOOTSTRAP_SUPERADMIN_USERNAME="admin"
export WINDRUNNER_BOOTSTRAP_SUPERADMIN_EMAIL="admin@localhost"
export WINDRUNNER_BOOTSTRAP_SUPERADMIN_PASSWORD="changeme"

# Enable LLM provider
export WINDRUNNER_LLM_PROVIDER="${WINDRUNNER_LLM_PROVIDER:-openai}"
export WINDRUNNER_AUDIO_TRANSCRIPTION_ENABLED="${WINDRUNNER_AUDIO_TRANSCRIPTION_ENABLED:-true}"

# export WINDRUNNER_LLM_PROVIDER="${WINDRUNNER_LLM_PROVIDER:-claude}"
# export WINDRUNNER_LLM_PROVIDER="${WINDRUNNER_LLM_PROVIDER:-gemini}"


echo "Starting Windrunner locally..."
exec "$ROOT_DIR/start.sh"
