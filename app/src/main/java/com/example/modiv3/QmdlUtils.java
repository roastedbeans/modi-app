package com.example.modiv3;

import android.os.Environment;
import android.util.Log;

public class QmdlUtils {
    private static final String TAG = "QmdlUtils";
    private static String QMDL_SOURCE_DIR = "/sdcard/diag_logs";
    
    /**
     * Check if the device has root access
     */
    public static boolean hasRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking root access", e);
            return false;
        }
    }
    
    /**
     * Check if external storage is available and writable
     */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    
    /**
     * Check if external storage is available and readable
     */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }
    
    /**
     * Set the QMDL source directory
     */
    public static void setQmdlSourceDirectory(String directory) {
        QMDL_SOURCE_DIR = directory;
        Log.d(TAG, "QMDL source directory set to: " + directory);
    }
}
