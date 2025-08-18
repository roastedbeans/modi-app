package com.example.modiv3;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to handle file access across different Android versions
 * Handles scoped storage issues in Android 11+
 */
public class StorageHelper {
    private static final String TAG = "StorageHelper";
    
    /**
     * Get QMDL files from the diag_logs directory using multiple methods
     */
    public static List<File> getQmdlFiles(String directoryPath) {
        List<File> qmdlFiles = new ArrayList<>();
        
        try {
            // Method 1: Direct file access (works on older Android versions)
            File directory = new File(directoryPath);
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    Log.d(TAG, "Direct access found " + files.length + " total files");
                    for (File file : files) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".qmdl")) {
                            qmdlFiles.add(file);
                            Log.d(TAG, "Found QMDL: " + file.getName() + " (" + file.length() + " bytes)");
                        }
                    }
                } else {
                    Log.w(TAG, "Directory.listFiles() returned null for: " + directoryPath);
                }
            } else {
                Log.w(TAG, "Directory does not exist or is not accessible: " + directoryPath);
            }
            
            // Method 2: Try with root access if available
            if (qmdlFiles.isEmpty()) {
                Log.d(TAG, "Trying root access method...");
                qmdlFiles = getQmdlFilesWithRoot(directoryPath);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error accessing files in " + directoryPath, e);
        }
        
        Log.d(TAG, "Total QMDL files found: " + qmdlFiles.size());
        return qmdlFiles;
    }
    
    /**
     * Try to get QMDL files using root access
     */
    private static List<File> getQmdlFilesWithRoot(String directoryPath) {
        List<File> qmdlFiles = new ArrayList<>();
        
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(process.getOutputStream());
            
            // List all .qmdl files in the directory
            writer.write("find " + directoryPath + " -name '*.qmdl' -type f\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().endsWith(".qmdl")) {
                    File file = new File(line.trim());
                    if (file.exists()) {
                        qmdlFiles.add(file);
                        Log.d(TAG, "Root found QMDL: " + file.getName() + " (" + file.length() + " bytes)");
                    }
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            Log.d(TAG, "Root file listing exit code: " + exitCode);
            
        } catch (Exception e) {
            Log.w(TAG, "Root access method failed", e);
        }
        
        return qmdlFiles;
    }
    
    /**
     * Check if we can read a specific file
     */
    public static boolean canReadFile(String filePath) {
        try {
            File file = new File(filePath);
            boolean canRead = file.exists() && file.canRead() && file.length() > 0;
            Log.d(TAG, "File " + filePath + " readable: " + canRead + " (size: " + file.length() + ")");
            return canRead;
        } catch (Exception e) {
            Log.w(TAG, "Error checking file readability: " + filePath, e);
            return false;
        }
    }
    
    /**
     * Get alternative directories to try for QMDL files
     */
    public static String[] getAlternativeDirectories() {
        return new String[] {
            "/sdcard/diag_logs",
            "/storage/emulated/0/diag_logs",
            "/sdcard/Download/diag_logs",
            "/data/local/tmp/diag_logs",
            Environment.getExternalStorageDirectory() + "/diag_logs"
        };
    }
    
    /**
     * Find QMDL files in any accessible directory
     */
    public static List<File> findQmdlFilesAnywhere() {
        List<File> allFiles = new ArrayList<>();
        String[] directories = getAlternativeDirectories();
        
        for (String dir : directories) {
            Log.d(TAG, "Searching directory: " + dir);
            List<File> files = getQmdlFiles(dir);
            if (!files.isEmpty()) {
                Log.d(TAG, "Found " + files.size() + " files in " + dir);
                allFiles.addAll(files);
            }
        }
        
        return allFiles;
    }
    
    /**
     * Check if external storage is available and accessible
     */
    public static boolean isExternalStorageAccessible() {
        String state = Environment.getExternalStorageState();
        boolean accessible = Environment.MEDIA_MOUNTED.equals(state);
        Log.d(TAG, "External storage state: " + state + ", accessible: " + accessible);
        return accessible;
    }
    
    /**
     * Get debug information about storage access
     */
    public static String getStorageDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Storage Debug Info ===\n");
        info.append("Android Version: ").append(Build.VERSION.SDK_INT).append("\n");
        info.append("External Storage State: ").append(Environment.getExternalStorageState()).append("\n");
        info.append("External Storage Dir: ").append(Environment.getExternalStorageDirectory()).append("\n");
        
        // Check each alternative directory
        String[] dirs = getAlternativeDirectories();
        for (String dir : dirs) {
            File directory = new File(dir);
            info.append("Directory: ").append(dir).append("\n");
            info.append("  Exists: ").append(directory.exists()).append("\n");
            info.append("  Is Directory: ").append(directory.isDirectory()).append("\n");
            info.append("  Can Read: ").append(directory.canRead()).append("\n");
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles();
                info.append("  File Count: ").append(files != null ? files.length : "null").append("\n");
            }
        }
        
        return info.toString();
    }
}
