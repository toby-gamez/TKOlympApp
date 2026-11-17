#!/usr/bin/env bash
# Report-only check: spacing literals that map to a Spacing token, and
# icon-only controls with a null contentDescription. Usage: scripts/check-compose-style.sh [dir]
dir="${1:-composeApp/src}"
echo "== Literal spacing that should use ui.theme.Spacing =="
grep -rnE --include=*.kt '(\b(horizontal|vertical|start|end|top|bottom) = |padding\(|spacedBy\()(4|8|12|16|24|32)\.dp' "$dir"
echo "== IconButton with null contentDescription =="
grep -rnE --include=*.kt 'IconButton.*contentDescription = null' "$dir"
