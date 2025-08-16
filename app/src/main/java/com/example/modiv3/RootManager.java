package com.example.modiv3;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class RootManager {
    private static final String TAG = "RootManager";
    
    public interface RootCallback {
        void onRootGranted();
        void onRootDenied();
        void onRootError(String error);
    }
    
    /**
     * Request root access and execute a test command
     */
    public static void requestRootAccess(Context context, RootCallback callback) {
        new Thread(() -> {
            try {
                // Test root access with a simple command
                Process process = Runtime.getRuntime().exec("su");
                OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
                
                // Write test command
                writer.write("id\n");
                writer.write("exit\n");
                writer.flush();
                writer.close();
                
                // Read the output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    Log.d(TAG, "Root access granted. Output: " + output.toString());
                    callback.onRootGranted();
                } else {
                    Log.w(TAG, "Root access denied. Exit code: " + exitCode);
                    callback.onRootDenied();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error requesting root access", e);
                callback.onRootError("Failed to request root access: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Execute a command with root privileges
     */
    public static void executeRootCommand(String command, RootCallback callback) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("su");
                OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
                
                writer.write(command + "\n");
                writer.write("exit\n");
                writer.flush();
                writer.close();
                
                // Read output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    Log.d(TAG, "Command executed successfully: " + command);
                    callback.onRootGranted();
                } else {
                    Log.w(TAG, "Command failed with exit code: " + exitCode);
                    callback.onRootDenied();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error executing root command", e);
                callback.onRootError("Failed to execute command: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Check if root access is available without requesting it
     */
    public static boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking root availability", e);
            return false;
        }
    }
}
