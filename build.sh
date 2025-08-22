#!/bin/bash

# MODIV3 Android App Build and Install Script
# Usage: ./build.sh [debug|release] [clean]

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
BUILD_TYPE="debug"
CLEAN_BUILD=false
PROJECT_DIR="/Users/roastedbeans/AndroidStudioProjects/MODIV3"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [debug|release] [clean]"
    echo ""
    echo "Arguments:"
    echo "  debug    - Build debug variant (default)"
    echo "  release  - Build release variant"
    echo "  clean    - Clean build cache before building"
    echo ""
    echo "Examples:"
    echo "  $0                    # Build and install debug variant"
    echo "  $0 release           # Build and install release variant"
    echo "  $0 debug clean       # Clean and build debug variant"
    echo "  $0 release clean     # Clean and build release variant"
    exit 1
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        debug|release)
            BUILD_TYPE="$1"
            shift
            ;;
        clean)
            CLEAN_BUILD=true
            shift
            ;;
        -h|--help)
            show_usage
            ;;
        *)
            print_error "Unknown argument: $1"
            show_usage
            ;;
    esac
done

# Function to check if device is connected
check_device() {
    print_status "Checking for connected Android devices..."
    if ! adb devices | grep -q "device$"; then
        print_error "No Android device connected!"
        print_status "Please connect your Android device and ensure USB debugging is enabled."
        print_status "You can also use an Android emulator."
        exit 1
    fi

    # Show connected devices
    print_status "Connected devices:"
    adb devices | grep "device$" | while read line; do
        echo "  $line"
    done
}

# Function to build the app
build_app() {
    print_status "Building $BUILD_TYPE variant..."

    cd "$PROJECT_DIR"

    # Clean build
    print_status "Cleaning build cache..."
    ./gradlew clean

    # Build the app
    print_status "Building debug APK..."
    ./gradlew assembleDebug

    if [ $? -eq 0 ]; then
        print_success "Build completed successfully!"
    else
        print_error "Build failed!"
        exit 1
    fi
}

# Function to install the app
install_app() {
    print_status "Installing app to device..."

    cd "$PROJECT_DIR"

    # Determine APK path
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

    # Check if APK exists
    if [ ! -f "$APK_PATH" ]; then
        print_error "APK not found at $APK_PATH"
        exit 1
    fi

    # Install APK
    print_status "Installing $APK_PATH..."
    if adb install -r "$APK_PATH"; then
        print_success "App installed successfully!"
        print_status "You can now launch the MODIV3 app from your device."
    else
        print_error "Installation failed!"
        print_status "Trying to uninstall existing app and reinstall..."
        adb uninstall com.example.modiv3 || true
        if adb install "$APK_PATH"; then
            print_success "App installed successfully after uninstall!"
        else
            print_error "Installation failed even after uninstall!"
            exit 1
        fi
    fi
}

# Function to show build information
show_build_info() {
    print_status "Build Configuration:"
    echo "  Project: MODIV3"
    echo "  Package: com.example.modiv3"
    echo "  Build Type: $BUILD_TYPE"
    echo "  Clean Build: $CLEAN_BUILD"
    echo "  Python Integration: Chaquopy 3.8"
    echo "  Native Support: C++ (armeabi-v7a, arm64-v8a, x86, x86_64)"
}

# Main execution
main() {
    echo "======================================"
    echo "  MODIV3 Android App Builder"
    echo "======================================"

    show_build_info
    echo ""

    # Check for required tools
    if ! command -v adb &> /dev/null; then
        print_error "ADB (Android Debug Bridge) is not installed!"
        print_status "Please install Android SDK platform tools."
        exit 1
    fi

    if ! command -v ./gradlew &> /dev/null  && [ ! -f "gradlew" ]; then
        print_error "Gradle wrapper not found!"
        print_status "Please run this script from the project root directory."
        exit 1
    fi

    # Check for connected device
    check_device
    echo ""

    # Build the app
    build_app
    echo ""

    # Install the app
    install_app
    echo ""

    print_success "Build and install process completed!"
    print_status "App package: com.example.modiv3"
    print_status "You can now use the app on your device."
}

# Run main function
main "$@"
