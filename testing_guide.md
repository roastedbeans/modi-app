# QMDL Parser Testing Guide

## 🎯 Current Status: READY FOR TESTING

### ✅ Issues Fixed:
1. **Scoped Storage**: App now finds your 21 QMDL files using root access
2. **Parse Errors**: Enhanced error handling with fallback messages
3. **File Access**: StorageHelper handles Android 11+ restrictions

### 📱 Testing Steps:

#### **Step 1: Test Parser (Recommended First)**
1. Open the app
2. Wait for "Ready" status
3. Press **"Test Parser"** button
4. **Expected**: Should find smallest QMDL file and parse it successfully
5. **Look for**: "Found QMDL files", parsing progress, message count

#### **Step 2: Parse All Files**
1. Press **"Parse QMDL"** button  
2. **Expected**: Should find and process all 21 files
3. **Watch for**: Progress updates, chunking for large files (400MB+)
4. **Note**: Large files will take time (30-60 seconds each)

#### **Step 3: Export Results**
1. After successful parsing, **"Export JSON"** button should enable
2. Press it to create readable JSON output
3. **Expected**: JSON file in `/sdcard/diag_logs/qmdl_export_*.json`

### 🔍 What to Monitor:

#### **ADB Logs** (run in terminal):
```bash
adb logcat | grep -E "(StorageHelper|QmdlParser|MainActivity)"
```

#### **Success Indicators**:
- ✅ `"Root found QMDL: diag_log_*.qmdl"`
- ✅ `"Found X QMDL files to parse"`
- ✅ `"Parsed X messages from Y in Z ms"`
- ✅ `"Chunking progress: X/Y"` (for large files)

#### **Expected Behavior**:
- **Small files** (< 20MB): Direct parsing, 1-5 seconds
- **Large files** (> 20MB): Chunked parsing, 30-60 seconds
- **Very large files** (400MB+): Multiple chunks, 2-3 minutes

### 🚨 If Issues Occur:

#### **Parse Errors**:
- Normal for some packets - parser creates fallback messages
- Look for "Unparseable packet" messages in logs
- Should still produce results, just with some raw packets

#### **Memory Issues**:
- App limits memory usage to 25% of heap
- Forces garbage collection between operations
- Large files are processed in chunks

#### **Permission Issues**:
- StorageHelper tries multiple access methods
- Root access should find files even with scoped storage
- Check "Storage Debug Info" in logs

### 📊 Expected Results:

With your 21 QMDL files (~2GB total):
- **Total messages**: Likely 50,000-200,000+ diagnostic messages
- **Technologies**: GSM, WCDMA, LTE, NR (depending on your device)
- **JSON size**: 50-200MB (depending on message content)
- **Parse time**: 5-15 minutes for all files

### 🎉 Success Criteria:
- ✅ App finds your existing QMDL files
- ✅ Parses files without crashing
- ✅ Produces meaningful message counts
- ✅ Creates JSON export successfully
- ✅ Shows technology breakdown (GSM/LTE/etc.)

**Ready to test!** Start with the "Test Parser" button and let me know what you see in the logs.
