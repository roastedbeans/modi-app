package com.example.modiv3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.example.modiv3.databinding.ActivityMainBinding;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity implements QmdlService.QmdlCallback, RootManager.RootCallback {

    private ActivityMainBinding binding;
    private QmdlService qmdlService;
    private boolean isGathering = false;
    private boolean hasRootAccess = false;
    private boolean hasStoragePermission = false;
    private Handler mainHandler;
    private StringBuilder logBuffer = new StringBuilder();
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE
    };
    
    private static final String LOG_DIRECTORY = "/sdcard/diag_logs"; // Fixed directory
    private boolean isServiceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainHandler = new Handler(Looper.getMainLooper());
        
        // Start and bind to the service to get proper Context
        Intent serviceIntent = new Intent(this, QmdlService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        // Set up the toggle button
        binding.toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isGathering) {
                    stopQmdlGathering();
                } else {
                    // Permissions are already checked during initial load, so we can proceed directly
                    startQmdlGathering();
                }
            }
        });

        // Check and request permissions on first load
        checkAndRequestPermissions();
    }
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            QmdlService.LocalBinder localBinder = (QmdlService.LocalBinder) binder;
            qmdlService = localBinder.getService();
            qmdlService.setCallback(MainActivity.this);
            qmdlService.setLogDirectory(LOG_DIRECTORY);
            isServiceBound = true;
            updateStatus("Service connected successfully");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            qmdlService = null;
            isServiceBound = false;
        }
    };
    

    
    private void checkAndRequestPermissions() {
        updateStatus("Checking permissions...");
        
        // Check basic storage permissions first
        boolean basicPermissionsGranted = true;
        String[] basicPermissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        
        for (String permission : basicPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                basicPermissionsGranted = false;
                break;
            }
        }
        
        if (basicPermissionsGranted) {
            hasStoragePermission = true;
            updateStatus("Basic storage permissions granted - Requesting root access...");
            requestRootAccess();
        } else {
            updateStatus("Requesting storage permissions...");
            requestStoragePermissions();
        }
    }
    
    private void requestStoragePermissions() {
        updateStatus("Please grant storage permissions to continue...");
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                hasStoragePermission = true;
                updateStatus("Storage permissions granted - Requesting root access...");
                requestRootAccess();
            } else {
                hasStoragePermission = false;
                updateStatus("Storage permissions denied - Please grant permissions in Settings");
                Toast.makeText(this, "Storage permissions are required. Please grant them in Settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startQmdlGathering() {
        // Permissions are already checked during initial load, so we can proceed directly
        binding.toggleButton.setEnabled(false);
        updateStatus("Starting QMDL gathering...");
        
        // Check if service is bound
        if (qmdlService == null) {
            mainHandler.post(() -> {
                updateStatus("Service not ready, please wait...");
                binding.toggleButton.setEnabled(true);
            });
            return;
        }
        
        // Run the service operation in a background thread
        new Thread(() -> {
            boolean success = qmdlService.startQmdlGathering();
            
            mainHandler.post(() -> {
                if (success) {
                    isGathering = true;
                    binding.toggleButton.setText("Stop QMDL Gathering");
                    binding.toggleButton.setEnabled(true);
                } else {
                    binding.toggleButton.setEnabled(true);
                    updateStatus("Failed to start QMDL gathering");
                }
            });
        }).start();
    }

    private void stopQmdlGathering() {
        binding.toggleButton.setEnabled(false);
        updateStatus("Stopping QMDL gathering...");
        
        // Check if service is bound
        if (qmdlService == null) {
            mainHandler.post(() -> {
                updateStatus("Service not ready, please wait...");
                binding.toggleButton.setEnabled(true);
            });
            return;
        }
        
        // Run the service operation in a background thread
        new Thread(() -> {
            boolean success = qmdlService.stopQmdlGathering();
            
            mainHandler.post(() -> {
                if (success) {
                    isGathering = false;
                    binding.toggleButton.setText("Start QMDL Gathering");
                    binding.toggleButton.setEnabled(true);
                    
                    // Backup the QMDL files
                    updateStatus("Backing up QMDL files...");
                    new Thread(() -> {
                        boolean backupSuccess = QmdlUtils.backupQmdlFiles(MainActivity.this);
                        mainHandler.post(() -> {
                            if (backupSuccess) {
                                long fileSize = QmdlUtils.getQmdlFilesSize();
                                String sizeStr = QmdlUtils.formatFileSize(fileSize);
                                String[] filesList = QmdlUtils.getQmdlFilesList();
                                String filesInfo = filesList.length > 0 ? " (" + filesList.length + " files)" : "";
                                updateStatus("QMDL gathering stopped. Files backed up (" + sizeStr + filesInfo + ")");
                            } else {
                                String[] filesList = QmdlUtils.getQmdlFilesList();
                                if (filesList.length > 0) {
                                    updateStatus("QMDL gathering stopped. Backup failed but files exist: " + filesList.length + " files");
                                } else {
                                    updateStatus("QMDL gathering stopped. No files found to backup");
                                }
                            }
                        });
                    }).start();
                } else {
                    binding.toggleButton.setEnabled(true);
                    updateStatus("Failed to stop QMDL gathering");
                }
            });
        }).start();
    }

    @Override
    public void onStatusUpdate(String status) {
        mainHandler.post(() -> updateStatus(status));
    }

    @Override
    public void onLogUpdate(String log) {
        mainHandler.post(() -> updateLog(log));
    }

    @Override
    public void onError(String error) {
        mainHandler.post(() -> {
            updateStatus("Error: " + error);
        });
    }
    
    // RootManager callback methods
    @Override
    public void onRootGranted() {
        mainHandler.post(() -> {
            hasRootAccess = true;
            binding.toggleButton.setEnabled(true);
            updateStatus("Root access granted - Checking diag_mdlog availability...");
            
            // Check if diag_mdlog is available
            checkDiagMdlogAvailability();
        });
    }
    
    @Override
    public void onRootDenied() {
        mainHandler.post(() -> {
            hasRootAccess = false;
            binding.toggleButton.setEnabled(true);
            updateStatus("Root access denied - Please grant root permissions");
        });
    }
    
    @Override
    public void onRootError(String error) {
        mainHandler.post(() -> {
            hasRootAccess = false;
            binding.toggleButton.setEnabled(true);
            updateStatus("Root access error: " + error);
        });
    }

    private void requestRootAccess() {
        updateStatus("Requesting root access - Please grant in SuperSU/Magisk...");
        binding.toggleButton.setEnabled(false);
        
        RootManager.requestRootAccess(this, this);
    }
    
    private void checkDiagMdlogAvailability() {
        new Thread(() -> {
            // Set the QmdlUtils directory first
            QmdlUtils.setQmdlSourceDirectory(LOG_DIRECTORY);
            
            // First create the directory if it doesn't exist
            boolean directoryCreated = createModiLogsDirectory();
            
            // Then check prerequisites
            String prerequisites = QmdlService.checkPrerequisites();
            mainHandler.post(() -> {
                if (prerequisites.equals("All prerequisites met")) {
                    if (directoryCreated) {
                        updateStatus("Directory created and ready - Tap 'Start QMDL Gathering' to begin");
                    } else {
                        updateStatus("Ready - Tap 'Start QMDL Gathering' to begin");
                    }
                    binding.toggleButton.setEnabled(true);
                } else {
                    updateStatus("Prerequisites check: " + prerequisites);
                    // Don't show toast since this is just informational
                }
            });
        }).start();
    }
    
    private boolean createModiLogsDirectory() {
        try {
            mainHandler.post(() -> updateStatus("Checking log directory..."));
            
            // Check if the directory exists
            File modiLogsDir = new File(LOG_DIRECTORY);
            if (!modiLogsDir.exists()) {
                // Create the directory
                mainHandler.post(() -> updateStatus("Creating log directory..."));
                return createModiLogsDirectoryWithRoot();
            } else {
                mainHandler.post(() -> updateStatus("Directory already exists"));
                return true;
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking diag_logs directory", e);
            mainHandler.post(() -> updateStatus("Error checking directory"));
            return false;
        }
    }
    

    

    
    private void continueWithPrerequisitesCheck() {
        new Thread(() -> {
            // Set the QmdlUtils directory
            QmdlUtils.setQmdlSourceDirectory(LOG_DIRECTORY);
            
            String prerequisites = QmdlService.checkPrerequisites();
            mainHandler.post(() -> {
                if (prerequisites.equals("All prerequisites met")) {
                    updateStatus("Ready - Tap 'Start QMDL Gathering' to begin");
                    binding.toggleButton.setEnabled(true);
                } else {
                    updateStatus("Prerequisites check: " + prerequisites);
                }
            });
        }).start();
    }
    
    private boolean createModiLogsDirectoryWithRoot() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            
            writer.write("mkdir -p " + LOG_DIRECTORY + " 2>/dev/null\n");
            writer.write("if [ -d " + LOG_DIRECTORY + " ]; then\n");
            writer.write("  echo 'DIRECTORY_CREATED'\n");
            writer.write("  chmod 777 " + LOG_DIRECTORY + " 2>/dev/null\n");
            writer.write("  echo 'PERMISSIONS_SET'\n");
            writer.write("  ls -la " + LOG_DIRECTORY + "\n");
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
                    mainHandler.post(() -> updateStatus("Directory created with root access"));
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            if (exitCode == 0 && directoryCreated) {
                mainHandler.post(() -> updateStatus("Directory setup completed successfully"));
                return true;
            } else {
                mainHandler.post(() -> updateStatus("Failed to create directory with root access"));
                return false;
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error creating directory with root", e);
            updateStatus("Error creating directory with root access");
            return false;
        }
    }
    
    private void updateStatus(String status) {
        binding.statusText.setText("Status: " + status);
    }

    private void updateLog(String log) {
        logBuffer.append(log).append("\n");
        
        // Keep only the last 1000 lines to prevent memory issues
        String[] lines = logBuffer.toString().split("\n");
        if (lines.length > 1000) {
            StringBuilder newBuffer = new StringBuilder();
            for (int i = lines.length - 1000; i < lines.length; i++) {
                newBuffer.append(lines[i]).append("\n");
            }
            logBuffer = newBuffer;
        }
        
        binding.logText.setText(logBuffer.toString());
        
        // Auto-scroll to bottom
        final int scrollAmount = binding.logText.getLayout().getLineTop(binding.logText.getLineCount()) - binding.logText.getHeight();
        if (scrollAmount > 0) {
            binding.logText.scrollTo(0, scrollAmount);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (qmdlService != null && isGathering) {
            qmdlService.stopQmdlGathering();
        }
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}