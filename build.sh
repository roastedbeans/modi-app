
#!/bin/bash

# MODIV3 Build and Install Script
# This script builds the Android app and installs it on your connected phone

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to check if device is connected
check_device_connection() {
    print_status "Checking for connected Android devices..."
    
    if ! command_exists adb; then
        print_error "ADB not found. Please install Android SDK Platform Tools."
        print_status "You can download it from: https://developer.android.com/studio/releases/platform-tools"
        exit 1
    fi
    
    # Start ADB server
    adb start-server >/dev/null 2>&1
    
    # Check for connected devices
    local devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
    
    if [ "$devices" -eq 0 ]; then
        print_error "No Android devices found!"
        print_status "Please ensure:"
        print_status "1. Your phone is connected via USB"
        print_status "2. USB debugging is enabled in Developer Options"
        print_status "3. You've authorized the computer on your phone"
        exit 1
    fi
    
    print_success "Found $devices connected device(s)"
    adb devices | grep -v "List of devices"
}

# Function to clean previous builds
clean_build() {
    print_status "Cleaning previous builds..."
    ./gradlew clean
    print_success "Clean completed"
}

# Function to build the app
build_app() {
    print_status "Building MODIV3 app..."
    
    # Check if gradlew exists
    if [ ! -f "./gradlew" ]; then
        print_error "gradlew not found in current directory!"
        print_status "Please run this script from the project root directory."
        exit 1
    fi
    
    # Make gradlew executable
    chmod +x ./gradlew
    
    # Build debug APK
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
    print_status "Installing MODIV3 app on device..."
    
    # Find the APK file
    local apk_path="app/build/outputs/apk/debug/app-debug.apk"
    
    if [ ! -f "$apk_path" ]; then
        print_error "APK file not found at: $apk_path"
        print_status "Build may have failed or APK is in a different location."
        exit 1
    fi
    
    # Uninstall existing app if it exists
    print_status "Checking for existing installation..."
    if adb shell pm list packages | grep -q "com.example.modiv3"; then
        print_warning "Existing MODIV3 app found. Uninstalling..."
        adb uninstall com.example.modiv3
        print_success "Previous installation removed"
    fi
    
    # Install the new APK
    print_status "Installing new APK..."
    adb install -r "$apk_path"
    
    if [ $? -eq 0 ]; then
        print_success "MODIV3 app installed successfully!"
    else
        print_error "Installation failed!"
        exit 1
    fi
}

# Function to launch the app
launch_app() {
    print_status "Launching MODIV3 app..."
    adb shell am start -n com.example.modiv3/.MainActivity
    
    if [ $? -eq 0 ]; then
        print_success "MODIV3 app launched successfully!"
    else
        print_warning "Failed to launch app automatically. You can launch it manually from your phone."
    fi
}

# Function to show app info
show_app_info() {
    print_status "App Information:"
    echo "Package: com.example.modiv3"
    echo "Version: 1.0"
    echo "Min SDK: 24 (Android 7.0)"
    echo "Target SDK: 36 (Android 14)"
    echo ""
    print_status "Features:"
    echo "- QMDL file processing and splitting"
    echo "- Memory-efficient file operations"
    echo "- Background processing capabilities"
    echo "- Root access detection"
}

# Main execution
main() {
    echo "=========================================="
    echo "    MODIV3 Build and Install Script"
    echo "=========================================="
    echo ""
    
    # Check if we're in the right directory
    if [ ! -f "settings.gradle.kts" ] || [ ! -d "app" ]; then
        print_error "This doesn't appear to be the MODIV3 project root directory!"
        print_status "Please run this script from the project root directory."
        exit 1
    fi
    
    # Show app info
    show_app_info
    echo ""
    
    # Check device connection
    check_device_connection
    echo ""
    
    # Clean and build
    clean_build
    echo ""
    
    build_app
    echo ""
    
    # Install and launch
    install_app
    echo ""
    
    launch_app
    echo ""
    
    print_success "MODIV3 app has been successfully built and installed!"
    print_status "The app should now be running on your device."
    echo ""
    print_status "To uninstall the app later, use: adb uninstall com.example.modiv3"
}

# Handle script interruption
trap 'print_error "Script interrupted by user"; exit 1' INT TERM

# Run main function
main "$@"
