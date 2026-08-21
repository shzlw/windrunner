#!/usr/bin/env bash
set -euo pipefail

SERVER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SERVER_ROOT/.." && pwd)"
VERSION_FILE="$SERVER_ROOT/VERSION"
REQUIRED_JAVA_VERSION=25

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Version file not found: $VERSION_FILE" >&2
  exit 1
fi

APP_VERSION="$(<"$VERSION_FILE")"
if [[ -z "$APP_VERSION" || "$APP_VERSION" =~ [[:space:]] ]]; then
  echo "VERSION must contain one non-empty version without whitespace." >&2
  exit 1
fi

JAR_PATH="${JAR_PATH:-$SERVER_ROOT/api/windrunner-app/target/windrunner-app-$APP_VERSION.jar}"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
elif [[ -x "/opt/homebrew/opt/openjdk/bin/java" ]]; then
  JAVA_BIN="/opt/homebrew/opt/openjdk/bin/java"
else
  JAVA_BIN="java"
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH" >&2
  echo "Run ./server/build.sh first." >&2
  exit 1
fi

JAVA_VERSION_OUTPUT="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
JAVA_VERSION="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$JAVA_VERSION_OUTPUT")"

if [[ ! "$JAVA_VERSION" =~ ^[0-9]+$ || "$JAVA_VERSION" -lt "$REQUIRED_JAVA_VERSION" ]]; then
  echo "Java $REQUIRED_JAVA_VERSION or newer is required to run this jar." >&2
  echo "Selected Java: $JAVA_BIN" >&2
  echo "Version: $JAVA_VERSION_OUTPUT" >&2
  echo "Set JAVA_HOME to a Java $REQUIRED_JAVA_VERSION+ install, or install OpenJDK $REQUIRED_JAVA_VERSION." >&2
  exit 1
fi

exec "$JAVA_BIN" -jar "$JAR_PATH" "$@"
