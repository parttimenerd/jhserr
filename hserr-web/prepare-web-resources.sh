#!/usr/bin/env bash
# Prepares web resources for the hserr-web build:
#   1. Generates reflect-config.json for GraalVM native-image
#   2. Bundles CodeMirror 6 from npm into web/lib/cm6-bundle.js (cached)
#   3. Downloads external JS/CSS libraries to web/lib/ (cached)
#   4. Copies hs_err grammar files to web/grammars/
# Usage: prepare-web-resources.sh <jhserr-jar-path> <output-dir>
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

# ── 2. Bundle CodeMirror 6 (cached) ──────────────────────────────────────

CM6_DIR="$SCRIPT_DIR/cm6"
CM6_BUNDLE="$SCRIPT_DIR/web/lib/cm6-bundle.js"

if [ ! -f "$CM6_BUNDLE" ]; then
  echo "Building CodeMirror 6 bundle…"
  (cd "$CM6_DIR" && npm install --silent && npx --yes esbuild entry.mjs --bundle --format=esm --outfile="$CM6_BUNDLE" --minify)
  echo "CodeMirror 6 bundle ready"
else
  echo "CodeMirror 6 bundle already exists — skipping (delete web/lib/cm6-bundle.js to rebuild)"
fi

# ── 3. Download external libraries (cached) ──────────────────────────────

HLJS_VERSION="11.9.0"
HLJS_BASE="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/${HLJS_VERSION}"

declare -A FILES=(
  ["highlight.min.js"]="${HLJS_BASE}/highlight.min.js"
  ["github-dark.min.css"]="${HLJS_BASE}/styles/github-dark.min.css"
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

# ── 4. Copy hs_err grammar files to web/grammars/ ───────────────────────

GRAMMAR_DIR="$SCRIPT_DIR/../hserr-grammar"
WEB_GRAMMAR_DIR="$SCRIPT_DIR/web/grammars"

mkdir -p "$WEB_GRAMMAR_DIR"

if [ -d "$GRAMMAR_DIR" ]; then
  # highlight.js grammar (used by the web app + available for download)
  cp "$GRAMMAR_DIR/highlightjs/hserr.js" "$WEB_GRAMMAR_DIR/hserr.js"
  # CodeMirror 6 grammar (TypeScript, for download / CM6 users)
  cp "$GRAMMAR_DIR/codemirror/hserr.ts" "$WEB_GRAMMAR_DIR/hserr-codemirror.ts"
  # TextMate grammar (for VS Code / shiki users)
  cp "$GRAMMAR_DIR/textmate/hserr.tmLanguage.json" "$WEB_GRAMMAR_DIR/hserr.tmLanguage.json"
  # YAML grammar spec (source of truth)
  cp "$GRAMMAR_DIR/hserr.grammar.yaml" "$WEB_GRAMMAR_DIR/hserr.grammar.yaml"
  echo "Grammar files copied to web/grammars/"
else
  echo "Warning: hserr-grammar directory not found at $GRAMMAR_DIR — skipping grammar copy"
fi
