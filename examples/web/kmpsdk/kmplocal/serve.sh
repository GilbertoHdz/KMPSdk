#!/bin/bash
# Serve the local web example at http://localhost:8080 (console-only mode, no Docker needed)
# Requires: Python 3 (included in macOS)

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../../../.."; pwd)"
PORT=8080

# Free port if already in use (e.g. a previous serve.sh still running)
if lsof -ti:$PORT >/dev/null 2>&1; then
    echo "Port $PORT is in use — stopping existing process..."
    lsof -ti:$PORT | xargs kill -9 2>/dev/null || true
    sleep 1
fi

# Build the JS SDK before serving
echo "Building JS SDK..."
cd "$REPO_ROOT"
./gradlew :sdk:jsBrowserProductionLibraryDistribution -q
echo "SDK built."

# Serve from repo root so both the SDK build output and the example are reachable.
cd "$REPO_ROOT"
echo ""
echo "Serving at: http://localhost:$PORT/examples/web/kmpsdk/kmplocal/"
echo "Press Ctrl+C to stop."
echo ""
open "http://localhost:$PORT/examples/web/kmpsdk/kmplocal/" 2>/dev/null || true
python3 -m http.server $PORT