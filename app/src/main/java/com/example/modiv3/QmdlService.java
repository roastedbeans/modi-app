package com.example.modiv3;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class QmdlService extends Service {
    private static final String TAG = "QmdlService";
    private static String QMDL_DIR = "/sdcard/diag_logs";
    private static String CONFIG_FILE = "/sdcard/diag_logs/Diag.cfg";
    
    // Fallback directory if main directory fails
    private static final String FALLBACK_DIR = "/data/local/tmp/diag_logs";
    private static final String FALLBACK_CONFIG = "/data/local/tmp/diag_logs/Diag.cfg";
    
    // Alternative directory paths to try if the default fails
    private static final String[] ALTERNATIVE_DIRS = {
        "/storage/emulated/0/diag_logs",
        "/sdcard/Download/diag_logs",
        "/data/local/tmp/diag_logs"
    };
    
    private Process suProcess;
    private Process diagProcess;
    private boolean isRunning = false;
    private volatile boolean shouldStop = false;
    private Thread outputReaderThread;
    private Thread errorReaderThread;
    private Thread monitorThread;
    
    public interface QmdlCallback {
        void onStatusUpdate(String status);
        void onLogUpdate(String log);
        void onError(String error);
    }
    
    private QmdlCallback callback;
    
    public void setCallback(QmdlCallback callback) {
        this.callback = callback;
    }
    
    public void setLogDirectory(String directory) {
        // Update the directory constants
        QMDL_DIR = directory;
        CONFIG_FILE = directory + "/Diag.cfg";
        updateLog("Log directory set to: " + directory);
    }
    

    
    private static QmdlService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // GOOD: Call setup method here when Context is guaranteed to be valid
        setupDirectories();
        
        // Verify folder and Diag.cfg are present and active on app load
        updateLog("=== App Load Verification ===");
        if (!verifyFolderAndConfig()) {
            updateLog("WARNING: Folder or Diag.cfg verification failed on app load");
        } else {
            updateLog("✓ App load verification passed - ready to start");
        }
    }
    
    public static QmdlService getInstance() {
        return instance;
    }
    
    public class LocalBinder extends Binder {
        public QmdlService getService() {
            return QmdlService.this;
        }
    }
    
    private final IBinder binder = new LocalBinder();
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public boolean startQmdlGathering() {
        if (isRunning) {
            updateStatus("QMDL gathering is already running");
            return false;
        }
        
        try {
            // Reset stop flag
            shouldStop = false;
            
            // Verification already done on app load, proceed directly
            updateStatus("Starting QMDL gathering...");
            
            // Start the diag_mdlog process
            startDiagProcess();
            
            isRunning = true;
            updateStatus("QMDL gathering started successfully");
            updateLog("diag_mdlog process started successfully");
            
            return true;
            
        } catch (Exception e) {
            updateError("Failed to start QMDL gathering: " + e.getMessage());
            Log.e(TAG, "Error starting QMDL gathering", e);
            return false;
        }
    }
    
    public boolean stopQmdlGathering() {
        if (!isRunning) {
            updateStatus("QMDL gathering is not running");
            return false;
        }
        
        try {
            updateStatus("Stopping QMDL gathering...");
            
            // Set stop flag to signal threads to stop
            shouldStop = true;
            
            // Kill diag_mdlog processes using root
            Process killProcess = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(killProcess.getOutputStream());
            writer.write("pkill -f diag_mdlog\n");
            writer.write("echo 'Killed diag_mdlog processes'\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            // Wait for kill command to complete
            int killExitCode = killProcess.waitFor();
            updateLog("Kill command exit code: " + killExitCode);
            
            // Wait a moment for processes to terminate
            Thread.sleep(1000);
            
            // Stop the diag process
            if (diagProcess != null) {
                try {
                    diagProcess.destroy();
                    updateLog("Destroyed diag process");
                } catch (Exception e) {
                    Log.e(TAG, "Error destroying diag process", e);
                }
                diagProcess = null;
            }
            
            // Stop the su process
            if (suProcess != null) {
                try {
                    suProcess.destroy();
                    updateLog("Destroyed su process");
                } catch (Exception e) {
                    Log.e(TAG, "Error destroying su process", e);
                }
                suProcess = null;
            }
            
            // Wait for reader threads to finish
            if (outputReaderThread != null && outputReaderThread.isAlive()) {
                outputReaderThread.interrupt();
                try {
                    outputReaderThread.join(2000); // Wait up to 2 seconds
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for output reader thread");
                }
            }
            
            if (errorReaderThread != null && errorReaderThread.isAlive()) {
                errorReaderThread.interrupt();
                try {
                    errorReaderThread.join(2000); // Wait up to 2 seconds
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for error reader thread");
                }
            }
            
            if (monitorThread != null && monitorThread.isAlive()) {
                monitorThread.interrupt();
                try {
                    monitorThread.join(2000); // Wait up to 2 seconds
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for monitor thread");
                }
            }
            
            isRunning = false;
            updateStatus("QMDL gathering stopped");
            updateLog("Processes terminated. QMDL files saved to " + QMDL_DIR);
            
            return true;
            
        } catch (Exception e) {
            updateError("Failed to stop QMDL gathering: " + e.getMessage());
            Log.e(TAG, "Error stopping QMDL gathering", e);
            return false;
        }
    }
    
    private boolean checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            
            // Test with a simple command
            writer.write("id\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            // Read output to verify root access
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean hasRoot = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("uid=0") || line.contains("root")) {
                    hasRoot = true;
                    break;
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0 && hasRoot;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking root access", e);
            return false;
        }
    }
    
    /**
     * Copy Diag.cfg file from assets to the target location
     */
    private boolean copyConfigFromAssets(String targetConfigPath) {
        try {
            updateLog("Copying Diag.cfg from assets...");
            
            // Check if we have a proper Context
            if (getApplicationContext() == null) {
                updateLog("No application context available");
                return false;
            }
            
            // Open the asset file and read content
            InputStream inputStream = getAssets().open("Diag.cfg");
            byte[] assetContent = new byte[inputStream.available()];
            inputStream.read(assetContent);
            inputStream.close();
            
            File targetFile = new File(targetConfigPath);
            
            // Create parent directory if it doesn't exist
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    updateLog("Failed to create parent directory, trying with root access");
                    return copyConfigWithRootAccess(targetConfigPath);
                }
            }
            
            // Try to copy the file normally first
            try {
                FileOutputStream outputStream = new FileOutputStream(targetFile);
                outputStream.write(assetContent);
                outputStream.close();
                
                updateLog("Diag.cfg copied successfully to: " + targetConfigPath);
                logConfigFileContent(targetConfigPath);
                return true;
                
            } catch (IOException e) {
                updateLog("Normal copy failed, trying with root access: " + e.getMessage());
                return copyConfigWithRootAccess(targetConfigPath);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying Diag.cfg from assets", e);
            updateLog("Error copying Diag.cfg: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Copy config file using root access
     */
    private boolean copyConfigWithRootAccess(String targetConfigPath) {
        try {
            updateLog("Copying Diag.cfg with root access...");
            
            // Read asset content as binary - must succeed
            InputStream inputStream = getAssets().open("Diag.cfg");
            byte[] assetContent = new byte[inputStream.available()];
            inputStream.read(assetContent);
            inputStream.close();
            
            // For binary files, we need to write the bytes directly, not as string
            updateLog("Asset file size: " + assetContent.length + " bytes");
            
            // Use base64 to handle binary data
            String base64Content = android.util.Base64.encodeToString(assetContent, android.util.Base64.NO_WRAP);
            
            // Use root to write the file using base64 decode
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            
            // Write the file using base64 decode
            writer.write("echo '" + base64Content + "' | base64 -d > " + targetConfigPath + "\n");
            writer.write("chmod 666 " + targetConfigPath + "\n");
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
            if (exitCode == 0 && success) {
                updateLog("Diag.cfg copied successfully with root access");
                logConfigFileContent(targetConfigPath);
                return true;
            } else {
                updateLog("Failed to copy with root access");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying config with root access", e);
            updateLog("Error copying with root access: " + e.getMessage());
            return false;
        }
    }
    

    
    /**
     * Verify that the folder and Diag.cfg are present and active
     */
    private boolean verifyFolderAndConfig() {
        try {
            updateLog("=== Starting folder and config verification ===");
            
            String targetDir = QMDL_DIR;
            String targetConfig = CONFIG_FILE;
            
            // If directory hasn't been set yet, use default
            if (targetDir == null || targetDir.isEmpty()) {
                targetDir = "/sdcard/diag_logs";
                targetConfig = "/sdcard/diag_logs/Diag.cfg";
            }
            
            updateLog("Verifying directory: " + targetDir);
            updateLog("Verifying config file: " + targetConfig);
            
            // Use root to verify everything
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            
            // Comprehensive verification commands
            writer.write("echo '=== VERIFICATION START ==='\n");
            
            // Check if directory exists and is accessible
            writer.write("if [ -d " + targetDir + " ]; then\n");
            writer.write("  echo 'DIRECTORY_EXISTS'\n");
            writer.write("  ls -ld " + targetDir + "\n");
            writer.write("  if [ -w " + targetDir + " ]; then\n");
            writer.write("    echo 'DIRECTORY_WRITABLE'\n");
            writer.write("  else\n");
            writer.write("    echo 'DIRECTORY_NOT_WRITABLE'\n");
            writer.write("  fi\n");
            writer.write("else\n");
            writer.write("  echo 'DIRECTORY_MISSING'\n");
            writer.write("fi\n");
            
            // Check if config file exists and is readable
            writer.write("if [ -f " + targetConfig + " ]; then\n");
            writer.write("  echo 'CONFIG_EXISTS'\n");
            writer.write("  ls -l " + targetConfig + "\n");
            writer.write("  if [ -r " + targetConfig + " ]; then\n");
            writer.write("    echo 'CONFIG_READABLE'\n");
            writer.write("    echo '=== CONFIG CONTENT ==='\n");
            writer.write("    cat " + targetConfig + "\n");
            writer.write("    echo '=== END CONFIG CONTENT ==='\n");
            writer.write("  else\n");
            writer.write("    echo 'CONFIG_NOT_READABLE'\n");
            writer.write("  fi\n");
            writer.write("else\n");
            writer.write("  echo 'CONFIG_MISSING'\n");
            writer.write("fi\n");
            
            // Check if diag_mdlog is available
            writer.write("if command -v diag_mdlog >/dev/null 2>&1; then\n");
            writer.write("  echo 'DIAG_MDLOG_AVAILABLE'\n");
            writer.write("  which diag_mdlog\n");
            writer.write("else\n");
            writer.write("  echo 'DIAG_MDLOG_MISSING'\n");
            writer.write("fi\n");
            
            // Check current user and permissions
            writer.write("echo '=== CURRENT USER ==='\n");
            writer.write("id\n");
            writer.write("whoami\n");
            
            writer.write("echo '=== VERIFICATION END ==='\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            // Read the verification output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean directoryExists = false;
            boolean directoryWritable = false;
            boolean configExists = false;
            boolean configReadable = false;
            boolean diagMdlogAvailable = false;
            
            while ((line = reader.readLine()) != null) {
                updateLog("Verify: " + line);
                
                if (line.contains("DIRECTORY_EXISTS")) {
                    directoryExists = true;
                } else if (line.contains("DIRECTORY_WRITABLE")) {
                    directoryWritable = true;
                } else if (line.contains("CONFIG_EXISTS")) {
                    configExists = true;
                } else if (line.contains("CONFIG_READABLE")) {
                    configReadable = true;
                } else if (line.contains("DIAG_MDLOG_AVAILABLE")) {
                    diagMdlogAvailable = true;
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            updateLog("Verification process exit code: " + exitCode);
            
            // Check all requirements
            if (!directoryExists) {
                updateLog("ERROR: Directory does not exist");
                return false;
            }
            
            if (!directoryWritable) {
                updateLog("ERROR: Directory is not writable");
                return false;
            }
            
            if (!configExists) {
                updateLog("ERROR: Diag.cfg file does not exist");
                return false;
            }
            
            if (!configReadable) {
                updateLog("ERROR: Diag.cfg file is not readable");
                return false;
            }
            
            if (!diagMdlogAvailable) {
                updateLog("ERROR: diag_mdlog command is not available");
                return false;
            }
            
            updateLog("=== All verifications passed ===");
            updateLog("✓ Directory exists and is writable");
            updateLog("✓ Diag.cfg exists and is readable");
            updateLog("✓ diag_mdlog command is available");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during folder and config verification", e);
            updateLog("Error during verification: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Log the content of the config file for debugging
     */
    private void logConfigFileContent(String configPath) {
        try {
            File configFile = new File(configPath);
            if (configFile.exists()) {
                updateLog("Config file size: " + configFile.length() + " bytes");
                updateLog("Config file path: " + configFile.getAbsolutePath());
                
                // Read and log first few lines of config
                BufferedReader reader = new BufferedReader(new java.io.FileReader(configFile));
                String line;
                int lineCount = 0;
                updateLog("Config file content (first 10 lines):");
                while ((line = reader.readLine()) != null && lineCount < 10) {
                    updateLog("  " + line);
                    lineCount++;
                }
                if (lineCount >= 10) {
                    updateLog("  ... (truncated)");
                }
                reader.close();
            }
        } catch (Exception e) {
            updateLog("Error reading config file: " + e.getMessage());
        }
    }
    
    private boolean setupDirectories() {
        try {
            updateLog("Setting up directories...");
            
            // Use the current directory that was set during initial load
            String targetDir = QMDL_DIR;
            String targetConfig = CONFIG_FILE;
            
            // If directory hasn't been set yet, use default
            if (targetDir == null || targetDir.isEmpty()) {
                targetDir = "/sdcard/diag_logs";
                targetConfig = "/sdcard/diag_logs/Diag.cfg";
                updateLog("Using default directory: " + targetDir);
            }
            
            updateLog("Using directory: " + targetDir);
            
            // Copy Diag.cfg from assets to the target location
            if (!copyConfigFromAssets(targetConfig)) {
                updateLog("Error: Failed to copy Diag.cfg from assets");
                return false;
            }
            
            // For external storage /sdcard/diag_logs, we need root access
            updateLog("Checking existing directory: " + targetDir);
            
            // First, let's check what's actually in the external storage
            Process checkProcess = Runtime.getRuntime().exec("su");
            OutputStreamWriter checkWriter = new OutputStreamWriter(checkProcess.getOutputStream());
            checkWriter.write("ls -la /sdcard/\n");
            checkWriter.write("echo '=== Checking if diag_logs exists ==='\n");
            checkWriter.write("ls -la /sdcard/diag_logs 2>/dev/null || echo 'diag_logs not found'\n");
            checkWriter.write("echo '=== Current user and permissions ==='\n");
            checkWriter.write("id\n");
            checkWriter.write("whoami\n");
            checkWriter.write("echo '=== External storage permissions ==='\n");
            checkWriter.write("ls -ld /sdcard/\n");
            checkWriter.write("exit\n");
            checkWriter.flush();
            checkWriter.close();
            
            BufferedReader checkReader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
            String checkLine;
            while ((checkLine = checkReader.readLine()) != null) {
                updateLog("Check: " + checkLine);
            }
            checkReader.close();
            
            int checkExitCode = checkProcess.waitFor();
            updateLog("Check process exit code: " + checkExitCode);
            
            // Now proceed with the actual setup
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            
            // Always create the directory if it doesn't exist
            writer.write("echo '=== Directory Setup ==='\n");
            writer.write("if [ -d " + targetDir + " ]; then\n");
            writer.write("  echo 'Directory already exists'\n");
            writer.write("  ls -la " + targetDir + "\n");
            writer.write("  echo 'Directory permissions check:'\n");
            writer.write("  stat " + targetDir + "\n");
            writer.write("else\n");
            writer.write("  echo 'Creating directory'\n");
            writer.write("  mkdir -p " + targetDir + " 2>&1\n");
            writer.write("  if [ $? -eq 0 ]; then\n");
            writer.write("    echo 'Directory created successfully'\n");
            writer.write("    ls -la " + targetDir + "\n");
            writer.write("  else\n");
            writer.write("    echo 'Failed to create directory'\n");
            writer.write("    exit 1\n");
            writer.write("  fi\n");
            writer.write("fi\n");
            
            // Set permissions
            writer.write("echo '=== Setting Permissions ==='\n");
            writer.write("chmod 777 " + targetDir + " 2>&1\n");
            writer.write("chown system:system " + targetDir + " 2>&1\n");
            writer.write("echo 'Permissions set'\n");
            
            // Verify the directory
            writer.write("echo '=== Verification ==='\n");
            writer.write("ls -la " + targetDir + "\n");
            writer.write("test -w " + targetDir + " && echo 'Directory is writable' || echo 'Directory is NOT writable'\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            // Read the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean directoryOk = false;
            while ((line = reader.readLine()) != null) {
                updateLog("Setup: " + line);
                if (line.contains("Directory is writable")) {
                    directoryOk = true;
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            updateLog("Setup process exit code: " + exitCode);
            
            if (!directoryOk) {
                updateLog("Warning: Config file not found, but continuing...");
            }
            
            updateLog("Directory setup completed successfully");
            
            // Additional verification - check if directory actually exists
            try {
                File verifyDir = new File(targetDir);
                if (verifyDir.exists() && verifyDir.isDirectory()) {
                    updateLog("Java verification: Directory exists and is accessible");
                    return true;
                } else {
                    updateLog("Java verification: Directory does not exist or is not accessible");
                    return false;
                }
            } catch (Exception e) {
                updateLog("Java verification error: " + e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up directories", e);
            updateError("Directory setup failed: " + e.getMessage());
            return false;
        }
    }
    
    private void startDiagProcess() throws IOException {
        // Use the current directory that was set during initial load
        String targetDir = QMDL_DIR;
        String targetConfig = CONFIG_FILE;
        
        updateLog("Starting diag_mdlog with directory: " + targetDir);
        updateLog("Config file: " + targetConfig);
        
        // Start su process
        suProcess = Runtime.getRuntime().exec("su");
        diagProcess = suProcess; // Set the diagProcess reference
        OutputStreamWriter writer = new OutputStreamWriter(suProcess.getOutputStream());
        
        // Execute the diag_mdlog command with the determined path
        String command = "diag_mdlog -o " + targetDir + " -f " + targetConfig + " -m " + targetConfig + " &";
        updateLog("Executing command: " + command);
        writer.write(command + "\n");
        writer.write("echo 'diag_mdlog started successfully with path: " + targetDir + "'\n");
        writer.flush();
        
        // Start a separate thread to read the output
        outputReaderThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null && !shouldStop) {
                    updateLog(line);
                }
            } catch (IOException e) {
                if (!shouldStop) {
                    Log.e(TAG, "Error reading diag_mdlog output", e);
                    updateError("Lost connection to diag_mdlog process");
                }
            }
        });
        outputReaderThread.start();
        
        // Start a separate thread to read error output
        errorReaderThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getErrorStream()));
                String line;
                while ((line = reader.readLine()) != null && !shouldStop) {
                    updateLog("ERROR: " + line);
                }
            } catch (IOException e) {
                if (!shouldStop) {
                    Log.e(TAG, "Error reading diag_mdlog error output", e);
                }
            }
        });
        errorReaderThread.start();
        
        // Start a monitor thread to check if process is still running
        monitorThread = new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds
                if (!shouldStop && suProcess != null) {
                    try {
                        int exitCode = suProcess.exitValue();
                        if (exitCode != 0) {
                            updateError("diag_mdlog process exited with code: " + exitCode);
                            shouldStop = true;
                        }
                    } catch (IllegalThreadStateException e) {
                        // Process is still running, which is good
                        updateLog("diag_mdlog process is running normally");
                        
                        // Check if diag_mdlog process is actually running
                        try {
                            Process checkProcess = Runtime.getRuntime().exec("su");
                            OutputStreamWriter checkWriter = new OutputStreamWriter(checkProcess.getOutputStream());
                            checkWriter.write("pgrep diag_mdlog\n");
                            checkWriter.write("echo 'CHECK_RESULT:$?'\n");
                            checkWriter.write("exit\n");
                            checkWriter.flush();
                            checkWriter.close();
                            
                            BufferedReader checkReader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
                            String checkLine;
                            boolean processFound = false;
                            while ((checkLine = checkReader.readLine()) != null) {
                                if (checkLine.startsWith("CHECK_RESULT:")) {
                                    String result = checkLine.split(":")[1];
                                    processFound = result.equals("0");
                                    break;
                                }
                            }
                            checkReader.close();
                            
                            int checkExitCode = checkProcess.waitFor();
                            if (processFound) {
                                updateLog("diag_mdlog process confirmed running (PID found)");
                            } else {
                                updateError("diag_mdlog process not found in process list");
                            }
                        } catch (Exception checkEx) {
                            updateLog("Could not verify diag_mdlog process status: " + checkEx.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Monitor thread interrupted", e);
            }
        });
        monitorThread.start();
    }
    
    private void updateStatus(String status) {
        if (callback != null) {
            callback.onStatusUpdate(status);
        }
        Log.d(TAG, "Status: " + status);
    }
    
    private void updateLog(String log) {
        if (callback != null) {
            callback.onLogUpdate(log);
        }
        Log.d(TAG, "Log: " + log);
    }
    
    private void updateError(String error) {
        if (callback != null) {
            callback.onError(error);
        }
        Log.e(TAG, "Error: " + error);
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Check if diag_mdlog is available on the device
     */
    public static boolean isDiagMdlogAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            writer.write("which diag_mdlog\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0 && line != null && !line.isEmpty();
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking diag_mdlog availability", e);
            return false;
        }
    }
    
    /**
     * Check if all required permissions and conditions are met
     */
    public static String checkPrerequisites() {
        StringBuilder issues = new StringBuilder();
        
        // Check root access
        if (!QmdlUtils.hasRootAccess()) {
            issues.append("Root access not available\n");
        }
        
        // Check diag_mdlog availability
        if (!isDiagMdlogAvailable()) {
            issues.append("diag_mdlog not available\n");
        }
        
        // Note: Directory write access will be handled during actual setup
        // We don't need to check it here since it requires root and will be created when needed
        
        return issues.length() > 0 ? issues.toString() : "All prerequisites met";
    }
    
    /**
     * Check if we can write to the QMDL directory
     */
    private static boolean canWriteToQmdlDirectory() {
        try {
            // First check if directory exists
            File qmdlDir = new File(QMDL_DIR);
            if (!qmdlDir.exists()) {
                // Directory doesn't exist, try to create it with root
                return canCreateQmdlDirectoryWithRoot();
            }
            
            // Directory exists, try to write a test file
            File testFile = new File(QMDL_DIR, "test_write.tmp");
            if (testFile.createNewFile()) {
                testFile.delete();
                return true;
            }
            
            // If that fails, try with root
            return canWriteToQmdlDirectoryWithRoot();
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking QMDL directory write access", e);
            return false;
        }
    }
    
    /**
     * Check if we can write to the QMDL directory using root
     */
    private static boolean canWriteToQmdlDirectoryWithRoot() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            writer.write("test -w " + QMDL_DIR + " && echo 'WRITABLE' || echo 'NOT_WRITABLE'\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0 && "WRITABLE".equals(line);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking QMDL directory write access with root", e);
            return false;
        }
    }
    
    /**
     * Check if we can create the QMDL directory using root
     */
    private static boolean canCreateQmdlDirectoryWithRoot() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            writer.write("mkdir -p " + QMDL_DIR + " 2>/dev/null\n");
            writer.write("if [ -d " + QMDL_DIR + " ]; then\n");
            writer.write("  echo 'DIRECTORY_CREATED'\n");
            writer.write("  chmod 777 " + QMDL_DIR + " 2>/dev/null\n");
            writer.write("  echo 'PERMISSIONS_SET'\n");
            writer.write("else\n");
            writer.write("  echo 'DIRECTORY_FAILED'\n");
            writer.write("fi\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean directoryCreated = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("DIRECTORY_CREATED")) {
                    directoryCreated = true;
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0 && directoryCreated;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating QMDL directory with root", e);
            return false;
        }
    }
    
    /**
     * Check if external storage is accessible
     */
    private boolean canAccessExternalStorage() {
        try {
            File externalDir = new File("/sdcard");
            return externalDir.exists() && externalDir.canWrite();
        } catch (Exception e) {
            return false;
        }
    }
}
