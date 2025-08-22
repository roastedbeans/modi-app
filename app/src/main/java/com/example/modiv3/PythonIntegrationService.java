package com.example.modiv3;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PythonIntegrationService extends Service {
    private static final String TAG = "PythonIntegrationService";
    private final IBinder binder = new LocalBinder();
    private Python python;
    private PyObject qmdlBridge;
    private ExecutorService executorService;

    public interface PythonCallback {
        void onAnalysisComplete(String result);
        void onAnalysisError(String error);
    }

    private PythonCallback callback;

    public void setCallback(PythonCallback callback) {
        this.callback = callback;
    }

    public class LocalBinder extends Binder {
        public PythonIntegrationService getService() {
            return PythonIntegrationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        initializePython();
    }

    private void initializePython() {
        try {
            Log.d(TAG, "Starting Python initialization...");
            if (!Python.isStarted()) {
                Log.d(TAG, "Starting Python platform...");
                Python.start(new AndroidPlatform(this));
            }
            python = Python.getInstance();
            Log.d(TAG, "Python instance obtained: " + (python != null ? "success" : "failed"));
            
            qmdlBridge = python.getModule("qmdl_bridge");
            Log.d(TAG, "QMDL bridge module loaded: " + (qmdlBridge != null ? "success" : "failed"));
            
            Log.d(TAG, "Python integration initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Python", e);
            python = null;
            qmdlBridge = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void analyzeLogFile(String filePath) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                PyObject result = qmdlBridge.callAttr("analyze_file", filePath);
                String jsonResult = result.toString();

                Log.d(TAG, "Analysis result: " + jsonResult);
                notifyComplete(jsonResult);

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing log file", e);
                notifyError("Analysis failed: " + e.getMessage());
            }
        });
    }

    public void analyzeLogDirectory(String directoryPath) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                // Use hybrid Java+Python approach (Java finds files with root access, Python processes them)
                String filesJson = discoverQmdlFilesWithRoot(directoryPath);
                
                if (filesJson != null) {
                    // Pass the file list to Python for processing
                    PyObject result = qmdlBridge.callAttr("process_qmdl_files_from_java", filesJson);
                    String jsonResult = result.toString();

                    Log.d(TAG, "Directory analysis result (hybrid): " + jsonResult);
                    notifyComplete(jsonResult);
                } else {
                    // Fallback to Python-only approach
                    Log.w(TAG, "Java root access failed, falling back to Python-only approach");
                    PyObject result = qmdlBridge.callAttr("analyze_directory", directoryPath);
                    String jsonResult = result.toString();

                    Log.d(TAG, "Directory analysis result (Python fallback): " + jsonResult);
                    notifyComplete(jsonResult);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing log directory", e);
                notifyError("Directory analysis failed: " + e.getMessage());
            }
        });
    }

    public void generateAnalysisReport(String outputPath) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                PyObject result = qmdlBridge.callAttr("generate_report", outputPath);
                String jsonResult = result.toString();

                Log.d(TAG, "Report generation result: " + jsonResult);
                notifyComplete(jsonResult);

            } catch (Exception e) {
                Log.e(TAG, "Error generating report", e);
                notifyError("Report generation failed: " + e.getMessage());
            }
        });
    }

    public void createVisualizations() {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                PyObject result = qmdlBridge.callAttr("create_visualizations");
                String jsonResult = result.toString();

                Log.d(TAG, "Visualization result: " + jsonResult);
                notifyComplete(jsonResult);

            } catch (Exception e) {
                Log.e(TAG, "Error creating visualizations", e);
                notifyError("Visualization creation failed: " + e.getMessage());
            }
        });
    }

    public void runMachineLearningAnalysis(String logDataJson) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                PyObject result = qmdlBridge.callAttr("run_ml_analysis", logDataJson);
                String jsonResult = result.toString();

                Log.d(TAG, "ML analysis result: " + jsonResult);
                notifyComplete(jsonResult);

            } catch (Exception e) {
                Log.e(TAG, "Error running ML analysis", e);
                notifyError("ML analysis failed: " + e.getMessage());
            }
        });
    }

    public void extractErrorPatterns() {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                PyObject result = qmdlBridge.callAttr("extract_errors");
                String jsonResult = result.toString();

                Log.d(TAG, "Error extraction result: " + jsonResult);
                notifyComplete(jsonResult);

            } catch (Exception e) {
                Log.e(TAG, "Error extracting error patterns", e);
                notifyError("Error extraction failed: " + e.getMessage());
            }
        });
    }

    public void testQmdlFileAccess(String directoryPath) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                // First try the original Python-only approach for debugging
                PyObject result = qmdlBridge.callAttr("test_qmdl_file_access", directoryPath);
                String jsonResult = result.toString();

                Log.d(TAG, "QMDL file access test result (Python-only): " + jsonResult);
                
                // Now try the hybrid Java+Python approach
                testQmdlFileAccessWithJava(directoryPath);

            } catch (Exception e) {
                Log.e(TAG, "Error testing QMDL file access", e);
                notifyError("QMDL file access test failed: " + e.getMessage());
            }
        });
    }
    
    public void testQmdlFileAccessWithJava(String directoryPath) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                // Use Java to discover QMDL files with root access (similar to QmdlService approach)
                String filesJson = discoverQmdlFilesWithRoot(directoryPath);
                
                if (filesJson != null) {
                    // Pass the file list to Python for processing
                    PyObject result = qmdlBridge.callAttr("process_qmdl_files_from_java", filesJson);
                    String jsonResult = result.toString();

                    Log.d(TAG, "QMDL file access test result (Java+Python): " + jsonResult);
                    notifyComplete(jsonResult);
                } else {
                    notifyError("Failed to discover QMDL files with Java");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error testing QMDL file access with Java", e);
                notifyError("QMDL file access test (Java+Python) failed: " + e.getMessage());
            }
        });
    }
    
    private String discoverQmdlFilesWithRoot(String directoryPath) {
        try {
            // Use the same pattern as QmdlService - start su process and write commands
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(process.getOutputStream());
            
            // Write commands to list QMDL files
            writer.write("ls -la " + directoryPath + " | grep -E '\\.qmdl$|\\.QMDL$'\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            org.json.JSONObject result = new org.json.JSONObject();
            result.put("directory", directoryPath);
            org.json.JSONArray files = new org.json.JSONArray();
            
            String line;
            int fileCount = 0;
            long minSizeBytes = 20 * 1024 * 1024; // 20MB
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                Log.d(TAG, "Root ls output: " + line);
                
                // Parse ls -la output: permissions links owner group size month day time filename
                String[] parts = line.trim().split("\\s+");
                Log.d(TAG, "Parsing line with " + parts.length + " parts: " + java.util.Arrays.toString(parts));
                if (parts.length >= 8) {
                    try {
                        // In ls -la output: permissions(0) links(1) owner(2) group(3) size(4) month(5) day(6) time(7) filename(8+)
                        long fileSize = Long.parseLong(parts[4]);
                        String filename = parts[parts.length - 1];
                        
                        if (fileSize >= minSizeBytes) {
                            org.json.JSONObject fileInfo = new org.json.JSONObject();
                            fileInfo.put("path", directoryPath + "/" + filename);
                            fileInfo.put("size", fileSize);
                            fileInfo.put("filename", filename);
                            files.put(fileInfo);
                            fileCount++;
                            
                            Log.d(TAG, "Found QMDL file >= 20MB (Java): " + filename + 
                                  " (" + (fileSize / (1024.0 * 1024.0)) + "MB)");
                        } else {
                            Log.d(TAG, "Skipping QMDL file < 20MB (Java): " + filename + 
                                  " (" + (fileSize / (1024.0 * 1024.0)) + "MB)");
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Could not parse file size from ls output: " + line);
                    }
                }
            }
            reader.close();
            
            result.put("files", files);
            
            int exitCode = process.waitFor();
            Log.d(TAG, "Root command exit code: " + exitCode);
            
            if (exitCode == 0) {
                Log.d(TAG, "Java root discovery found " + fileCount + " QMDL files >= 20MB in " + directoryPath);
                return result.toString();
            } else {
                Log.e(TAG, "Root ls command failed with exit code: " + exitCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error discovering QMDL files with Java root access", e);
            return null;
        }
    }

    public void readQmdlFile(String filePath) {
        executorService.execute(() -> {
            try {
                if (qmdlBridge == null) {
                    notifyError("Python bridge not initialized");
                    return;
                }

                Log.d(TAG, "Attempting to read QMDL file with comprehensive signaling analysis: " + filePath);
                
                // Copy file to accessible location using root access, then let Python read it
                String accessibleFilePath = copyQmdlFileToAccessibleLocation(filePath);
                
                if (accessibleFilePath != null) {
                    Log.d(TAG, "File copied to accessible location: " + accessibleFilePath);
                    
                    // Now Python can read the file using scat FileIO approach
                    PyObject result = qmdlBridge.callAttr("read_qmdl_file", accessibleFilePath);
                    String jsonResult = result.toString();

                    Log.d(TAG, "QMDL file read result: " + jsonResult);
                    notifyComplete(jsonResult);
                    
                    // Clean up the temporary file
                    cleanupTempFile(accessibleFilePath);
                } else {
                    notifyError("Failed to copy QMDL file to accessible location");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error reading QMDL file", e);
                notifyError("QMDL file read failed: " + e.getMessage());
            }
        });
    }
    
    private String copyQmdlFileToAccessibleLocation(String originalFilePath) {
        try {
            // Create temp file in app's cache directory
            String tempFileName = "temp_qmdl_" + System.currentTimeMillis() + ".qmdl";
            String tempFilePath = getApplicationContext().getCacheDir().getAbsolutePath() + "/" + tempFileName;
            
            Log.d(TAG, "Copying QMDL file to: " + tempFilePath);
            
            // Use root access to copy the file
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(process.getOutputStream());
            
            // Copy the file and make it readable by the app
            writer.write("cp \"" + originalFilePath + "\" \"" + tempFilePath + "\"\n");
            writer.write("chown " + getApplicationContext().getApplicationInfo().uid + ":" + getApplicationContext().getApplicationInfo().uid + " \"" + tempFilePath + "\"\n");
            writer.write("chmod 644 \"" + tempFilePath + "\"\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            int exitCode = process.waitFor();
            Log.d(TAG, "Root copy command exit code: " + exitCode);
            
            if (exitCode == 0) {
                // Verify the file was copied successfully
                java.io.File tempFile = new java.io.File(tempFilePath);
                if (tempFile.exists() && tempFile.canRead()) {
                    Log.d(TAG, "Successfully copied QMDL file. Size: " + tempFile.length() + " bytes");
                    return tempFilePath;
                } else {
                    Log.e(TAG, "Temp file not accessible after copy");
                    return null;
                }
            } else {
                Log.e(TAG, "Root copy command failed with exit code: " + exitCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying QMDL file to accessible location", e);
            return null;
        }
    }
    
    private void cleanupTempFile(String tempFilePath) {
        try {
            java.io.File tempFile = new java.io.File(tempFilePath);
            if (tempFile.exists()) {
                boolean deleted = tempFile.delete();
                Log.d(TAG, "Temp file cleanup: " + (deleted ? "success" : "failed"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up temp file", e);
        }
    }

    private void notifyComplete(String result) {
        if (callback != null) {
            callback.onAnalysisComplete(result);
        }
    }

    private void notifyError(String error) {
        if (callback != null) {
            callback.onAnalysisError(error);
        }
    }

    public boolean isPythonReady() {
        return python != null && qmdlBridge != null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
