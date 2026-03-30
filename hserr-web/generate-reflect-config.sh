#!/usr/bin/env bash
# Generates reflect-config.json for GraalVM native-image from the jhserr jar,
# and downloads external JS/CSS libraries to web/lib/ (cached).
# Usage: generate-reflect-config.sh <jhserr-jar-path> <output-dir>
set -euo pipefail

JAR="$1"
OUT_DIR="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$SCRIPT_DIR/web/lib"

# ── 1. reflect-config.json ──────────────────────────────────────────────

mkdir -p "$OUT_DIR"

echo '[' > "$OUT_DIR/reflect-config.json"

jar tf "$JAR" \
  | grep '\.class$' \
  | grep '^me/bechberger/jhserr/' \
  | grep -v 'cli/' \
  | grep -v 'package-info' \
  | sed 's|/|.|g; s|\.class$||' \
  | sort \
  | while IFS= read -r cls; do
      echo "  {"
      echo "    \"name\": \"$cls\","
      echo "    \"allDeclaredConstructors\": true,"
      echo "    \"allDeclaredMethods\": true,"
      echo "    \"allDeclaredFields\": true,"
      echo "    \"allPublicConstructors\": true,"
      echo "    \"allPublicMethods\": true,"
      echo "    \"allPublicFields\": true"
      echo "  },"
    done \
  | sed '$ s/,$//' \
  >> "$OUT_DIR/reflect-config.json"

echo ']' >> "$OUT_DIR/reflect-config.json"

echo "Generated reflect-config.json with $(grep -c '"name"' "$OUT_DIR/reflect-config.json") entries"

# ── 2. Download external libraries (cached) ─────────────────────────────

HLJS_VERSION="11.9.0"
HLJS_BASE="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/${HLJS_VERSION}"
CM_VERSION="5.65.18"
CM_BASE="https://cdnjs.cloudflare.com/ajax/libs/codemirror/${CM_VERSION}"

declare -A FILES=(
  ["highlight.min.js"]="${HLJS_BASE}/highlight.min.js"
  ["github-dark.min.css"]="${HLJS_BASE}/styles/github-dark.min.css"
  ["codemirror.min.js"]="${CM_BASE}/codemirror.min.js"
  ["codemirror.min.css"]="${CM_BASE}/codemirror.min.css"
  ["cm-javascript.min.js"]="${CM_BASE}/mode/javascript/javascript.min.js"
)

mkdir -p "$LIB_DIR"

for name in "${!FILES[@]}"; do
  dest="$LIB_DIR/$name"
  if [ -f "$dest" ]; then
    continue
  fi
  echo "Downloading $name …"
  curl -fsSL -o "$dest" "${FILES[$name]}"
done

echo "Libraries ready in web/lib/"
