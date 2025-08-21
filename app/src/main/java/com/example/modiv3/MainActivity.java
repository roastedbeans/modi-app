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
    private PythonIntegrationService pythonService;
    private boolean isGathering = false;
    private Handler mainHandler;
    private StringBuilder logBuffer = new StringBuilder();

    private static final String LOG_DIRECTORY = "/sdcard/diag_logs"; // Fixed directory
    private boolean isServiceBound = false;
    private boolean isPythonServiceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainHandler = new Handler(Looper.getMainLooper());
        
        // Start and bind to the QMDL service to get proper Context
        Intent serviceIntent = new Intent(this, QmdlService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        // Start and bind to the Python integration service
        Intent pythonServiceIntent = new Intent(this, PythonIntegrationService.class);
        startService(pythonServiceIntent);
        bindService(pythonServiceIntent, pythonServiceConnection, BIND_AUTO_CREATE);

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
        updateLog("Initializing service...");
    }
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            QmdlService.LocalBinder localBinder = (QmdlService.LocalBinder) binder;
            qmdlService = localBinder.getService();
            qmdlService.setCallback(MainActivity.this);
            qmdlService.setLogDirectory(LOG_DIRECTORY);
            isServiceBound = true;
            updateLog("QMDL Service connected successfully");

            // Connect Python service to QMDL service if available
            if (pythonService != null) {
                qmdlService.setPythonService(pythonService);
            }

            // Start monitoring service readiness
            startServiceReadinessMonitor();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            qmdlService = null;
            isServiceBound = false;
        }
    };

    private ServiceConnection pythonServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            PythonIntegrationService.LocalBinder localBinder = (PythonIntegrationService.LocalBinder) binder;
            pythonService = localBinder.getService();
            isPythonServiceBound = true;
            updateLog("Python Service connected successfully");

            // Connect Python service to QMDL service if available
            if (qmdlService != null) {
                qmdlService.setPythonService(pythonService);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            pythonService = null;
            isPythonServiceBound = false;
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
                        
                            binding.toggleButton.setEnabled(true);
                        });
                        break; // Stop monitoring once ready
                    } else {
                        mainHandler.post(() -> {
                          
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
        
        // Check if service is bound
        if (qmdlService == null) {
            mainHandler.post(() -> {
                binding.toggleButton.setEnabled(true);
            });
            return;
        }
        
        // Check if service is ready to start
        if (!qmdlService.isReady()) {
            mainHandler.post(() -> {
                updateLog("Setup status: " + qmdlService.getSetupStatus());
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
                   
                }
            });
        }).start();
    }

    private void stopQmdlGathering() {
        binding.toggleButton.setEnabled(false);
        
        // Check if service is bound
        if (qmdlService == null) {
            mainHandler.post(() -> {
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
                } else {
                    binding.toggleButton.setEnabled(true);
                }
            });
        }).start();
    }

    @Override
    public void onLogUpdate(String log) {
        mainHandler.post(() -> updateLog(log));
    }

    @Override
    public void onError(String error) {
        mainHandler.post(() -> {
            updateLog("Error: " + error);
        });
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
        if (binding.logText.getLayout() != null) {
            final int scrollAmount = binding.logText.getLayout().getLineTop(binding.logText.getLineCount()) - binding.logText.getHeight();
            if (scrollAmount > 0) {
                binding.logText.scrollTo(0, scrollAmount);
            }
        }
    }

    // Python Integration Methods
    private void analyzeLogsWithPython() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Running Python log analysis...");
            qmdlService.analyzeLogsWithPython();
        } else {
            updateLog("Python integration not available");
        }
    }

    private void generatePythonReport() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Generating Python analysis report...");
            qmdlService.generatePythonReport();
        } else {
            updateLog("Python integration not available");
        }
    }

    private void createPythonVisualizations() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Creating Python visualizations...");
            qmdlService.createPythonVisualizations();
        } else {
            updateLog("Python integration not available");
        }
    }

    private void extractErrorPatternsWithPython() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Extracting error patterns with Python...");
            qmdlService.extractErrorPatternsWithPython();
        } else {
            updateLog("Python integration not available");
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
        if (isPythonServiceBound) {
            unbindService(pythonServiceConnection);
            isPythonServiceBound = false;
        }
    }
}