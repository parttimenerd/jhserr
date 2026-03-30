#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PORT="${PORT:-8080}"

# ── Find latest sdkman GraalVM ───────────────────────────────────────
SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
JAVA_CANDIDATES="$SDKMAN_DIR/candidates/java"

if [[ ! -d "$JAVA_CANDIDATES" ]]; then
  echo "ERROR: sdkman java candidates not found at $JAVA_CANDIDATES" >&2
  exit 1
fi

GRAAL_HOME=$(ls -d "$JAVA_CANDIDATES"/*graal* 2>/dev/null | sort -V | tail -1)
if [[ -z "$GRAAL_HOME" ]]; then
  echo "ERROR: No GraalVM installation found under $JAVA_CANDIDATES" >&2
  echo "       Install one with: sdk install java 25.0.2-graal" >&2
  exit 1
fi

export JAVA_HOME="$GRAAL_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using GraalVM: $JAVA_HOME"
java -version 2>&1 | head -1

# ── Check binaryen (wasm-opt) ────────────────────────────────────────
if ! command -v wasm-opt &>/dev/null; then
  echo "ERROR: binaryen (wasm-opt) not found on PATH" >&2
  echo "       Install with: brew install binaryen" >&2
  exit 1
fi

# ── Build main parser library ────────────────────────────────────────
echo ""
echo "=== Building hserr-parser ==="
mvn -B install -DskipTests 2>&1 | tee mvn.out
echo "Parser build OK"

# ── Build web module (WASM) ──────────────────────────────────────────
echo ""
echo "=== Building hserr-web (GraalVM Web Image) ==="
cd hserr-web
mvn -B package 2>&1 | tee mvn.out

if [[ ! -f web/hserr.js ]] || [[ ! -f web/hserr.js.wasm ]]; then
  echo "ERROR: Expected web/hserr.js and web/hserr.js.wasm not found" >&2
  exit 1
fi
echo "Web build OK — output in hserr-web/web/"

# ── Launch dev server ────────────────────────────────────────────────
echo ""
echo "=== Starting dev server at http://localhost:$PORT ==="
cd web
python3 -m http.server "$PORT"
