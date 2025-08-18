package com.example.modiv3;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Handles chunking of large QMDL files into smaller 20MB pieces for efficient processing
 * Supports both sequential and parallel chunking operations with memory management
 */
public class QmdlFileChunker {
    private static final String TAG = "QmdlFileChunker";
    private static final long CHUNK_SIZE = 20 * 1024 * 1024; // 20MB chunks
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for file operations
    private static final int MAX_THREADS = 4; // Maximum threads for parallel operations
    
    private ExecutorService executorService;
    private volatile boolean isCancelled = false;
    
    public interface ChunkingCallback {
        void onProgress(int chunksCompleted, int totalChunks, String currentChunk);
        void onChunkCompleted(String chunkPath, long chunkSize);
        void onError(String error, Exception e);
        void onCompleted(List<String> chunkPaths);
    }
    
    public static class ChunkInfo {
        public final String originalFile;
        public final String chunkPath;
        public final long startOffset;
        public final long size;
        public final int chunkIndex;
        public final int totalChunks;
        
        public ChunkInfo(String originalFile, String chunkPath, long startOffset, 
                        long size, int chunkIndex, int totalChunks) {
            this.originalFile = originalFile;
            this.chunkPath = chunkPath;
            this.startOffset = startOffset;
            this.size = size;
            this.chunkIndex = chunkIndex;
            this.totalChunks = totalChunks;
        }
        
        @Override
        public String toString() {
            return String.format("Chunk %d/%d: %s (offset=%d, size=%d)", 
                chunkIndex + 1, totalChunks, chunkPath, startOffset, size);
        }
    }
    
    public QmdlFileChunker() {
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
    }
    
    /**
     * Chunk a QMDL file into 20MB pieces
     * @param inputFilePath Path to the input QMDL file
     * @param outputDirectory Directory to store chunks
     * @param callback Callback for progress updates
     * @return List of chunk information
     */
    public List<ChunkInfo> chunkFile(String inputFilePath, String outputDirectory, 
                                   ChunkingCallback callback) {
        List<ChunkInfo> chunks = new ArrayList<>();
        
        try {
            File inputFile = new File(inputFilePath);
            if (!inputFile.exists() || !inputFile.canRead()) {
                if (callback != null) {
                    callback.onError("Input file does not exist or is not readable: " + inputFilePath, null);
                }
                return chunks;
            }
            
            // Create output directory if it doesn't exist
            File outputDir = new File(outputDirectory);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                if (callback != null) {
                    callback.onError("Failed to create output directory: " + outputDirectory, null);
                }
                return chunks;
            }
            
            long fileSize = inputFile.length();
            int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
            
            Log.d(TAG, String.format("Chunking file: %s (%.2f MB) into %d chunks", 
                inputFilePath, fileSize / (1024.0 * 1024.0), totalChunks));
            
            String baseFileName = getBaseFileName(inputFile.getName());
            
            // Create chunk info objects
            for (int i = 0; i < totalChunks; i++) {
                long startOffset = i * CHUNK_SIZE;
                long chunkSize = Math.min(CHUNK_SIZE, fileSize - startOffset);
                String chunkPath = String.format("%s/%s_chunk_%03d.qmdl", 
                    outputDirectory, baseFileName, i);
                
                chunks.add(new ChunkInfo(inputFilePath, chunkPath, startOffset, 
                    chunkSize, i, totalChunks));
            }
            
            // Perform chunking
            chunkFileSequential(inputFile, chunks, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during file chunking", e);
            if (callback != null) {
                callback.onError("Chunking failed: " + e.getMessage(), e);
            }
        }
        
        return chunks;
    }
    
    /**
     * Sequential chunking - safer for memory management
     */
    private void chunkFileSequential(File inputFile, List<ChunkInfo> chunks, 
                                   ChunkingCallback callback) {
        List<String> completedChunks = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
            byte[] buffer = new byte[BUFFER_SIZE];
            
            for (int i = 0; i < chunks.size() && !isCancelled; i++) {
                ChunkInfo chunk = chunks.get(i);
                
                if (callback != null) {
                    callback.onProgress(i, chunks.size(), chunk.chunkPath);
                }
                
                try {
                    createChunk(raf, chunk, buffer);
                    completedChunks.add(chunk.chunkPath);
                    
                    if (callback != null) {
                        callback.onChunkCompleted(chunk.chunkPath, chunk.size);
                    }
                    
                    Log.d(TAG, String.format("Created chunk %d/%d: %s (%.2f MB)", 
                        i + 1, chunks.size(), chunk.chunkPath, chunk.size / (1024.0 * 1024.0)));
                        
                } catch (IOException e) {
                    Log.e(TAG, "Error creating chunk: " + chunk.chunkPath, e);
                    if (callback != null) {
                        callback.onError("Failed to create chunk: " + chunk.chunkPath, e);
                    }
                    // Continue with next chunk
                }
                
                // Memory management - suggest GC between chunks
                if (i % 5 == 0) {
                    System.gc();
                }
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error accessing input file", e);
            if (callback != null) {
                callback.onError("Failed to access input file", e);
            }
            return;
        }
        
        if (callback != null && !isCancelled) {
            callback.onCompleted(completedChunks);
        }
    }
    
    /**
     * Create a single chunk file
     */
    private void createChunk(RandomAccessFile raf, ChunkInfo chunk, byte[] buffer) throws IOException {
        File chunkFile = new File(chunk.chunkPath);
        
        // Ensure parent directory exists
        File parentDir = chunkFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
            raf.seek(chunk.startOffset);
            
            long remaining = chunk.size;
            int bytesRead;
            
            while (remaining > 0 && !isCancelled) {
                int toRead = (int) Math.min(buffer.length, remaining);
                bytesRead = raf.read(buffer, 0, toRead);
                
                if (bytesRead == -1) {
                    break; // End of file
                }
                
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            
            fos.flush();
        }
        
        // Verify chunk was created correctly
        if (!chunkFile.exists() || chunkFile.length() != chunk.size) {
            throw new IOException("Chunk verification failed: " + chunk.chunkPath);
        }
    }
    
    /**
     * Parallel chunking - use with caution on memory-constrained devices
     */
    public List<ChunkInfo> chunkFileParallel(String inputFilePath, String outputDirectory, 
                                           ChunkingCallback callback) {
        List<ChunkInfo> chunks = new ArrayList<>();
        
        try {
            File inputFile = new File(inputFilePath);
            if (!inputFile.exists() || !inputFile.canRead()) {
                if (callback != null) {
                    callback.onError("Input file does not exist or is not readable: " + inputFilePath, null);
                }
                return chunks;
            }
            
            // Create output directory if it doesn't exist
            File outputDir = new File(outputDirectory);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                if (callback != null) {
                    callback.onError("Failed to create output directory: " + outputDirectory, null);
                }
                return chunks;
            }
            
            long fileSize = inputFile.length();
            int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
            
            String baseFileName = getBaseFileName(inputFile.getName());
            
            // Create chunk info objects
            for (int i = 0; i < totalChunks; i++) {
                long startOffset = i * CHUNK_SIZE;
                long chunkSize = Math.min(CHUNK_SIZE, fileSize - startOffset);
                String chunkPath = String.format("%s/%s_chunk_%03d.qmdl", 
                    outputDirectory, baseFileName, i);
                
                chunks.add(new ChunkInfo(inputFilePath, chunkPath, startOffset, 
                    chunkSize, i, totalChunks));
            }
            
            // Submit chunking tasks
            List<Future<Boolean>> futures = new ArrayList<>();
            List<String> completedChunks = new ArrayList<>();
            
            for (ChunkInfo chunk : chunks) {
                Future<Boolean> future = executorService.submit(() -> {
                    try {
                        try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
                            createChunk(raf, chunk, new byte[BUFFER_SIZE]);
                        }
                        
                        if (callback != null) {
                            callback.onChunkCompleted(chunk.chunkPath, chunk.size);
                        }
                        
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error creating chunk: " + chunk.chunkPath, e);
                        if (callback != null) {
                            callback.onError("Failed to create chunk: " + chunk.chunkPath, e);
                        }
                        return false;
                    }
                });
                
                futures.add(future);
            }
            
            // Wait for all tasks to complete
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Boolean success = futures.get(i).get(30, TimeUnit.SECONDS);
                    if (success) {
                        completedChunks.add(chunks.get(i).chunkPath);
                    }
                    
                    if (callback != null) {
                        callback.onProgress(i + 1, chunks.size(), chunks.get(i).chunkPath);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error waiting for chunk completion", e);
                    if (callback != null) {
                        callback.onError("Chunk processing timeout or error", e);
                    }
                }
            }
            
            if (callback != null) {
                callback.onCompleted(completedChunks);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during parallel file chunking", e);
            if (callback != null) {
                callback.onError("Parallel chunking failed: " + e.getMessage(), e);
            }
        }
        
        return chunks;
    }
    
    /**
     * Get the base filename without extension
     */
    private String getBaseFileName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }
    
    /**
     * Cancel ongoing chunking operation
     */
    public void cancel() {
        isCancelled = true;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
    
    /**
     * Check if a file needs chunking
     */
    public static boolean needsChunking(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.length() > CHUNK_SIZE;
    }
    
    /**
     * Get estimated number of chunks for a file
     */
    public static int getEstimatedChunkCount(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return 0;
        }
        return (int) Math.ceil((double) file.length() / CHUNK_SIZE);
    }
    
    /**
     * Clean up resources
     */
    public void shutdown() {
        cancel();
        if (executorService != null && !executorService.isShutdown()) {
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Cleanup chunk files
     */
    public static void cleanupChunks(List<String> chunkPaths) {
        for (String chunkPath : chunkPaths) {
            try {
                File chunkFile = new File(chunkPath);
                if (chunkFile.exists() && !chunkFile.delete()) {
                    Log.w(TAG, "Failed to delete chunk file: " + chunkPath);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error deleting chunk file: " + chunkPath, e);
            }
        }
    }
}
