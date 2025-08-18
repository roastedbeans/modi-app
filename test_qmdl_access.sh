#!/bin/bash

echo "=== Testing QMDL File Access ==="
echo

# Get the smallest QMDL file for testing
SMALLEST_FILE=$(adb shell "ls -la /sdcard/diag_logs/*.qmdl | sort -k5 -n | head -1 | awk '{print \$9}'")
echo "Smallest QMDL file: $SMALLEST_FILE"

if [ ! -z "$SMALLEST_FILE" ]; then
    echo
    echo "File details:"
    adb shell "ls -la '$SMALLEST_FILE'"
    echo
    
    echo "Testing file readability:"
    adb shell "su -c 'test -r \"$SMALLEST_FILE\" && echo \"READABLE\" || echo \"NOT_READABLE\"'" 2>/dev/null
    echo
    
    echo "First 32 bytes (hex):"
    adb shell "su -c 'head -c 32 \"$SMALLEST_FILE\" | xxd'" 2>/dev/null || echo "xxd not available"
    echo
    
    echo "HDLC frame check (looking for 0x7E):"
    adb shell "su -c 'head -c 100 \"$SMALLEST_FILE\" | od -t x1 | head -3'" 2>/dev/null
else
    echo "No QMDL files found"
fi

echo
echo "=== Test Complete ==="
