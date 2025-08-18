package com.example.modiv3;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Utility class for testing QMDL parser functionality
 * Creates sample QMDL data for testing purposes
 */
public class QmdlTestUtils {
    private static final String TAG = "QmdlTestUtils";
    
    // HDLC frame delimiter
    private static final byte HDLC_FLAG = 0x7E;
    
    /**
     * Create a sample QMDL file for testing
     */
    public static boolean createSampleQmdlFile(String filePath, int numMessages) {
        try {
            File file = new File(filePath);
            
            // Check if we can write to the directory first
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                if (!parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        Log.e(TAG, "Failed to create directory: " + parentDir.getAbsolutePath());
                        return false;
                    }
                }
                
                // Test write permission by creating a temp file
                try {
                    File tempFile = new File(parentDir, ".test_write_" + System.currentTimeMillis());
                    if (!tempFile.createNewFile()) {
                        Log.e(TAG, "Cannot write to directory: " + parentDir.getAbsolutePath());
                        return false;
                    }
                    tempFile.delete();
                } catch (IOException e) {
                    Log.e(TAG, "No write permission to directory: " + parentDir.getAbsolutePath(), e);
                    return false;
                }
            }
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                Random random = new Random();
                
                for (int i = 0; i < numMessages; i++) {
                    // Create a sample DIAG LOG packet (0x10)
                    byte[] packet = createSampleLogPacket(i, random);
                    
                    // Write HDLC frame
                    fos.write(HDLC_FLAG);
                    fos.write(packet);
                    fos.write(HDLC_FLAG);
                }
                
                fos.flush();
            }
            
            Log.d(TAG, String.format("Created sample QMDL file: %s with %d messages (size: %d bytes)", 
                filePath, numMessages, file.length()));
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error creating sample QMDL file", e);
            return false;
        }
    }
    
    /**
     * Create a sample LOG packet
     */
    private static byte[] createSampleLogPacket(int messageIndex, Random random) {
        // LOG packet structure: cmd(1) + reserved(1) + length1(2) + length2(2) + log_id(2) + timestamp(8) + data
        int dataSize = 32 + random.nextInt(64); // 32-96 bytes of data
        int packetSize = 16 + dataSize + 2; // Header + data + CRC
        
        byte[] packet = new byte[packetSize];
        int offset = 0;
        
        // Command code (DIAG_LOG_F = 0x10)
        packet[offset++] = 0x10;
        
        // Reserved
        packet[offset++] = 0x00;
        
        // Length1 (little endian)
        int length1 = packetSize - 4; // Total length minus cmd and reserved
        packet[offset++] = (byte) (length1 & 0xFF);
        packet[offset++] = (byte) ((length1 >> 8) & 0xFF);
        
        // Length2 (little endian) - same as length1 + 4
        int length2 = length1 + 4;
        packet[offset++] = (byte) (length2 & 0xFF);
        packet[offset++] = (byte) ((length2 >> 8) & 0xFF);
        
        // Log ID (little endian) - use different log IDs for variety
        int logId = 0x4000 + (messageIndex % 256); // GSM range
        packet[offset++] = (byte) (logId & 0xFF);
        packet[offset++] = (byte) ((logId >> 8) & 0xFF);
        
        // Timestamp (little endian, 8 bytes)
        long timestamp = System.currentTimeMillis() + messageIndex * 1000;
        for (int i = 0; i < 8; i++) {
            packet[offset++] = (byte) ((timestamp >> (i * 8)) & 0xFF);
        }
        
        // Sample data
        for (int i = 0; i < dataSize; i++) {
            packet[offset++] = (byte) (random.nextInt(256));
        }
        
        // Simple CRC (just use random bytes for testing)
        packet[offset++] = (byte) random.nextInt(256);
        packet[offset] = (byte) random.nextInt(256);
        
        return packet;
    }
    
    /**
     * Create a large sample QMDL file for chunking tests
     */
    public static boolean createLargeSampleQmdlFile(String filePath, long targetSizeMB) {
        try {
            long targetSize = targetSizeMB * 1024 * 1024; // Convert to bytes
            int avgMessageSize = 128; // Average message size
            int estimatedMessages = (int) (targetSize / avgMessageSize);
            
            Log.d(TAG, String.format("Creating large QMDL file: %s (target: %d MB, estimated messages: %d)", 
                filePath, targetSizeMB, estimatedMessages));
            
            return createSampleQmdlFile(filePath, estimatedMessages);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating large sample QMDL file", e);
            return false;
        }
    }
    
    /**
     * Verify that a file exists and has expected properties
     */
    public static boolean verifyQmdlFile(String filePath) {
        try {
            File file = new File(filePath);
            
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: " + filePath);
                return false;
            }
            
            if (!file.canRead()) {
                Log.w(TAG, "File is not readable: " + filePath);
                return false;
            }
            
            long fileSize = file.length();
            if (fileSize == 0) {
                Log.w(TAG, "File is empty: " + filePath);
                return false;
            }
            
            Log.d(TAG, String.format("Verified QMDL file: %s (size: %.2f MB)", 
                filePath, fileSize / (1024.0 * 1024.0)));
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying QMDL file", e);
            return false;
        }
    }
    
    /**
     * Clean up test files
     */
    public static void cleanupTestFiles(String... filePaths) {
        for (String filePath : filePaths) {
            try {
                File file = new File(filePath);
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "Deleted test file: " + filePath);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete test file: " + filePath, e);
            }
        }
    }
}
