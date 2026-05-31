#!/bin/bash
# Serve the local web example at http://localhost:8080
# Requires: Python 3 (included in macOS)

set -e

# Build the JS SDK before serving
REPO_ROOT="$(cd "$(dirname "$0")/../../../.."; pwd)"
echo "Building JS SDK..."
cd "$REPO_ROOT"
./gradlew :sdk:jsBrowserProductionLibraryDistribution -q
echo "SDK built."

# Start the local server and open the browser
cd "$REPO_ROOT/examples/web/kmpsdk/kmplocal"
echo ""
echo "Serving at: http://localhost:8080"
echo "Press Ctrl+C to stop."
echo ""
open "http://localhost:8080" 2>/dev/null || true
python3 -m http.server 8080