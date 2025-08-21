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
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }
            python = Python.getInstance();
            qmdlBridge = python.getModule("qmdl_bridge");
            Log.d(TAG, "Python integration initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Python", e);
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

                PyObject result = qmdlBridge.callAttr("analyze_directory", directoryPath);
                String jsonResult = result.toString();

                Log.d(TAG, "Directory analysis result: " + jsonResult);
                notifyComplete(jsonResult);

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
