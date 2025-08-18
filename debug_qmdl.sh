#!/bin/bash

echo "=== QMDL Parser Debug Information ==="
echo

echo "1. Checking /sdcard/diag_logs directory:"
adb shell "ls -la /sdcard/diag_logs/ | head -10"
echo

echo "2. Checking app permissions:"
adb shell "dumpsys package com.example.modiv3 | grep -A 5 -B 5 permission"
echo

echo "3. Testing file access:"
adb shell "su -c 'ls -la /sdcard/diag_logs/*.qmdl | wc -l'" 2>/dev/null || echo "Root access not available"
echo

echo "4. Checking app's private directory:"
adb shell "run-as com.example.modiv3 ls -la files/" 2>/dev/null || echo "App private directory not accessible"
echo

echo "5. Recent app logs (last 50 lines):"
adb logcat -d | grep "com.example.modiv3" | tail -50
echo

echo "=== Debug Complete ==="
