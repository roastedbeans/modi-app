package com.example.modiv3;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * File reader that can handle files requiring root access
 * Falls back to root-based reading when normal file access fails
 */
public class RootFileReader {
    private static final String TAG = "RootFileReader";
    
    /**
     * Read file data with automatic fallback to root access if needed
     */
    public static byte[] readFile(String filePath) throws IOException {
        File file = new File(filePath);
        
        // Try normal file access first
        if (file.exists() && file.canRead()) {
            try {
                return readFileNormal(file);
            } catch (IOException e) {
                Log.w(TAG, "Normal file read failed for " + filePath + ", trying root access", e);
            }
        }
        
        // Fallback to root access
        return readFileWithRoot(filePath);
    }
    
    /**
     * Read file using normal Java file access
     */
    private static byte[] readFileNormal(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            
            int totalBytesRead = 0;
            int bytesRead;
            
            while (totalBytesRead < data.length) {
                bytesRead = bis.read(data, totalBytesRead, data.length - totalBytesRead);
                if (bytesRead == -1) {
                    break;
                }
                totalBytesRead += bytesRead;
            }
            
            if (totalBytesRead != data.length) {
                throw new IOException("File read incomplete: expected " + data.length + 
                    " bytes, got " + totalBytesRead);
            }
        }
        
        Log.d(TAG, "Successfully read " + data.length + " bytes from " + file.getName());
        return data;
    }
    
    /**
     * Read file using root access
     */
    private static byte[] readFileWithRoot(String filePath) throws IOException {
        try {
            Log.d(TAG, "Reading file with root access: " + filePath);
            
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(process.getOutputStream());
            
            // Use cat to read the file and encode as base64 for safe transport
            writer.write("if [ -f \"" + filePath + "\" ]; then\n");
            writer.write("  echo 'FILE_EXISTS'\n");
            writer.write("  stat -c%s \"" + filePath + "\"\n");
            writer.write("  cat \"" + filePath + "\" | base64\n");
            writer.write("  echo 'EOF_MARKER'\n");
            writer.write("else\n");
            writer.write("  echo 'FILE_NOT_FOUND'\n");
            writer.write("fi\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            String line;
            boolean fileExists = false;
            long fileSize = 0;
            StringBuilder base64Data = new StringBuilder();
            boolean readingData = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.equals("FILE_EXISTS")) {
                    fileExists = true;
                } else if (line.equals("FILE_NOT_FOUND")) {
                    throw new IOException("File not found: " + filePath);
                } else if (fileExists && !readingData && line.matches("\\d+")) {
                    fileSize = Long.parseLong(line);
                    readingData = true;
                } else if (readingData && !line.equals("EOF_MARKER")) {
                    base64Data.append(line);
                } else if (line.equals("EOF_MARKER")) {
                    break;
                }
            }
            reader.close();
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Root command failed with exit code: " + exitCode);
            }
            
            if (!fileExists) {
                throw new IOException("File does not exist: " + filePath);
            }
            
            if (base64Data.length() == 0) {
                throw new IOException("No data read from file: " + filePath);
            }
            
            // Decode base64 data
            byte[] fileData = android.util.Base64.decode(base64Data.toString(), android.util.Base64.DEFAULT);
            
            Log.d(TAG, String.format("Root read successful: %s (%d bytes)", 
                new File(filePath).getName(), fileData.length));
            
            return fileData;
            
        } catch (Exception e) {
            throw new IOException("Root file read failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a file is accessible for reading
     */
    public static boolean canReadFile(String filePath) {
        File file = new File(filePath);
        
        // Try normal access first
        if (file.exists() && file.canRead()) {
            return true;
        }
        
        // Try root access
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(process.getOutputStream());
            
            writer.write("test -r \"" + filePath + "\" && echo 'READABLE' || echo 'NOT_READABLE'\n");
            writer.write("exit\n");
            writer.flush();
            writer.close();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            reader.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0 && "READABLE".equals(result);
            
        } catch (Exception e) {
            Log.w(TAG, "Error checking file readability with root", e);
            return false;
        }
    }
}
