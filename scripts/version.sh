#!/bin/bash

# Version management script for KMP SDK
# This script helps update the version in gradle.properties

set -e

GRADLE_PROPERTIES="gradle.properties"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to get current version
get_current_version() {
    grep "VERSION_NAME=" "$GRADLE_PROPERTIES" | cut -d'=' -f2
}

# Function to update version
update_version() {
    local new_version=$1
    sed -i.bak "s/VERSION_NAME=.*/VERSION_NAME=$new_version/" "$GRADLE_PROPERTIES"
    rm "${GRADLE_PROPERTIES}.bak"
    echo -e "${GREEN}✓ Version updated to $new_version${NC}"

    # Ask if user wants to rebuild XCFramework for SPM
    if command -v swift &> /dev/null; then
        echo ""
        read -p "Rebuild XCFramework for Swift Package Manager? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo -e "${YELLOW}Building XCFramework...${NC}"
            if [ -f "scripts/build-xcframework.sh" ]; then
                ./scripts/build-xcframework.sh release

                # Check if XCFramework was built successfully
                if [ -d "sdk/build/XCFrameworks/release/kmpsdk.xcframework" ]; then
                    echo -e "${GREEN}✓ XCFramework rebuilt successfully${NC}"
                    echo -e "${YELLOW}Don't forget to commit the XCFramework:${NC}"
                    echo -e "  git add sdk/build/XCFrameworks/release/"
                    echo -e "  git commit -m 'Update XCFramework for v$new_version'"
                fi
            else
                echo -e "${YELLOW}⚠️  build-xcframework.sh script not found${NC}"
                echo -e "   Run manually: ./gradlew :sdk:assembleKmpsdkReleaseXCFramework"
            fi
        fi
    fi
}

# Function to bump version
bump_version() {
    local current=$(get_current_version)
    local bump_type=$1

    IFS='.' read -r -a version_parts <<< "$current"
    local major="${version_parts[0]}"
    local minor="${version_parts[1]}"
    local patch="${version_parts[2]}"

    case $bump_type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            echo -e "${RED}✗ Invalid bump type: $bump_type${NC}"
            echo "Use: major, minor, or patch"
            exit 1
            ;;
    esac

    echo "$major.$minor.$patch"
}

# Display usage
usage() {
    echo "KMP SDK Version Management"
    echo ""
    echo "Usage: ./scripts/version.sh [command] [version]"
    echo ""
    echo "Commands:"
    echo "  current              Show current version"
    echo "  set <version>        Set specific version (e.g., 1.0.0)"
    echo "  bump major           Bump major version (1.0.0 -> 2.0.0)"
    echo "  bump minor           Bump minor version (1.0.0 -> 1.1.0)"
    echo "  bump patch           Bump patch version (1.0.0 -> 1.0.1)"
    echo ""
    echo "Examples:"
    echo "  ./scripts/version.sh current"
    echo "  ./scripts/version.sh set 2.0.0"
    echo "  ./scripts/version.sh bump patch"
}

# Main script
case "${1:-}" in
    current)
        version=$(get_current_version)
        echo -e "${GREEN}Current version: $version${NC}"
        ;;
    set)
        if [ -z "$2" ]; then
            echo -e "${RED}✗ Version number required${NC}"
            echo "Usage: ./scripts/version.sh set <version>"
            exit 1
        fi

        # Validate version format
        if ! [[ $2 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            echo -e "${RED}✗ Invalid version format: $2${NC}"
            echo "Use semantic versioning: MAJOR.MINOR.PATCH (e.g., 1.0.0)"
            exit 1
        fi

        current=$(get_current_version)
        echo -e "${YELLOW}Current version: $current${NC}"
        echo -e "${YELLOW}New version: $2${NC}"

        read -p "Update version? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            update_version "$2"
        else
            echo "Cancelled"
            exit 0
        fi
        ;;
    bump)
        if [ -z "$2" ]; then
            echo -e "${RED}✗ Bump type required${NC}"
            echo "Usage: ./scripts/version.sh bump [major|minor|patch]"
            exit 1
        fi

        current=$(get_current_version)
        new_version=$(bump_version "$2")

        echo -e "${YELLOW}Current version: $current${NC}"
        echo -e "${YELLOW}New version: $new_version${NC}"

        read -p "Bump version? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            update_version "$new_version"
        else
            echo "Cancelled"
            exit 0
        fi
        ;;
    *)
        usage
        exit 1
        ;;
esac
