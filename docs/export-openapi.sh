#!/usr/bin/env bash
#
# Exports the OpenAPI specification from a running Windrunner server and
# writes it to docs/static/windrunner-openapi.json, which powers the
# Scalar API reference on the documentation site.
#
# The springdoc-generated "servers" entry (derived from the request host,
# e.g. http://localhost:8066) is stripped so the published spec is
# host-neutral. Pass a public base URL as the first argument to embed it.
#
# Usage:
#   ./export-openapi.sh [base-url]
#
# Examples:
#   ./export-openapi.sh                          # strip servers entirely
#   ./export-openapi.sh https://windrunner.example.com

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="$SCRIPT_DIR/static/windrunner-openapi.json"
BASE_URL="${1:-}"

SPEC_URL="${BASE_URL:-http://localhost:8066}"
SPEC_URL="${SPEC_URL%/}/api/openapi.json"

echo "Fetching OpenAPI spec from $SPEC_URL ..."

if ! curl -fsSL "$SPEC_URL" -o "$OUTPUT_FILE"; then
  echo "Failed to fetch $SPEC_URL" >&2
  echo "Is the server running? Start it with server/start.sh (or start-local.sh)." >&2
  exit 1
fi

python3 - "$BASE_URL" "$OUTPUT_FILE" <<'PY'
import json, sys

base_url, output_file = sys.argv[1], sys.argv[2]
spec = json.load(open(output_file))
if base_url:
    spec['servers'] = [{'url': base_url.rstrip('/') + '/'}]
else:
    spec.pop('servers', None)
json.dump(spec, open(output_file, 'w'), indent=2)
PY

echo "Wrote $(wc -c < "$OUTPUT_FILE" | tr -d ' ') bytes to $OUTPUT_FILE"
echo "Rebuild the docs site (npm run build) to see the updated API reference."
