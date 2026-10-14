#!/usr/bin/env bash
# Compares the committed schema with the live endpoint.
#   1. graphql-inspector diff  -> breaking / dangerous / safe changes; fails on breaking
#   2. newly deprecated fields -> and whether the app's operations use them
#   3. app operations validated against the LIVE schema (what users would actually hit)
# Exit code is non-zero if (1) has breaking changes or (3) finds invalid operations.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
ensure_deps
cd "$TOOLS"

live="$(mktemp -d)/live.graphql"
# LIVE_SCHEMA_FILE lets you test the drift logic against a hand-edited schema without touching the network.
if [ -n "${LIVE_SCHEMA_FILE:-}" ]; then cp "$LIVE_SCHEMA_FILE" "$live"; else download_schema "$live"; fi ||  { echo "::error::Could not download the live schema"; exit 2; }
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
status=0

echo "## Schema diff (committed -> live)" | tee -a "$summary"
if diff -q "$SCHEMA" "$live" >/dev/null; then
  echo "No changes." | tee -a "$summary"
else
  { echo '```'; npx --no-install graphql-inspector diff "$SCHEMA" "$live" 2>&1 | grep -v 'npm notice'; echo '```'; } | tee -a "$summary" || true
  # The summary above is for humans; this run sets the exit code (inspector exits 1 on breaking changes).
  npx --no-install graphql-inspector diff "$SCHEMA" "$live" >/dev/null 2>&1 || { echo "::error::Breaking schema changes detected"; status=1; }
fi

echo | tee -a "$summary"
echo "## Deprecations" | tee -a "$summary"
node deprecations.mjs --schema "$live" --ops "$OPS_DIR" --old "$SCHEMA" | tee -a "$summary"

echo | tee -a "$summary"
echo "## App operations vs live schema" | tee -a "$summary"
ignore=()
if [ -f known-invalid.txt ]; then
  while IFS= read -r name; do
    name="${name%%#*}"; name="${name// /}"
    [ -n "$name" ] && ignore+=(--ignore "$OPS_DIR/$name.graphql")
  done < known-invalid.txt
fi
npx --no-install graphql-inspector validate "$OPS_DIR/*.graphql" "$live" "${ignore[@]}" --relativePaths > "$live.validate" 2>&1
rc=$?
{ echo '```'; grep -v 'npm notice' "$live.validate"; echo '```'; } | tee -a "$summary"
[ "$rc" -eq 0 ] || { echo "::error::Operations are invalid against the live schema"; status=1; }

exit $status
