#!/usr/bin/env bash
# Overwrites the committed schema with the live one. Review `git diff` before committing.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
ensure_deps
download_schema "$SCHEMA"
echo "Updated $SCHEMA"
git -C "$ROOT" --no-pager diff --stat -- "$SCHEMA" || true
