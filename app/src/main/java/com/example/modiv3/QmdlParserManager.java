package com.example.modiv3;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main manager class for QMDL parsing operations
 * Handles file chunking, parsing, and JSON conversion with memory management
 */
public class QmdlParserManager {
    private static final String TAG = "QmdlParserManager";
    private static final int MAX_THREADS = 3; // Conservative thread count for Android
    private static final long MAX_MEMORY_USAGE = Runtime.getRuntime().maxMemory() / 4; // Use 25% of max memory
    
    private ExecutorService executorService;
    private QmdlFileChunker fileChunker;
    private QmdlParser parser;
    private volatile boolean isRunning = false;
    
    // Statistics
    private AtomicInteger totalFilesProcessed = new AtomicInteger(0);
    private AtomicInteger totalMessagesProcessed = new AtomicInteger(0);
    private AtomicLong totalBytesProcessed = new AtomicLong(0);
    
    public interface ProgressCallback {
        void onFileStarted(String fileName, long fileSize);
        void onChunkingProgress(String fileName, int chunksCompleted, int totalChunks);
        void onParsingProgress(String fileName, long bytesProcessed, long totalBytes);
        void onFileCompleted(String fileName, int messageCount, long parseTimeMs);
        void onError(String fileName, String error, Exception e);
        void onAllCompleted(ParseSummary summary);
    }
    
    public static class ParseResult {
        public final boolean success;
        public final List<DiagMessage> messages;
        public final int totalMessages;
        public final long parseTimeMs;
        public final String errorMessage;
        public final String fileName;
        
        public ParseResult(boolean success, List<DiagMessage> messages, int totalMessages,
                          long parseTimeMs, String errorMessage, String fileName) {
            this.success = success;
            this.messages = messages != null ? messages : new ArrayList<>();
            this.totalMessages = totalMessages;
            this.parseTimeMs = parseTimeMs;
            this.errorMessage = errorMessage != null ? errorMessage : "";
            this.fileName = fileName != null ? fileName : "";
        }
        
        public static ParseResult success(List<DiagMessage> messages, long parseTimeMs, String fileName) {
            return new ParseResult(true, messages, messages.size(), parseTimeMs, null, fileName);
        }
        
        public static ParseResult error(String errorMessage, String fileName) {
            return new ParseResult(false, null, 0, 0, errorMessage, fileName);
        }
    }
    
    public static class ParseSummary {
        public final int totalFiles;
        public final int successfulFiles;
        public final int failedFiles;
        public final int totalMessages;
        public final long totalParseTime;
        public final long totalBytesProcessed;
        public final Map<String, Integer> messagesByTechnology;
        public final Map<String, Integer> messagesByLayer;
        public final List<String> errors;
        
        public ParseSummary(int totalFiles, int successfulFiles, int failedFiles,
                           int totalMessages, long totalParseTime, long totalBytesProcessed,
                           Map<String, Integer> messagesByTechnology,
                           Map<String, Integer> messagesByLayer,
                           List<String> errors) {
            this.totalFiles = totalFiles;
            this.successfulFiles = successfulFiles;
            this.failedFiles = failedFiles;
            this.totalMessages = totalMessages;
            this.totalParseTime = totalParseTime;
            this.totalBytesProcessed = totalBytesProcessed;
            this.messagesByTechnology = messagesByTechnology != null ? messagesByTechnology : new HashMap<>();
            this.messagesByLayer = messagesByLayer != null ? messagesByLayer : new HashMap<>();
            this.errors = errors != null ? errors : new ArrayList<>();
        }
    }
    
    public static class DiagMessage {
        // This is a placeholder - the actual DiagMessage class is defined separately
        public final int logId;
        public final long timestamp;
        public final String technology;
        public final String layer;
        public final String parsedContent;
        
        public DiagMessage(int logId, long timestamp, String technology, String layer, String parsedContent) {
            this.logId = logId;
            this.timestamp = timestamp;
            this.technology = technology != null ? technology : "";
            this.layer = layer != null ? layer : "";
            this.parsedContent = parsedContent != null ? parsedContent : "";
        }
        
        @Override
        public String toString() {
            return String.format("[0x%04X] %s %s: %s", logId, technology, layer, 
                parsedContent.length() > 50 ? parsedContent.substring(0, 47) + "..." : parsedContent);
        }
    }
    
    public QmdlParserManager() {
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
        this.fileChunker = new QmdlFileChunker();
        this.parser = new QmdlParser();
    }
    
    /**
     * Parse a single QMDL file
     */
    public ParseResult parseQmdlFile(String filePath) {
        return parseQmdlFile(filePath, null);
    }
    
    /**
     * Parse a single QMDL file with callback
     */
    public ParseResult parseQmdlFile(String filePath, ProgressCallback callback) {
        long startTime = System.currentTimeMillis();
        String fileName = new File(filePath).getName();
        
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.canRead()) {
                String error = "File does not exist or is not readable: " + filePath;
                if (callback != null) callback.onError(fileName, error, null);
                return ParseResult.error(error, fileName);
            }
            
            long fileSize = file.length();
            if (callback != null) {
                callback.onFileStarted(fileName, fileSize);
            }
            
            Log.d(TAG, String.format("Starting parse of %s (%.2f MB)", fileName, fileSize / (1024.0 * 1024.0)));
            
            List<com.example.modiv3.DiagMessage> allMessages = new ArrayList<>();
            
            // Check if file needs chunking
            if (QmdlFileChunker.needsChunking(filePath)) {
                allMessages = parseWithChunking(filePath, callback);
            } else {
                allMessages = parseDirectly(filePath, callback);
            }
            
            long parseTime = System.currentTimeMillis() - startTime;
            
            if (callback != null) {
                callback.onFileCompleted(fileName, allMessages.size(), parseTime);
            }
            
            // Convert to legacy DiagMessage format for compatibility
            List<DiagMessage> legacyMessages = convertToLegacyFormat(allMessages);
            
            Log.d(TAG, String.format("Completed parsing %s: %d messages in %d ms", 
                fileName, allMessages.size(), parseTime));
            
            return ParseResult.success(legacyMessages, parseTime, fileName);
            
        } catch (Exception e) {
            long parseTime = System.currentTimeMillis() - startTime;
            String error = "Failed to parse file: " + e.getMessage();
            Log.e(TAG, error, e);
            
            if (callback != null) {
                callback.onError(fileName, error, e);
            }
            
            return ParseResult.error(error, fileName);
        }
    }
    
    /**
     * Parse file with chunking for large files
     */
    private List<com.example.modiv3.DiagMessage> parseWithChunking(String filePath, ProgressCallback callback) {
        List<com.example.modiv3.DiagMessage> allMessages = new ArrayList<>();
        String tempDir = createTempDirectory(filePath);
        
        try {
            // Chunk the file
            List<QmdlFileChunker.ChunkInfo> chunks = fileChunker.chunkFile(filePath, tempDir,
                new QmdlFileChunker.ChunkingCallback() {
                    @Override
                    public void onProgress(int chunksCompleted, int totalChunks, String currentChunk) {
                        if (callback != null) {
                            callback.onChunkingProgress(new File(filePath).getName(), chunksCompleted, totalChunks);
                        }
                    }
                    
                    @Override
                    public void onChunkCompleted(String chunkPath, long chunkSize) {
                        Log.d(TAG, String.format("Chunk completed: %s (%.2f MB)", 
                            new File(chunkPath).getName(), chunkSize / (1024.0 * 1024.0)));
                    }
                    
                    @Override
                    public void onError(String error, Exception e) {
                        Log.e(TAG, "Chunking error: " + error, e);
                    }
                    
                    @Override
                    public void onCompleted(List<String> chunkPaths) {
                        Log.d(TAG, "Chunking completed: " + chunkPaths.size() + " chunks");
                    }
                });
            
            if (chunks.isEmpty()) {
                throw new RuntimeException("Failed to create chunks");
            }
            
            // Parse each chunk
            for (QmdlFileChunker.ChunkInfo chunk : chunks) {
                try {
                    QmdlParser.ParseResult chunkResult = parser.parseFile(chunk.chunkPath,
                        new QmdlParser.ParseCallback() {
                            @Override
                            public void onProgress(long bytesProcessed, long totalBytes, String currentFile) {
                                if (callback != null) {
                                    callback.onParsingProgress(currentFile, bytesProcessed, totalBytes);
                                }
                            }
                            
                            @Override
                            public void onMessageParsed(com.example.modiv3.DiagMessage message) {
                                // Messages are collected in the result
                            }
                            
                            @Override
                            public void onError(String error, Exception e) {
                                Log.w(TAG, "Chunk parsing error: " + error, e);
                            }
                            
                            @Override
                            public void onCompleted(int totalMessages, long parseTimeMs) {
                                Log.d(TAG, String.format("Chunk parsed: %d messages in %d ms", 
                                    totalMessages, parseTimeMs));
                            }
                        });
                    
                    if (chunkResult.success) {
                        allMessages.addAll(chunkResult.messages);
                    }
                    
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing chunk: " + chunk.chunkPath, e);
                }
                
                // Memory management
                System.gc();
                
                // Check memory usage
                if (getMemoryUsage() > MAX_MEMORY_USAGE) {
                    Log.w(TAG, "High memory usage detected, forcing GC");
                    System.gc();
                    Thread.yield();
                }
            }
            
        } finally {
            // Cleanup temporary chunks
            cleanupTempDirectory(tempDir);
        }
        
        return allMessages;
    }
    
    /**
     * Parse file directly without chunking
     */
    private List<com.example.modiv3.DiagMessage> parseDirectly(String filePath, ProgressCallback callback) {
        QmdlParser.ParseResult result = parser.parseFile(filePath,
            new QmdlParser.ParseCallback() {
                @Override
                public void onProgress(long bytesProcessed, long totalBytes, String currentFile) {
                    if (callback != null) {
                        callback.onParsingProgress(currentFile, bytesProcessed, totalBytes);
                    }
                }
                
                @Override
                public void onMessageParsed(com.example.modiv3.DiagMessage message) {
                    // Messages are collected in the result
                }
                
                @Override
                public void onError(String error, Exception e) {
                    Log.w(TAG, "Direct parsing error: " + error, e);
                }
                
                @Override
                public void onCompleted(int totalMessages, long parseTimeMs) {
                    Log.d(TAG, String.format("Direct parsing completed: %d messages in %d ms", 
                        totalMessages, parseTimeMs));
                }
            });
        
        return result.success ? result.messages : new ArrayList<>();
    }
    
    /**
     * Parse all QMDL files in a directory
     */
    public List<ParseResult> parseQmdlDirectory(String directoryPath) {
        return parseQmdlDirectory(directoryPath, null);
    }
    
    /**
     * Parse all QMDL files in a directory with callback
     */
    public List<ParseResult> parseQmdlDirectory(String directoryPath, ProgressCallback callback) {
        List<ParseResult> results = new ArrayList<>();
        
        try {
            // Use StorageHelper to find QMDL files (handles Android 11+ scoped storage)
            List<File> qmdlFiles = StorageHelper.getQmdlFiles(directoryPath);
            
            if (qmdlFiles.isEmpty()) {
                // Try alternative directories
                Log.w(TAG, "No QMDL files found in " + directoryPath + ", trying alternative locations...");
                qmdlFiles = StorageHelper.findQmdlFilesAnywhere();
                
                if (qmdlFiles.isEmpty()) {
                    Log.w(TAG, "No QMDL files found in any location");
                    Log.d(TAG, StorageHelper.getStorageDebugInfo());
                    return results;
                }
            }
            
            Log.d(TAG, String.format("Found %d QMDL files to parse", qmdlFiles.size()));
            
            // Convert List<File> to File[] for compatibility
            File[] files = qmdlFiles.toArray(new File[0]);
            
            // Parse each file
            for (File file : files) {
                try {
                    // Check file accessibility before parsing
                    if (!file.exists()) {
                        Log.w(TAG, "File does not exist: " + file.getAbsolutePath());
                        results.add(ParseResult.error("File does not exist", file.getName()));
                        continue;
                    }
                    
                    if (!file.canRead()) {
                        Log.w(TAG, "File is not readable: " + file.getAbsolutePath());
                        results.add(ParseResult.error("File is not readable", file.getName()));
                        continue;
                    }
                    
                    if (file.length() == 0) {
                        Log.w(TAG, "File is empty: " + file.getAbsolutePath());
                        results.add(ParseResult.error("File is empty", file.getName()));
                        continue;
                    }
                    
                    Log.d(TAG, String.format("Parsing file: %s (%.2f MB)", 
                        file.getName(), file.length() / (1024.0 * 1024.0)));
                    
                    ParseResult result = parseQmdlFile(file.getAbsolutePath(), callback);
                    results.add(result);
                    
                    if (result.success) {
                        totalFilesProcessed.incrementAndGet();
                        totalMessagesProcessed.addAndGet(result.totalMessages);
                        totalBytesProcessed.addAndGet(file.length());
                    } else {
                        Log.w(TAG, "Parse failed for " + file.getName() + ": " + result.errorMessage);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Exception parsing file: " + file.getName(), e);
                    results.add(ParseResult.error("Exception: " + e.getMessage(), file.getName()));
                }
                
                // Memory management between files
                System.gc();
            }
            
            // Generate summary
            if (callback != null) {
                ParseSummary summary = generateSummary(results);
                callback.onAllCompleted(summary);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing directory: " + directoryPath, e);
        }
        
        return results;
    }
    
    /**
     * Convert messages to JSON and save to file
     */
    public boolean convertToJson(List<com.example.modiv3.DiagMessage> messages, String outputPath) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", "1.0");
            root.put("generatedAt", System.currentTimeMillis());
            root.put("totalMessages", messages.size());
            
            JSONArray messagesArray = new JSONArray();
            for (com.example.modiv3.DiagMessage message : messages) {
                messagesArray.put(message.toJson());
            }
            root.put("messages", messagesArray);
            
            // Write to file
            try (FileWriter writer = new FileWriter(outputPath)) {
                writer.write(root.toString(2)); // Pretty print with indent
            }
            
            Log.d(TAG, String.format("Converted %d messages to JSON: %s", messages.size(), outputPath));
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting to JSON", e);
            return false;
        }
    }
    
    /**
     * Generate parse summary
     */
    public ParseSummary generateSummary(List<ParseResult> results) {
        int totalFiles = results.size();
        int successfulFiles = 0;
        int failedFiles = 0;
        int totalMessages = 0;
        long totalParseTime = 0;
        long totalBytes = 0;
        Map<String, Integer> messagesByTechnology = new HashMap<>();
        Map<String, Integer> messagesByLayer = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        for (ParseResult result : results) {
            if (result.success) {
                successfulFiles++;
                totalMessages += result.totalMessages;
                totalParseTime += result.parseTimeMs;
                
                // Count by technology and layer
                for (DiagMessage message : result.messages) {
                    if (!message.technology.isEmpty()) {
                        messagesByTechnology.merge(message.technology, 1, Integer::sum);
                    }
                    if (!message.layer.isEmpty()) {
                        messagesByLayer.merge(message.layer, 1, Integer::sum);
                    }
                }
            } else {
                failedFiles++;
                if (!result.errorMessage.isEmpty()) {
                    errors.add(result.fileName + ": " + result.errorMessage);
                }
            }
        }
        
        return new ParseSummary(totalFiles, successfulFiles, failedFiles, totalMessages,
            totalParseTime, totalBytes, messagesByTechnology, messagesByLayer, errors);
    }
    
    /**
     * Get a human-readable summary string
     */
    public String getParseSummary(List<ParseResult> results) {
        ParseSummary summary = generateSummary(results);
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== QMDL Parse Summary ===\n");
        sb.append(String.format("Total Files: %d\n", summary.totalFiles));
        sb.append(String.format("Successful: %d\n", summary.successfulFiles));
        sb.append(String.format("Failed: %d\n", summary.failedFiles));
        sb.append(String.format("Total Messages: %d\n", summary.totalMessages));
        sb.append(String.format("Total Parse Time: %.2f seconds\n", summary.totalParseTime / 1000.0));
        
        if (!summary.messagesByTechnology.isEmpty()) {
            sb.append("\nMessages by Technology:\n");
            for (Map.Entry<String, Integer> entry : summary.messagesByTechnology.entrySet()) {
                sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
            }
        }
        
        if (!summary.messagesByLayer.isEmpty()) {
            sb.append("\nMessages by Layer:\n");
            for (Map.Entry<String, Integer> entry : summary.messagesByLayer.entrySet()) {
                sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
            }
        }
        
        if (!summary.errors.isEmpty()) {
            sb.append("\nErrors:\n");
            for (String error : summary.errors) {
                sb.append("  ").append(error).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Convert new DiagMessage format to legacy format for compatibility
     */
    private List<DiagMessage> convertToLegacyFormat(List<com.example.modiv3.DiagMessage> newMessages) {
        List<DiagMessage> legacyMessages = new ArrayList<>();
        
        for (com.example.modiv3.DiagMessage newMsg : newMessages) {
            DiagMessage legacyMsg = new DiagMessage(
                newMsg.logId,
                newMsg.timestamp,
                newMsg.technology,
                newMsg.layer,
                newMsg.parsedContent
            );
            legacyMessages.add(legacyMsg);
        }
        
        return legacyMessages;
    }
    
    /**
     * Create temporary directory for chunks
     */
    private String createTempDirectory(String originalFilePath) {
        String baseDir = new File(originalFilePath).getParent();
        String tempDirName = "qmdl_chunks_" + System.currentTimeMillis();
        String tempDirPath = baseDir + File.separator + tempDirName;
        
        File tempDir = new File(tempDirPath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        return tempDirPath;
    }
    
    /**
     * Clean up temporary directory and its contents
     */
    private void cleanupTempDirectory(String tempDirPath) {
        try {
            File tempDir = new File(tempDirPath);
            if (tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.delete()) {
                            Log.w(TAG, "Failed to delete temp file: " + file.getName());
                        }
                    }
                }
                if (!tempDir.delete()) {
                    Log.w(TAG, "Failed to delete temp directory: " + tempDirPath);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up temp directory", e);
        }
    }
    
    /**
     * Get current memory usage
     */
    private long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    /**
     * Stop all parsing operations
     */
    public void stop() {
        isRunning = false;
        if (parser != null) {
            parser.stop();
        }
        if (fileChunker != null) {
            fileChunker.cancel();
        }
    }
    
    /**
     * Shutdown and cleanup resources
     */
    public void shutdown() {
        stop();
        
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (fileChunker != null) {
            fileChunker.shutdown();
        }
    }
    
    /**
     * Get parsing statistics
     */
    public String getStatistics() {
        return String.format("Files: %d, Messages: %d, Bytes: %.2f MB",
            totalFilesProcessed.get(),
            totalMessagesProcessed.get(),
            totalBytesProcessed.get() / (1024.0 * 1024.0));
    }
}
