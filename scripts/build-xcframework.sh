#!/bin/bash

# Build XCFramework for Swift Package Manager
# This script builds the XCFramework that SPM will use for iOS distribution

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Building XCFramework for SPM            ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# Configuration
BUILD_TYPE="${1:-release}"  # release or debug
XCFRAMEWORK_PATH="sdk/build/XCFrameworks/${BUILD_TYPE}/kmpsdk.xcframework"

echo -e "${YELLOW}Build Configuration:${NC}"
echo -e "  Type: ${BUILD_TYPE}"
echo -e "  Output: ${XCFRAMEWORK_PATH}"
echo ""

# Clean previous build
echo -e "${YELLOW}🧹 Cleaning previous build...${NC}"
rm -rf "sdk/build/XCFrameworks/${BUILD_TYPE}"

# Build XCFramework
echo -e "${YELLOW}🔨 Building XCFramework...${NC}"
if [ "$BUILD_TYPE" = "release" ]; then
    ./gradlew :sdk:assembleKmpsdkReleaseXCFramework
else
    ./gradlew :sdk:assembleKmpsdkDebugXCFramework
fi

# Verify build
echo ""
if [ -d "$XCFRAMEWORK_PATH" ]; then
    echo -e "${GREEN}✅ XCFramework built successfully!${NC}"
    echo ""

    # Show details
    echo -e "${BLUE}📦 Framework Details:${NC}"
    echo -e "  Location: ${XCFRAMEWORK_PATH}"

    # Show size
    FRAMEWORK_SIZE=$(du -sh "$XCFRAMEWORK_PATH" | cut -f1)
    echo -e "  Size: ${FRAMEWORK_SIZE}"

    # Show architectures
    echo -e "  Architectures:"
    find "$XCFRAMEWORK_PATH" -name "*.framework" -type d | while read framework; do
        ARCH=$(basename $(dirname "$framework"))
        echo -e "    - ${ARCH}"
    done

    echo ""
    echo -e "${GREEN}🎉 Ready for Swift Package Manager!${NC}"
    echo ""

    # Show next steps
    echo -e "${BLUE}📝 Next Steps:${NC}"
    echo -e "  1. Review the XCFramework: ${XCFRAMEWORK_PATH}"
    echo -e "  2. Test in Xcode project"
    echo -e "  3. Commit to git: git add ${XCFRAMEWORK_PATH}"
    echo -e "  4. Push and tag: git tag <version> && git push --tags"
    echo ""

    # Check if XCFramework is tracked by git
    if git ls-files --error-unmatch "$XCFRAMEWORK_PATH" &>/dev/null; then
        echo -e "${GREEN}✓ XCFramework is already tracked by git${NC}"
    else
        echo -e "${YELLOW}⚠️  XCFramework is not tracked by git${NC}"
        echo -e "   To track it: git add ${XCFRAMEWORK_PATH}"
    fi

else
    echo -e "${RED}❌ XCFramework build failed!${NC}"
    echo -e "${RED}   Check the build logs above for errors${NC}"
    exit 1
fi
