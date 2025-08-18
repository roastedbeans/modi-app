#!/bin/bash

echo "=== Testing File Access Methods ==="
echo

echo "1. Shell access to /sdcard/diag_logs:"
adb shell "ls -la /sdcard/diag_logs/*.qmdl | head -5"
echo

echo "2. Count of QMDL files from shell:"
adb shell "ls -1 /sdcard/diag_logs/*.qmdl | wc -l"
echo

echo "3. Root access test:"
adb shell "su -c 'find /sdcard/diag_logs -name \"*.qmdl\" -type f | head -3'" 2>/dev/null || echo "Root access not available"
echo

echo "4. Alternative directory check:"
adb shell "ls -la /storage/emulated/0/diag_logs/ 2>/dev/null | head -3" || echo "Alternative path not accessible"
echo

echo "5. App's perspective (recent logs):"
echo "Look for StorageHelper logs in the app..."
