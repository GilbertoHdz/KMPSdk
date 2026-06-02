#!/bin/bash
# Serve the web example with traces sent to a local Jaeger instance via OTel Collector.
#
# Requires: Docker, Python 3
#
# What this script does:
#   1. Frees port 8080 if another server is already running
#   2. Starts OTel Collector + Jaeger (docker compose up -d)
#   3. Waits for the Collector to accept connections on :4318
#   4. Builds the JS SDK
#   5. Serves the demo at http://localhost:8080/examples/web/kmpsdk/kmplocal/?jaeger=1
#   6. Opens Jaeger UI at http://localhost:16686

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../../../.."; pwd)"
DOCKER_DIR="$REPO_ROOT/docker"

# ── 0. Free port 8080 ────────────────────────────────────────────────────────
PORT=8080
if lsof -ti:$PORT >/dev/null 2>&1; then
    echo "Port $PORT is in use — stopping existing process..."
    lsof -ti:$PORT | xargs kill -9 2>/dev/null || true
    sleep 1
    echo "Port $PORT freed."
fi

# ── 1. Start OTel stack ──────────────────────────────────────────────────────

# Verify Docker daemon is running before attempting anything
if ! docker info >/dev/null 2>&1; then
    echo ""
    echo "✗ Docker is not running."
    echo "  Open Docker Desktop and wait until the whale icon appears in the menu bar,"
    echo "  then re-run this script."
    echo ""
    exit 1
fi

echo ""
echo "Starting OTel Collector + Jaeger..."
cd "$DOCKER_DIR"
docker compose up -d

# Wait until port 4318 accepts TCP connections (nc -z = port scan, no data sent).
# Avoids printing HTTP responses to the terminal (curl would show JSON error bodies).
echo "Waiting for Collector on :4318..."
for i in $(seq 1 20); do
    if nc -z localhost 4318 2>/dev/null; then
        echo "Collector ready."
        break
    fi
    if [ "$i" -eq 20 ]; then
        echo "Warning: Collector did not respond in time — continuing anyway."
    fi
    sleep 1
done

# ── 2. Build JS SDK ──────────────────────────────────────────────────────────
echo ""
echo "Building JS SDK..."
cd "$REPO_ROOT"
./gradlew :sdk:jsBrowserProductionLibraryDistribution -q
echo "SDK built."

# ── 3. Serve ─────────────────────────────────────────────────────────────────
cd "$REPO_ROOT"
PAGE_URL="http://localhost:$PORT/examples/web/kmpsdk/kmplocal/?jaeger=1"
JAEGER_URL="http://localhost:16686"

echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│  Web demo  →  $PAGE_URL"
echo "│  Jaeger UI →  $JAEGER_URL"
echo "│"
echo "│  Press Ctrl+C to stop the web server."
echo "│  Docker stack keeps running — stop it with:"
echo "│    cd docker && docker compose down"
echo "└─────────────────────────────────────────────────────────┘"
echo ""

open "$PAGE_URL"    2>/dev/null || true
open "$JAEGER_URL"  2>/dev/null || true

python3 -m http.server $PORT