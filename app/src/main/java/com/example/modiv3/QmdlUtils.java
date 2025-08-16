package com.example.modiv3;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class QmdlUtils {
    private static final String TAG = "QmdlUtils";
    private static String QMDL_SOURCE_DIR = "/sdcard/diag_logs";
    private static final String QMDL_BACKUP_DIR = "diag_logs";
    
    /**
     * Copy QMDL files from the source directory to a timestamped backup directory
     */
    public static boolean backupQmdlFiles(Context context) {
        try {
            // Add a small delay to ensure files are fully written
            Thread.sleep(500);
            
            File sourceDir = new File(QMDL_SOURCE_DIR);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                Log.w(TAG, "Source directory does not exist: " + QMDL_SOURCE_DIR);
                return false;
            }
            
            // Create timestamp for backup directory
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String backupDirName = "QMDL_" + timestamp;
            
            // Create backup directory in app's external files directory
            File backupDir = new File(context.getExternalFilesDir(QMDL_BACKUP_DIR), backupDirName);
            if (!backupDir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory: " + backupDir.getAbsolutePath());
                return false;
            }
            
            // List all files in the directory for debugging
            File[] allFiles = sourceDir.listFiles();
            if (allFiles != null) {
                Log.d(TAG, "Files found in source directory:");
                for (File file : allFiles) {
                    Log.d(TAG, "  - " + file.getName() + " (" + file.length() + " bytes)");
                }
            }
            
            // Copy all files (not just .qmdl files) to capture any diagnostic data
            File[] files = sourceDir.listFiles();
            if (files == null || files.length == 0) {
                Log.w(TAG, "No files found in source directory");
                return false;
            }
            
            int copiedCount = 0;
            for (File sourceFile : files) {
                // Skip directories and hidden files
                if (sourceFile.isDirectory() || sourceFile.getName().startsWith(".")) {
                    continue;
                }
                
                // Check if we can read the file
                if (!sourceFile.canRead()) {
                    Log.w(TAG, "Cannot read file: " + sourceFile.getName() + " - trying with root access");
                    // Try to copy with root access
                    if (copyFileWithRoot(sourceFile, backupDir)) {
                        copiedCount++;
                        Log.d(TAG, "Copied with root: " + sourceFile.getName());
                    } else {
                        Log.e(TAG, "Failed to copy with root: " + sourceFile.getName());
                    }
                } else {
                    File destFile = new File(backupDir, sourceFile.getName());
                    if (copyFile(sourceFile, destFile)) {
                        copiedCount++;
                        Log.d(TAG, "Copied: " + sourceFile.getName() + " (" + sourceFile.length() + " bytes)");
                    } else {
                        Log.e(TAG, "Failed to copy: " + sourceFile.getName());
                    }
                }
            }
            
            Log.i(TAG, "Backup completed. Copied " + copiedCount + " files to " + backupDir.getAbsolutePath());
            return copiedCount > 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during backup", e);
            return false;
        }
    }
    
    /**
     * Copy a single file from source to destination
     */
    private static boolean copyFile(File sourceFile, File destFile) {
        FileChannel source = null;
        FileChannel destination = null;
        
        try {
            if (!destFile.exists()) {
                destFile.createNewFile();
            }
            
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            
            if (destination != null && source != null) {
                destination.transferFrom(source, 0, source.size());
            }
            
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error copying file: " + sourceFile.getName(), e);
            return false;
        } finally {
            try {
                if (source != null) {
                    source.close();
                }
                if (destination != null) {
                    destination.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing file channels", e);
            }
        }
    }
    
    /**
     * Copy a file using root access
     */
    private static boolean copyFileWithRoot(File sourceFile, File backupDir) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            
            String sourcePath = sourceFile.getAbsolutePath();
            String destPath = new File(backupDir, sourceFile.getName()).getAbsolutePath();
            
            writer.write("cp " + sourcePath + " " + destPath + "\n");
            writer.write("echo 'COPY_RESULT:$?'\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean success = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("COPY_RESULT:")) {
                    String result = line.split(":")[1];
                    success = result.equals("0");
                    break;
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0 && success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying file with root: " + sourceFile.getName(), e);
            return false;
        }
    }
    
    /**
     * Get the size of QMDL files in the source directory
     */
    public static long getQmdlFilesSize() {
        try {
            File sourceDir = new File(QMDL_SOURCE_DIR);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                return 0;
            }
            
            File[] files = sourceDir.listFiles();
            if (files == null) {
                return 0;
            }
            
            long totalSize = 0;
            for (File file : files) {
                if (file.isFile()) {
                    totalSize += file.length();
                }
            }
            
            return totalSize;
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating QMDL files size", e);
            return 0;
        }
    }
    
    /**
     * Format file size in human readable format
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
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
    
    /**
     * Get a list of files in the QMDL source directory
     */
    public static String[] getQmdlFilesList() {
        try {
            File sourceDir = new File(QMDL_SOURCE_DIR);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                return new String[0];
            }
            
            File[] files = sourceDir.listFiles();
            if (files == null) {
                return new String[0];
            }
            
            String[] fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = files[i].getName() + " (" + files[i].length() + " bytes)";
            }
            
            return fileNames;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting QMDL files list", e);
            return new String[0];
        }
    }
}
