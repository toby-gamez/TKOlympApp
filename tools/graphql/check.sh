#!/usr/bin/env bash
# Validates every operation in shared/src/commonMain/graphql/operations against the committed schema
# and reports deprecated fields the app uses.
#
#   FAIL_ON_DEPRECATED=1 tools/graphql/check.sh   # also fail when a deprecated field is used (default: warn)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
ensure_deps
cd "$TOOLS"

# Operations that are already invalid against the current schema (tracked in known-invalid.txt) are
# skipped so that CI only fails on *new* breakage. They are listed loudly on every run.
ignore=()
if [ -f known-invalid.txt ]; then
  while IFS= read -r name; do
    name="${name%%#*}"; name="${name// /}"
    [ -n "$name" ] || continue
    ignore+=(--ignore "$OPS_DIR/$name.graphql")
    echo "::warning::known-invalid operation skipped: $name (see tools/graphql/known-invalid.txt)"
  done < known-invalid.txt
fi

echo "== Operation file naming =="
for f in "$OPS_DIR"/*.graphql; do
  base="$(basename "$f" .graphql)"
  grep -Eq "^(query|mutation|subscription) $base\b" "$f" || { echo "::error file=$f::first line must declare operation named '$base'"; exit 1; }
done
echo "ok"

# A known-invalid entry must stay genuinely invalid: once the operation validates (or is deleted), the entry is
# stale and would silently exempt it from CI forever.
if [ -f known-invalid.txt ]; then
  while IFS= read -r name; do
    name="${name%%#*}"; name="${name// /}"
    [ -n "$name" ] || continue
    if [ ! -f "$OPS_DIR/$name.graphql" ]; then echo "::error::known-invalid.txt lists '$name' but that operation no longer exists - remove the line"; exit 1; fi
    if npx --no-install graphql-inspector validate "$OPS_DIR/$name.graphql" "$SCHEMA" --relativePaths >/dev/null 2>&1; then
      echo "::error::known-invalid.txt lists '$name' but it validates now - remove the line"; exit 1
    fi
  done < known-invalid.txt
fi

echo "== Validate operations against schema =="
npx --no-install graphql-inspector validate "$OPS_DIR/*.graphql" "$SCHEMA" "${ignore[@]}" --relativePaths

echo "== Deprecated fields used by the app =="
args=(--schema "$SCHEMA" --ops "$OPS_DIR")
[ "${FAIL_ON_DEPRECATED:-0}" = "1" ] && args+=(--fail-on-used)
node deprecations.mjs "${args[@]}" | tee "${GITHUB_STEP_SUMMARY:-/dev/null}"
