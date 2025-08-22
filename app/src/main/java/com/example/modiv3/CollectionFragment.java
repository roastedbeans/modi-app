package com.example.modiv3;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.modiv3.databinding.FragmentCollectionBinding;

public class CollectionFragment extends Fragment implements QmdlService.QmdlCallback {

    private FragmentCollectionBinding binding;
    private QmdlService qmdlService;
    private PythonIntegrationService pythonService;
    private boolean isGathering = false;
    private Handler mainHandler;
    private StringBuilder logBuffer = new StringBuilder();
    private boolean isServiceBound = false;
    private boolean isPythonServiceBound = false;

    private static final String LOG_DIRECTORY = "/sdcard/diag_logs";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCollectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());

        // Set up the toggle button
        binding.toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isGathering) {
                    stopQmdlGathering();
                } else {
                    startQmdlGathering();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start and bind to the QMDL service
        Intent serviceIntent = new Intent(getContext(), QmdlService.class);
        getContext().startService(serviceIntent);
        getContext().bindService(serviceIntent, serviceConnection, getContext().BIND_AUTO_CREATE);

        // Start and bind to the Python integration service
        Intent pythonServiceIntent = new Intent(getContext(), PythonIntegrationService.class);
        getContext().startService(pythonServiceIntent);
        getContext().bindService(pythonServiceIntent, pythonServiceConnection, getContext().BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            getContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (isPythonServiceBound) {
            getContext().unbindService(pythonServiceConnection);
            isPythonServiceBound = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (qmdlService != null && isGathering) {
            qmdlService.stopQmdlGathering();
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            QmdlService.LocalBinder localBinder = (QmdlService.LocalBinder) binder;
            qmdlService = localBinder.getService();
            qmdlService.setCallback(CollectionFragment.this);
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
    public void analyzeLogsWithPython() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Running Python log analysis...");
            qmdlService.analyzeLogsWithPython();
        } else {
            updateLog("Python integration not available");
        }
    }

    public void generatePythonReport() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Generating Python analysis report...");
            qmdlService.generatePythonReport();
        } else {
            updateLog("Python integration not available");
        }
    }

    public void createPythonVisualizations() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Creating Python visualizations...");
            qmdlService.createPythonVisualizations();
        } else {
            updateLog("Python integration not available");
        }
    }

    public void extractErrorPatternsWithPython() {
        if (qmdlService != null && qmdlService.isPythonIntegrationAvailable()) {
            updateLog("Extracting error patterns with Python...");
            qmdlService.extractErrorPatternsWithPython();
        } else {
            updateLog("Python integration not available");
        }
    }
}
