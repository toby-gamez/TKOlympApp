# Shared paths/settings for the GraphQL tooling. Sourced by the other scripts.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="$ROOT/tools/graphql"
GQL_DIR="$ROOT/shared/src/commonMain/graphql"
SCHEMA="$GQL_DIR/schema.graphql"
OPS_DIR="$GQL_DIR/operations"
GRAPHQL_URL="${GRAPHQL_URL:-https://tkolymp.cz/graphql}"

ensure_deps() {
  [ -d "$TOOLS/node_modules" ] || (cd "$TOOLS" && npm ci --no-audit --no-fund)
}

# Downloads the live schema (introspection) into $1 as SDL. Uses curl: the endpoint answers 403 to some
# HTTP clients (e.g. Python urllib), and the introspection query needs no credentials.
download_schema() {
  local out="$1" tmp
  tmp="$(mktemp -d)"
  node "$TOOLS/introspection-query.mjs" > "$tmp/query.json"
  curl --fail --silent --show-error --max-time 60 --retry 3 --retry-delay 5 \
    -A "tkolymp-schema-check" -H 'Content-Type: application/json' \
    --data @"$tmp/query.json" "$GRAPHQL_URL" -o "$tmp/introspection.json"
  node "$TOOLS/fetch-schema.mjs" "$tmp/introspection.json" "$out"
  rm -rf "$tmp"
}
