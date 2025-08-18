package com.example.modiv3;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import com.example.modiv3.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements QmdlService.QmdlCallback {

    private ActivityMainBinding binding;
    private QmdlService qmdlService;
    private boolean isGathering = false;
    private Handler mainHandler;
    private StringBuilder logBuffer = new StringBuilder();
    
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

        // Service will handle permissions and setup automatically
        updateStatus("Initializing service...");
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
            
            // Start monitoring service readiness
            startServiceReadinessMonitor();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            qmdlService = null;
            isServiceBound = false;
        }
    };
    
    /**
     * Monitor service readiness and update UI accordingly
     */
    private void startServiceReadinessMonitor() {
        new Thread(() -> {
            while (qmdlService != null && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // Check every second
                    
                    if (qmdlService.isReady()) {
                        mainHandler.post(() -> {
                            updateStatus("Ready - Tap 'Start QMDL Gathering' to begin");
                            binding.toggleButton.setEnabled(true);
                        });
                        break; // Stop monitoring once ready
                    } else {
                        mainHandler.post(() -> {
                            updateStatus("Setting up service... (" + qmdlService.getSetupStatus() + ")");
                            binding.toggleButton.setEnabled(false);
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    

    


    private void startQmdlGathering() {
        binding.toggleButton.setEnabled(false);
        updateStatus("Checking service readiness...");
        
        // Check if service is bound
        if (qmdlService == null) {
            mainHandler.post(() -> {
                updateStatus("Service not ready, please wait...");
                binding.toggleButton.setEnabled(true);
            });
            return;
        }
        
        // Check if service is ready to start
        if (!qmdlService.isReady()) {
            mainHandler.post(() -> {
                updateStatus("Service setup not complete yet, please wait...");
                updateLog("Setup status: " + qmdlService.getSetupStatus());
                binding.toggleButton.setEnabled(true);
            });
            return;
        }
        
        updateStatus("Starting QMDL gathering...");
        
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
                    
                    updateStatus("QMDL gathering stopped successfully");
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