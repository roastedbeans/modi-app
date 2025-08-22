package com.example.modiv3;

import android.app.AlertDialog;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.modiv3.databinding.FragmentLoggerBinding;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoggerFragment extends Fragment implements PythonIntegrationService.PythonCallback {

    private FragmentLoggerBinding binding;
    private PythonIntegrationService pythonService;
    private boolean isPythonServiceBound = false;
    private Handler mainHandler;
    private StringBuilder pythonLogBuffer = new StringBuilder();
    private SignalingCardAdapter signalingCardAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoggerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());

        // Set up RecyclerView for signaling cards
        signalingCardAdapter = new SignalingCardAdapter();
        binding.signalingCardsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.signalingCardsRecycler.setAdapter(signalingCardAdapter);
        
        // Set up card click listener
        signalingCardAdapter.setOnCardClickListener(this::showSignalingDialog);

        // Set up QMDL reader buttons
        binding.readQmdlButton.setOnClickListener(v -> readQmdlFile());
        binding.clearLogButton.setOnClickListener(v -> clearLogs());

        updateLog("Logger initialized - QMDL reader ready (20MB+ files only)");
    }

    @Override
    public void onResume() {
        super.onResume();
        // Bind to Python service
        Intent pythonServiceIntent = new Intent(getContext(), PythonIntegrationService.class);
        getContext().startService(pythonServiceIntent);
        getContext().bindService(pythonServiceIntent, pythonServiceConnection, getContext().BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isPythonServiceBound) {
            getContext().unbindService(pythonServiceConnection);
            isPythonServiceBound = false;
        }
    }

    private ServiceConnection pythonServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            PythonIntegrationService.LocalBinder localBinder = (PythonIntegrationService.LocalBinder) binder;
            pythonService = localBinder.getService();
            pythonService.setCallback(LoggerFragment.this);
            isPythonServiceBound = true;
            updateLog("Python Service connected to Logger");

            // Update button states
            updateButtonStates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            pythonService = null;
            isPythonServiceBound = false;
            updateLog("Python Service disconnected from Logger");

            // Update button states
            updateButtonStates();
        }
    };

    private void updateButtonStates() {
        boolean isPythonReady = isPythonServiceBound && pythonService != null && pythonService.isPythonReady();

        binding.readQmdlButton.setEnabled(isPythonReady);
        binding.clearLogButton.setEnabled(true); // Clear button is always available
    }



    private void readQmdlFile() {
        if (pythonService != null && pythonService.isPythonReady()) {
            updateLog("=== Reading QMDL File ===");
            updateLog("Using first available QMDL file from previous scan...");
            
            // Use a known QMDL file path from our previous successful scans
            // We know from the logs that these files exist and are >= 20MB
            String[] knownQmdlFiles = {
                "/sdcard/diag_logs/diag_log_20250821_1536231755758183536.qmdl",
            };
            
            // Try the first known QMDL file
            String filePath = knownQmdlFiles[0];
            updateLog("Attempting to read QMDL file: " + filePath);
            updateLog("Note: Using scat FileIO approach with Python");
            pythonService.readQmdlFile(filePath);
        } else {
            updateLog("Python service not available");
        }
    }

    @Override
    public void onAnalysisComplete(String result) {
        mainHandler.post(() -> {
            try {
                // Try to parse as JSON to see if it's a structured result
                if (result.startsWith("{")) {
                    org.json.JSONObject jsonResult = new org.json.JSONObject(result);
                    
                    // Check if it's a QMDL file listing result
                    if (jsonResult.has("qmdl_files_found")) {
                        int fileCount = jsonResult.getInt("qmdl_files_found");
                        String directory = jsonResult.getString("directory");
                        updateLog("✓ Directory scan complete - " + directory + ": " + fileCount + " files (≥20MB)");
                        
                        if (jsonResult.has("files")) {
                            org.json.JSONArray files = jsonResult.getJSONArray("files");
                            for (int i = 0; i < files.length(); i++) {
                                org.json.JSONObject file = files.getJSONObject(i);
                                String fileName = new File(file.getString("path")).getName();
                                long fileSize = file.getLong("size");
                                String fileSizeMB = String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
                                updateLog("  📄 " + fileName + " (" + fileSizeMB + ")");
                            }
                        }
                    }
                    // Check if it's a QMDL file content result (new hex-based format)
                                else if (jsonResult.has("metadata")) {
                org.json.JSONObject metadata = jsonResult.getJSONObject("metadata");
                updateLog("✓ QMDL File processed successfully - Cellular data extracted:");

                if (metadata.has("file_path")) {
                    updateLog("  📁 File: " + metadata.getString("file_path"));
                }
                if (metadata.has("total_bytes")) {
                    long totalBytes = metadata.getLong("total_bytes");
                    updateLog("  📊 Total bytes read: " + totalBytes + " (" + String.format("%.1f MB", totalBytes / (1024.0 * 1024.0)) + ")");
                }

                // Display SCAT cellular data extraction statistics
                if (metadata.has("scat_extraction_stats")) {
                    org.json.JSONObject stats = metadata.getJSONObject("scat_extraction_stats");
                    updateLog("  📡 SCAT Cellular Data Extraction:");
                    updateLog("    Total packets processed: " + stats.getInt("total_packets"));
                    updateLog("    ✅ Successfully parsed by SCAT: " + stats.getInt("parsed_packets"));
                    updateLog("    📶 GSM data extracted: " + stats.getInt("gsm_data_extracted"));
                    updateLog("    📶 UMTS data extracted: " + stats.getInt("umts_data_extracted"));
                    updateLog("    📶 LTE data extracted: " + stats.getInt("lte_data_extracted"));
                    updateLog("    📶 NR (5G) data extracted: " + stats.getInt("nr_data_extracted"));
                    updateLog("    💬 System messages: " + stats.getInt("system_messages"));
                    updateLog("    📢 Events extracted: " + stats.getInt("events_extracted"));
                }

                // Display current cellular state
                if (metadata.has("cellular_state")) {
                    org.json.JSONObject cellularState = metadata.getJSONObject("cellular_state");
                    updateLog("  📡 Current Cellular State:");
                    
                    if (cellularState.has("lte")) {
                        org.json.JSONObject lte = cellularState.getJSONObject("lte");
                        updateLog("    📶 LTE: " + lte.getString("cell_id") + ", EARFCN " + lte.getInt("earfcn") + " (" + lte.getString("band") + ")");
                    }
                    
                    if (cellularState.has("gsm")) {
                        org.json.JSONObject gsm = cellularState.getJSONObject("gsm");
                        updateLog("    📶 GSM: " + gsm.getString("cell_id") + ", ARFCN " + gsm.getInt("arfcn") + " (" + gsm.getString("band") + ")");
                    }
                    
                    if (cellularState.has("umts")) {
                        org.json.JSONObject umts = cellularState.getJSONObject("umts");
                        updateLog("    📶 UMTS: " + umts.getString("cell_id") + ", UARFCN " + umts.getInt("uarfcn") + " (" + umts.getString("band") + ")");
                    }
                }

                // Display sample cellular data
                if (metadata.has("sample_data")) {
                    org.json.JSONArray sampleData = metadata.getJSONArray("sample_data");
                    updateLog("  🔍 Sample cellular data extracted:");
                    for (int i = 0; i < Math.min(5, sampleData.length()); i++) {
                        org.json.JSONObject data = sampleData.getJSONObject(i);
                        String technology = data.optString("technology", "Unknown");
                        String type = data.getString("type");
                        updateLog("    " + (i+1) + ". " + technology + " - " + type);
                        
                        if (data.has("data")) {
                            String dataStr = data.getString("data");
                            if (dataStr.length() > 100) {
                                dataStr = dataStr.substring(0, 100) + "...";
                            }
                            updateLog("       " + dataStr);
                        }
                        
                        if (data.has("timestamp")) {
                            updateLog("       Time: " + data.getString("timestamp"));
                        }
                    }
                }
                
                // Process card data for UI display
                if (jsonResult.has("card_data")) {
                    org.json.JSONArray cardDataArray = jsonResult.getJSONArray("card_data");
                    updateLog("📱 Populating " + cardDataArray.length() + " signaling cards...");
                    
                    List<SignalingEvent> signalingEvents = new ArrayList<>();
                    for (int i = 0; i < cardDataArray.length(); i++) {
                        org.json.JSONObject cardData = cardDataArray.getJSONObject(i);
                        
                        // Convert JSON to SignalingEvent
                        SignalingEvent event = createSignalingEventFromJson(cardData);
                        if (event != null) {
                            signalingEvents.add(event);
                        }
                    }
                    
                    // Update the cards adapter
                    signalingCardAdapter.setEvents(signalingEvents);
                    updateLog("✅ Cards updated with real signaling data");
                }
            }
                    // Handle simple status messages
                    else if (jsonResult.has("message")) {
                        updateLog("✓ " + jsonResult.getString("message"));
                    }
                    // Handle error responses
                    else if (jsonResult.has("error")) {
                        updateLog("✗ Error: " + jsonResult.getString("error"));
                    }
                    else {
                        updateLog("✓ Result: " + result);
                    }
                } else {
                    updateLog("✓ " + result);
                }
            } catch (Exception e) {
                updateLog("✓ " + result);
            }
        });
    }

    @Override
    public void onAnalysisError(String error) {
        mainHandler.post(() -> {
            updateLog("✗ Analysis Error: " + error);
        });
    }

    private void updateLog(String log) {
        // For now, just log to console since we're using cards for signaling data
        System.out.println("Logger: " + log);
    }

    public void addLogMessage(String message) {
        updateLog(message);
    }

    public void clearLogs() {
        signalingCardAdapter.clearEvents();
        updateLog("Logger cleared");
    }
    
    private SignalingEvent createSignalingEventFromJson(org.json.JSONObject cardData) {
        try {
            // Extract data from JSON
            String technology = cardData.getString("technology");
            String direction = cardData.getString("direction");
            String messageTitle = cardData.getString("message_title");
            String description = cardData.getString("description");
            String timestamp = cardData.getString("timestamp");
            String fullData = cardData.getString("full_data");
            
            // Convert technology string to enum
            SignalingEvent.Technology tech;
            switch (technology.toUpperCase()) {
                case "LTE":
                    tech = SignalingEvent.Technology.LTE;
                    break;
                case "NR":
                    tech = SignalingEvent.Technology.NR;
                    break;
                case "UMTS":
                    tech = SignalingEvent.Technology.UMTS;
                    break;
                case "GSM":
                    tech = SignalingEvent.Technology.GSM;
                    break;
                default:
                    tech = SignalingEvent.Technology.LTE; // Default fallback
            }
            
            // Convert direction string to enum
            SignalingEvent.Direction dir;
            if ("DOWN".equalsIgnoreCase(direction)) {
                dir = SignalingEvent.Direction.DOWN;
            } else {
                dir = SignalingEvent.Direction.UP;
            }
            
            return new SignalingEvent(tech, dir, messageTitle, description, timestamp, fullData);
            
        } catch (Exception e) {
            updateLog("Error parsing card data: " + e.getMessage());
            return null;
        }
    }
    
    private void showSignalingDialog(SignalingEvent event) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(event.getTechnology().name() + " - " + event.getMessageTitle())
                .setMessage(event.getFullData())
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    public void addSignalingEvent(SignalingEvent event) {
        signalingCardAdapter.addEvent(event);
    }

}
