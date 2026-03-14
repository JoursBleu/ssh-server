#!/bin/bash
set -e

# SSH Server build script
# Usage:
#   ./build.sh              # Build debug APK
#   ./build.sh release      # Build release APK
#   ./build.sh clean        # Clean build outputs

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/android-sdk" ]; then
        export ANDROID_HOME="$HOME/android-sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "Error: ANDROID_HOME not set and no SDK found"
        exit 1
    fi
fi

echo "ANDROID_HOME=$ANDROID_HOME"

LINT_SKIP="-x lint -x lintVitalRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease"

case "${1:-debug}" in
    debug)
        echo "Building debug APK..."
        ./gradlew clean assembleDebug
        APK="app/build/outputs/apk/debug/app-debug.apk"
        ;;
    release)
        if [ ! -f "release.keystore" ]; then
            echo "Error: release.keystore not found"
            echo "Generate one with:"
            echo "  keytool -genkey -v -keystore release.keystore -alias ssh-server -keyalg RSA -keysize 2048 -validity 36500"
            exit 1
        fi
        echo "Building release APK..."
        ./gradlew clean assembleRelease $LINT_SKIP
        APK="app/build/outputs/apk/release/app-release.apk"
        ;;
    clean)
        echo "Cleaning..."
        rm -rf app/build build .gradle
        ./gradlew clean
        echo "Done."
        exit 0
        ;;
    *)
        echo "Usage: $0 [debug|release|clean]"
        exit 1
        ;;
esac

if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo ""
    echo "BUILD SUCCESSFUL"
    echo "APK: $APK ($SIZE)"
else
    echo "Build failed: APK not found"
    exit 1
fi
