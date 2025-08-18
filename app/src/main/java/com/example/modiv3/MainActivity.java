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
import com.example.modiv3.QmdlParserManager;
import com.example.modiv3.QmdlParserManager.ParseResult;
import com.example.modiv3.QmdlParserManager.DiagMessage;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

public class MainActivity extends AppCompatActivity implements QmdlService.QmdlCallback {
    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private QmdlService qmdlService;
    private QmdlParserManager qmdlParser;
    private boolean isGathering = false;
    private Handler mainHandler;
    private StringBuilder logBuffer = new StringBuilder();
    
    private static final String LOG_DIRECTORY = "/sdcard/diag_logs"; // Fixed directory
    private boolean isServiceBound = false;
    
    // Parser state
    private List<ParseResult> lastParseResults = null;
    private volatile boolean isParsingInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize QMDL parser with enhanced features
        qmdlParser = new QmdlParserManager();
        
        // Start and bind to the service to get proper Context
        Intent serviceIntent = new Intent(this, QmdlService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        setupButtons();
        
        // Service will handle permissions and setup automatically
        updateStatus("Initializing service...");
        
        // Test the parser after a short delay
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                testParserWithFile();
            }
        }, 3000); // 3 second delay
    }
    
    private void setupButtons() {
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

        // Set up parse button
        binding.parseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isParsingInProgress) {
                    parseQmdlFiles();
                } else {
                    updateLog("Parsing already in progress, please wait...");
                }
            }
        });

        // Test parser with a specific file
        binding.testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isParsingInProgress) {
                    testParserWithFile();
                } else {
                    updateLog("Parsing already in progress, please wait...");
                }
            }
        });

        // Export to JSON button
        binding.exportJsonButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportToJson();
            }
        });
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
                            binding.parseButton.setEnabled(true);
                            binding.testButton.setEnabled(true);
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
        try {
            final int scrollAmount = binding.logText.getLayout().getLineTop(binding.logText.getLineCount()) - binding.logText.getHeight();
            if (scrollAmount > 0) {
                binding.logText.scrollTo(0, scrollAmount);
            }
        } catch (Exception e) {
            // Ignore layout exceptions
        }
    }

    /**
     * Test parser with a specific file
     */
    private void testParserWithFile() {
        if (isParsingInProgress) {
            updateLog("Parsing already in progress, please wait...");
            return;
        }
        
        updateStatus("Testing parser with specific file...");
        isParsingInProgress = true;
        binding.testButton.setEnabled(false);
        
                 new Thread(() -> {
             try {
                 // First, look for existing QMDL files using StorageHelper
                 List<File> qmdlFilesList = StorageHelper.getQmdlFiles(LOG_DIRECTORY);
                 
                 // If no files found in primary directory, try alternatives
                 if (qmdlFilesList.isEmpty()) {
                     mainHandler.post(() -> updateLog("No files in primary directory, searching alternatives..."));
                     qmdlFilesList = StorageHelper.findQmdlFilesAnywhere();
                 }
                 
                 String testFile = null;
                 
                 if (!qmdlFilesList.isEmpty()) {
                     File[] qmdlFiles = qmdlFilesList.toArray(new File[0]);
                     // Use the smallest existing QMDL file for testing
                     File smallestFile = qmdlFiles[0];
                     for (File file : qmdlFiles) {
                         if (file.length() < smallestFile.length() && file.length() > 1024) { // At least 1KB
                             smallestFile = file;
                         }
                     }
                     testFile = smallestFile.getAbsolutePath();
                     final String existingFile = testFile;
                     final long fileSize = smallestFile.length();
                     mainHandler.post(() -> updateLog("Using existing QMDL file: " + existingFile + 
                         " (size: " + String.format("%.2f MB", fileSize / (1024.0 * 1024.0)) + ")"));
                 } else {
                     // Try to create a test file in app's private directory
                     String privateTestFile = getFilesDir() + "/test_sample.qmdl";
                     mainHandler.post(() -> updateLog("No existing QMDL files found, creating sample in app directory..."));
                     boolean created = QmdlTestUtils.createSampleQmdlFile(privateTestFile, 50); // Smaller sample
                     
                     if (created) {
                         testFile = privateTestFile;
                         final String createdFile = testFile;
                         mainHandler.post(() -> updateLog("Created sample test file: " + createdFile));
                     } else {
                         mainHandler.post(() -> {
                             updateStatus("No QMDL files found for testing");
                             updateLog("No QMDL files found in " + LOG_DIRECTORY + " and failed to create sample");
                             isParsingInProgress = false;
                             binding.testButton.setEnabled(true);
                         });
                         return;
                     }
                 }
                
                final String finalTestFile = testFile;
                
                // Verify file before parsing
                if (!QmdlTestUtils.verifyQmdlFile(finalTestFile)) {
                    mainHandler.post(() -> {
                        updateStatus("Test file verification failed");
                        updateLog("Test file is not valid: " + finalTestFile);
                        isParsingInProgress = false;
                        binding.testButton.setEnabled(true);
                    });
                    return;
                }
                
                ParseResult result = qmdlParser.parseQmdlFile(finalTestFile, new QmdlParserManager.ProgressCallback() {
                    @Override
                    public void onFileStarted(String fileName, long fileSize) {
                        mainHandler.post(() -> updateLog("Starting parse: " + fileName + " (size: " + (fileSize / 1024 / 1024) + " MB)"));
                    }
                    
                    @Override
                    public void onChunkingProgress(String fileName, int chunksCompleted, int totalChunks) {
                        mainHandler.post(() -> updateLog("Chunking progress: " + chunksCompleted + "/" + totalChunks));
                    }
                    
                    @Override
                    public void onParsingProgress(String fileName, long bytesProcessed, long totalBytes) {
                        int progress = (int) ((bytesProcessed * 100) / totalBytes);
                        mainHandler.post(() -> updateStatus("Parsing: " + progress + "%"));
                    }
                    
                    @Override
                    public void onFileCompleted(String fileName, int messageCount, long parseTimeMs) {
                        mainHandler.post(() -> updateLog("Completed: " + messageCount + " messages in " + parseTimeMs + " ms"));
                    }
                    
                    @Override
                    public void onError(String fileName, String error, Exception e) {
                        mainHandler.post(() -> updateLog("Error: " + error));
                        Log.e(TAG, "Parse error for " + fileName, e);
                    }
                    
                    @Override
                    public void onAllCompleted(QmdlParserManager.ParseSummary summary) {
                        // Not used for single file parsing
                    }
                });
                
                mainHandler.post(() -> {
                    updateStatus("Test parsing completed");
                    updateLog("=== Test Parse Result ===");
                    updateLog("File: " + finalTestFile);
                    updateLog("Success: " + result.success);
                    updateLog("Total messages: " + result.totalMessages);
                    updateLog("Parse time: " + result.parseTimeMs + " ms");
                    
                    if (!result.success) {
                        updateLog("Error: " + result.errorMessage);
                    } else {
                        updateLog("Messages found: " + result.messages.size());
                        for (int i = 0; i < Math.min(3, result.messages.size()); i++) {
                            DiagMessage msg = result.messages.get(i);
                            updateLog("Message " + (i+1) + ": " + msg.toString());
                        }
                        
                        if (result.messages.size() > 3) {
                            updateLog("... and " + (result.messages.size() - 3) + " more messages");
                        }
                    }
                    
                    isParsingInProgress = false;
                    binding.testButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Test parsing exception", e);
                mainHandler.post(() -> {
                    updateStatus("Test parsing failed: " + e.getMessage());
                    updateLog("Test error: " + e.getMessage());
                    isParsingInProgress = false;
                    binding.testButton.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Parse QMDL files in the diag_logs directory
     */
    private void parseQmdlFiles() {
        if (isParsingInProgress) {
            updateLog("Parsing already in progress, please wait...");
            return;
        }
        
        updateStatus("Starting QMDL file parsing...");
        binding.parseButton.setEnabled(false);
        isParsingInProgress = true;
        
        new Thread(() -> {
            try {
                // Parse all QMDL files in the directory
                List<ParseResult> results = qmdlParser.parseQmdlDirectory(LOG_DIRECTORY, 
                    new QmdlParserManager.ProgressCallback() {
                        @Override
                        public void onFileStarted(String fileName, long fileSize) {
                            mainHandler.post(() -> {
                                updateStatus("Parsing: " + fileName);
                                updateLog("Starting: " + fileName + " (size: " + (fileSize / 1024 / 1024) + " MB)");
                            });
                        }
                        
                        @Override
                        public void onChunkingProgress(String fileName, int chunksCompleted, int totalChunks) {
                            mainHandler.post(() -> updateLog("Chunking " + fileName + ": " + chunksCompleted + "/" + totalChunks));
                        }
                        
                        @Override
                        public void onParsingProgress(String fileName, long bytesProcessed, long totalBytes) {
                            int progress = (int) ((bytesProcessed * 100) / totalBytes);
                            mainHandler.post(() -> updateStatus("Parsing " + fileName + ": " + progress + "%"));
                        }
                        
                        @Override
                        public void onFileCompleted(String fileName, int messageCount, long parseTimeMs) {
                            mainHandler.post(() -> updateLog("Completed " + fileName + ": " + messageCount + " messages in " + parseTimeMs + " ms"));
                        }
                        
                        @Override
                        public void onError(String fileName, String error, Exception e) {
                            mainHandler.post(() -> updateLog("Error in " + fileName + ": " + error));
                            Log.e(TAG, "Parse error for " + fileName, e);
                        }
                        
                        @Override
                        public void onAllCompleted(QmdlParserManager.ParseSummary summary) {
                            mainHandler.post(() -> {
                                updateStatus("All parsing completed");
                                updateLog("=== Final Summary ===");
                                updateLog("Total files: " + summary.totalFiles);
                                updateLog("Successful: " + summary.successfulFiles);
                                updateLog("Failed: " + summary.failedFiles);
                                updateLog("Total messages: " + summary.totalMessages);
                                updateLog("Total time: " + (summary.totalParseTime / 1000.0) + " seconds");
                            });
                        }
                    });
                
                // Store results for JSON export
                lastParseResults = results;
                
                // Get summary
                String summary = qmdlParser.getParseSummary(results);
                
                mainHandler.post(() -> {
                    updateStatus("QMDL parsing completed");
                    updateLog("=== QMDL Parse Results ===");
                    updateLog(summary);
                    
                    // Enable JSON export if we have results
                    if (!results.isEmpty()) {
                        boolean hasSuccessfulResults = false;
                        for (ParseResult result : results) {
                            if (result.success && !result.messages.isEmpty()) {
                                hasSuccessfulResults = true;
                                break;
                            }
                        }
                        
                        if (hasSuccessfulResults) {
                            binding.exportJsonButton.setEnabled(true);
                            updateLog("JSON export now available - tap 'Export to JSON' button");
                        } else {
                            updateLog("No successful parse results for JSON export");
                        }
                    }
                    
                    binding.parseButton.setEnabled(true);
                    isParsingInProgress = false;
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Directory parsing exception", e);
                mainHandler.post(() -> {
                    updateStatus("QMDL parsing failed: " + e.getMessage());
                    updateLog("Parse error: " + e.getMessage());
                    binding.parseButton.setEnabled(true);
                    isParsingInProgress = false;
                });
            }
        }).start();
    }
    
    /**
     * Export parsed results to JSON
     */
    private void exportToJson() {
        if (lastParseResults == null || lastParseResults.isEmpty()) {
            updateLog("No parse results available. Please parse QMDL files first.");
            return;
        }
        
        updateStatus("Exporting to JSON...");
        binding.exportJsonButton.setEnabled(false);
        
        new Thread(() -> {
            try {
                // Collect all messages from all successful parse results
                List<com.example.modiv3.DiagMessage> allMessages = new ArrayList<>();
                
                for (ParseResult result : lastParseResults) {
                    if (result.success) {
                        // Convert legacy messages to new format for JSON export
                        for (DiagMessage legacyMsg : result.messages) {
                            com.example.modiv3.DiagMessage newMsg = new com.example.modiv3.DiagMessage.Builder()
                                .setLogId(legacyMsg.logId)
                                .setTimestamp(legacyMsg.timestamp)
                                .setTechnology(legacyMsg.technology)
                                .setLayer(legacyMsg.layer)
                                .setParsedContent(legacyMsg.parsedContent)
                                .build();
                            allMessages.add(newMsg);
                        }
                    }
                }
                
                if (allMessages.isEmpty()) {
                    mainHandler.post(() -> {
                        updateLog("No messages to export");
                        binding.exportJsonButton.setEnabled(true);
                    });
                    return;
                }
                
                // Create output file path
                String timestamp = String.valueOf(System.currentTimeMillis());
                String outputPath = LOG_DIRECTORY + "/qmdl_export_" + timestamp + ".json";
                
                boolean success = qmdlParser.convertToJson(allMessages, outputPath);
                
                mainHandler.post(() -> {
                    if (success) {
                        updateStatus("JSON export completed");
                        updateLog("Exported " + allMessages.size() + " messages to: " + outputPath);
                        
                        // Check file size
                        File jsonFile = new File(outputPath);
                        if (jsonFile.exists()) {
                            double fileSizeMB = jsonFile.length() / (1024.0 * 1024.0);
                            updateLog(String.format("JSON file size: %.2f MB", fileSizeMB));
                        }
                    } else {
                        updateStatus("JSON export failed");
                        updateLog("Failed to export to JSON file");
                    }
                    
                    binding.exportJsonButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "JSON export exception", e);
                mainHandler.post(() -> {
                    updateStatus("JSON export error: " + e.getMessage());
                    updateLog("JSON export error: " + e.getMessage());
                    binding.exportJsonButton.setEnabled(true);
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop any ongoing parsing
        if (qmdlParser != null) {
            qmdlParser.stop();
        }
        
        if (qmdlService != null && isGathering) {
            qmdlService.stopQmdlGathering();
        }
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        // Cleanup parser resources
        if (qmdlParser != null) {
            qmdlParser.shutdown();
        }
    }
}
