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
            
            qmdlBridge = python.getModule("qmdl_reader");
            Log.d(TAG, "QMDL reader module loaded: " + (qmdlBridge != null ? "success" : "failed"));
            
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
