#!/usr/bin/env bash
set -euo pipefail

SERVER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SERVER_ROOT/.." && pwd)"
WEBUI_DIR="$SERVER_ROOT/web"
WEB_DIST_DIR="$WEBUI_DIR/dist"
SERVER_DIR="$SERVER_ROOT/api"
SERVER_APP_DIR="$SERVER_DIR/windrunner-app"
STATIC_DIR="$SERVER_APP_DIR/src/main/resources/static"
VERSION_FILE="$SERVER_ROOT/VERSION"
SKIP_NPM_CI="${SKIP_NPM_CI:-0}"

if (( $# > 0 )); then
  echo "build.sh does not accept a version argument; update $VERSION_FILE instead." >&2
  exit 1
fi

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Version file not found: $VERSION_FILE" >&2
  exit 1
fi

APP_VERSION="$(<"$VERSION_FILE")"
if [[ -z "$APP_VERSION" || "$APP_VERSION" =~ [[:space:]] ]]; then
  echo "VERSION must contain one non-empty version without whitespace." >&2
  exit 1
fi

if [[ ! -d "$WEBUI_DIR" ]]; then
  echo "Frontend project not found: $WEBUI_DIR" >&2
  exit 1
fi

if [[ ! -d "$SERVER_APP_DIR" ]]; then
  echo "Server project not found: $SERVER_APP_DIR" >&2
  exit 1
fi

command -v npm >/dev/null || { echo "npm is required but was not found." >&2; exit 1; }
command -v mvn >/dev/null || { echo "mvn is required but was not found." >&2; exit 1; }

echo "Building Windrunner $APP_VERSION..."

cd "$WEBUI_DIR"
if [[ "$SKIP_NPM_CI" == "1" ]]; then
  echo "Skipping web dependency install..."
else
  echo "Installing web dependencies..."
  npm ci --no-audit --no-fund --progress=false
fi

echo "Building web..."
npm run build

if [[ ! -d "$WEB_DIST_DIR" ]]; then
  echo "Web build output not found: $WEB_DIST_DIR" >&2
  exit 1
fi

echo "Copying web build into server resources..."
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -R "$WEB_DIST_DIR/." "$STATIC_DIR/"

echo "Building server jar..."
cd "$SERVER_DIR"
BACKEND_BUILD_START_SECONDS="$SECONDS"
mvn --batch-mode --no-transfer-progress -q \
  -Drevision="$APP_VERSION" \
  -pl windrunner-app -am clean package
BACKEND_BUILD_DURATION_SECONDS="$((SECONDS - BACKEND_BUILD_START_SECONDS))"
printf 'Backend build finished in %02d:%02d\n' "$((BACKEND_BUILD_DURATION_SECONDS / 60))" "$((BACKEND_BUILD_DURATION_SECONDS % 60))"
