package com.example.modiv3;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QMDL file parser based on SCAT project's Qualcomm parser
 * Handles parsing of diagnostic messages from QMDL files with HDLC framing
 */
public class QmdlParser {
    private static final String TAG = "QmdlParser";
    
    // DIAG command codes (from SCAT diagcmd)
    private static final int DIAG_LOG_F = 0x10;
    private static final int DIAG_EVENT_REPORT_F = 0x60;
    private static final int DIAG_EXT_MSG_F = 0x79;
    private static final int DIAG_QSR_EXT_MSG_TERSE_F = 0x7A;
    private static final int DIAG_QSR4_EXT_MSG_TERSE_F = 0x7B;
    private static final int DIAG_MULTI_RADIO_CMD_F = 0x98;
    private static final int DIAG_VERNO_F = 0x00;
    private static final int DIAG_EXT_BUILD_ID_F = 0x7C;
    private static final int DIAG_LOG_CONFIG_F = 0x73;
    private static final int DIAG_EXT_MSG_CONFIG_F = 0x7D;
    
    // HDLC frame delimiter
    private static final byte HDLC_FLAG = 0x7E;
    private static final byte HDLC_ESCAPE = 0x7D;
    private static final byte HDLC_ESCAPE_MASK = 0x20;
    
    // Buffer size for file reading
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB
    private static final int MAX_PACKET_SIZE = 16 * 1024; // 16KB max packet
    
    private volatile boolean shouldStop = false;
    private AtomicInteger sequenceCounter = new AtomicInteger(0);
    
    public interface ParseCallback {
        void onProgress(long bytesProcessed, long totalBytes, String currentFile);
        void onMessageParsed(DiagMessage message);
        void onError(String error, Exception e);
        void onCompleted(int totalMessages, long parseTimeMs);
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
    
    /**
     * Parse a QMDL file and return all diagnostic messages
     */
    public ParseResult parseFile(String filePath, ParseCallback callback) {
        long startTime = System.currentTimeMillis();
        List<DiagMessage> messages = new ArrayList<>();
        String fileName = new File(filePath).getName();
        
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.canRead()) {
                String error = "File does not exist or is not readable: " + filePath;
                if (callback != null) callback.onError(error, null);
                return ParseResult.error(error, fileName);
            }
            
            long fileSize = file.length();
            Log.d(TAG, String.format("Parsing QMDL file: %s (%.2f MB)", 
                fileName, fileSize / (1024.0 * 1024.0)));
            
            // Try normal file access first, then root access if needed
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                messages = parseFileContent(raf, fileSize, fileName, callback);
            } catch (IOException e) {
                Log.w(TAG, "Normal file access failed for " + fileName + ", trying root access", e);
                
                // Try root access method
                try {
                    byte[] fileData = RootFileReader.readFile(filePath);
                    messages = parseFileData(fileData, fileName, callback);
                } catch (IOException rootError) {
                    throw new IOException("Both normal and root access failed: " + rootError.getMessage(), rootError);
                }
            }
            
            long parseTime = System.currentTimeMillis() - startTime;
            
            if (callback != null) {
                callback.onCompleted(messages.size(), parseTime);
            }
            
            Log.d(TAG, String.format("Parsed %d messages from %s in %d ms", 
                messages.size(), fileName, parseTime));
            
            return ParseResult.success(messages, parseTime, fileName);
            
        } catch (Exception e) {
            long parseTime = System.currentTimeMillis() - startTime;
            String error = "Failed to parse file: " + e.getMessage();
            Log.e(TAG, error, e);
            
            if (callback != null) {
                callback.onError(error, e);
            }
            
            return ParseResult.error(error, fileName);
        }
    }
    
    /**
     * Parse file content using HDLC framing
     */
    private List<DiagMessage> parseFileContent(RandomAccessFile raf, long fileSize, 
                                             String fileName, ParseCallback callback) throws IOException {
        List<DiagMessage> messages = new ArrayList<>();
        byte[] buffer = new byte[BUFFER_SIZE];
        byte[] packetBuffer = new byte[MAX_PACKET_SIZE];
        int packetLength = 0;
        boolean inPacket = false;
        long bytesProcessed = 0;
        long lastProgressUpdate = 0;
        
        // Check for QMDL header and skip it if present
        byte[] headerCheck = new byte[32];
        raf.read(headerCheck);
        raf.seek(0); // Reset to beginning
        
        int startOffset = detectQmdlHeaderSize(headerCheck);
        if (startOffset > 0) {
            Log.d(TAG, "Detected QMDL header, skipping " + startOffset + " bytes");
            raf.seek(startOffset);
            bytesProcessed = startOffset;
        }
        
        while (bytesProcessed < fileSize && !shouldStop) {
            int bytesRead = raf.read(buffer);
            if (bytesRead <= 0) {
                break;
            }
            
            for (int i = 0; i < bytesRead && !shouldStop; i++) {
                byte currentByte = buffer[i];
                
                if (currentByte == HDLC_FLAG) {
                    if (inPacket && packetLength > 0) {
                        // End of packet - process it
                        try {
                            DiagMessage message = parsePacket(packetBuffer, packetLength, fileName, bytesProcessed - packetLength);
                            if (message != null) {
                                message.sequenceNumber = sequenceCounter.incrementAndGet();
                                messages.add(message);
                                
                                if (callback != null) {
                                    callback.onMessageParsed(message);
                                }
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Error parsing packet at offset " + (bytesProcessed - packetLength) + 
                                ", length " + packetLength + ": " + e.getMessage());
                            
                            // Create a fallback message for this failed packet
                            try {
                                DiagMessage fallbackMsg = createFallbackMessage(packetBuffer, packetLength, 
                                    fileName, bytesProcessed - packetLength, e.getMessage());
                                if (fallbackMsg != null) {
                                    fallbackMsg.sequenceNumber = sequenceCounter.incrementAndGet();
                                    messages.add(fallbackMsg);
                                    
                                    if (callback != null) {
                                        callback.onMessageParsed(fallbackMsg);
                                    }
                                }
                            } catch (Exception fallbackError) {
                                Log.w(TAG, "Failed to create fallback message", fallbackError);
                            }
                        }
                        
                        packetLength = 0;
                    }
                    inPacket = true;
                } else if (inPacket) {
                    if (packetLength < MAX_PACKET_SIZE) {
                        packetBuffer[packetLength++] = currentByte;
                    } else {
                        // Packet too large, reset
                        Log.w(TAG, "Packet too large at offset " + bytesProcessed + ", resetting");
                        packetLength = 0;
                        inPacket = false;
                    }
                }
                
                bytesProcessed++;
            }
            
            // Update progress periodically
            if (callback != null && (bytesProcessed - lastProgressUpdate) > (fileSize / 100)) {
                callback.onProgress(bytesProcessed, fileSize, fileName);
                lastProgressUpdate = bytesProcessed;
            }
        }
        
        return messages;
    }
    
    /**
     * Parse file data from byte array (for root access method)
     */
    private List<DiagMessage> parseFileData(byte[] fileData, String fileName, ParseCallback callback) {
        List<DiagMessage> messages = new ArrayList<>();
        byte[] packetBuffer = new byte[MAX_PACKET_SIZE];
        int packetLength = 0;
        boolean inPacket = false;
        long bytesProcessed = 0;
        long lastProgressUpdate = 0;
        long totalBytes = fileData.length;
        
        // Check for QMDL header and skip it if present
        int startOffset = detectQmdlHeaderSize(fileData);
        if (startOffset > 0) {
            Log.d(TAG, "Detected QMDL header, skipping " + startOffset + " bytes");
        }
        
        // Parse QMDL format: look for packet headers followed by HDLC frames
        int i = startOffset;
        while (i < fileData.length - 4 && !shouldStop) {
            // Look for QMDL packet header pattern (0x99 0x02)
            if ((fileData[i] & 0xFF) == 0x99 && (fileData[i + 1] & 0xFF) == 0x02) {
                // Found potential QMDL packet header
                int packetHeaderSize = 4; // Minimum header size
                
                // Try to find the HDLC frame after the header
                int hdlcStart = -1;
                for (int j = i + packetHeaderSize; j < Math.min(i + 50, fileData.length); j++) {
                    if ((fileData[j] & 0xFF) == HDLC_FLAG) {
                        hdlcStart = j;
                        break;
                    }
                }
                
                if (hdlcStart != -1) {
                    // Find the end of this HDLC frame
                    int hdlcEnd = -1;
                    for (int j = hdlcStart + 1; j < Math.min(hdlcStart + MAX_PACKET_SIZE, fileData.length); j++) {
                        if ((fileData[j] & 0xFF) == HDLC_FLAG) {
                            hdlcEnd = j;
                            break;
                        }
                    }
                    
                    if (hdlcEnd != -1) {
                        // Extract the HDLC frame content (without the 0x7E delimiters)
                        int frameLength = hdlcEnd - hdlcStart - 1;
                        if (frameLength > 0 && frameLength < MAX_PACKET_SIZE) {
                            byte[] frameData = new byte[frameLength];
                            System.arraycopy(fileData, hdlcStart + 1, frameData, 0, frameLength);
                            
                            try {
                                DiagMessage message = parsePacket(frameData, frameLength, fileName, hdlcStart);
                                if (message != null) {
                                    message.sequenceNumber = sequenceCounter.incrementAndGet();
                                    messages.add(message);
                                    
                                    if (callback != null) {
                                        callback.onMessageParsed(message);
                                    }
                                }
                            } catch (Exception e) {
                                Log.d(TAG, "Error parsing QMDL packet at offset " + hdlcStart + 
                                    ", length " + frameLength + ": " + e.getMessage());
                                
                                // Create a fallback message
                                try {
                                    DiagMessage fallbackMsg = createFallbackMessage(frameData, frameLength, 
                                        fileName, hdlcStart, e.getMessage());
                                    if (fallbackMsg != null) {
                                        fallbackMsg.sequenceNumber = sequenceCounter.incrementAndGet();
                                        messages.add(fallbackMsg);
                                        
                                        if (callback != null) {
                                            callback.onMessageParsed(fallbackMsg);
                                        }
                                    }
                                } catch (Exception fallbackError) {
                                    Log.w(TAG, "Failed to create fallback message", fallbackError);
                                }
                            }
                        }
                        
                        // Move to after this packet
                        i = hdlcEnd + 1;
                    } else {
                        // No closing HDLC frame found, skip this
                        i++;
                    }
                } else {
                    // No HDLC frame found after header, skip
                    i++;
                }
            } else {
                i++;
            }
            
            bytesProcessed = i;
            
            // Update progress periodically
            if (callback != null && (bytesProcessed - lastProgressUpdate) > (totalBytes / 100)) {
                callback.onProgress(bytesProcessed, totalBytes, fileName);
                lastProgressUpdate = bytesProcessed;
            }
        }
        
        return messages;
    }
    
    /**
     * Parse a single DIAG packet
     */
    private DiagMessage parsePacket(byte[] packet, int length, String fileName, long fileOffset) {
        if (length < 1) {
            return null; // Too short for any packet
        }
        
        try {
            // Remove HDLC encoding
            byte[] decodedPacket = hdlcDecode(packet, length);
            if (decodedPacket == null || decodedPacket.length < 1) {
                return null;
            }
            
            // Check and remove CRC if present (be more flexible)
            if (decodedPacket.length >= 3) {
                // Try to detect if CRC is present by checking packet structure
                int cmdCode = decodedPacket[0] & 0xFF;
                
                // For known command codes, assume CRC is present
                if (cmdCode == DIAG_LOG_F || cmdCode == DIAG_EVENT_REPORT_F || 
                    cmdCode == DIAG_EXT_MSG_F || cmdCode == DIAG_MULTI_RADIO_CMD_F) {
                    if (decodedPacket.length > 2) {
                        byte[] noCrcPacket = new byte[decodedPacket.length - 2];
                        System.arraycopy(decodedPacket, 0, noCrcPacket, 0, noCrcPacket.length);
                        decodedPacket = noCrcPacket;
                    }
                }
            }
            
            if (decodedPacket.length == 0) {
                return null;
            }
            
            // Parse based on command code
            int cmdCode = decodedPacket[0] & 0xFF;
            DiagMessage.MessageType messageType = DiagMessage.MessageType.fromValue(cmdCode);
            
            return parseByMessageType(decodedPacket, messageType, fileName, fileOffset);
            
        } catch (Exception e) {
            // Create a fallback message for unparseable packets
            Log.d(TAG, "Packet parse error at offset " + fileOffset + ", creating fallback message");
            return createFallbackMessage(packet, length, fileName, fileOffset, e.getMessage());
        }
    }
    
    /**
     * Create a fallback message for packets that can't be parsed
     */
    private DiagMessage createFallbackMessage(byte[] packet, int length, String fileName, 
                                            long fileOffset, String errorMsg) {
        try {
            // Try to get at least the command code
            int cmdCode = 0;
            if (length > 0) {
                cmdCode = packet[0] & 0xFF;
            }
            
            DiagMessage.Builder builder = new DiagMessage.Builder()
                .setLogId(cmdCode)
                .setTimestamp(System.currentTimeMillis())
                .setRadioId(0)
                .setMessageType(DiagMessage.MessageType.UNKNOWN)
                .setRawData(packet)
                .setFileName(fileName)
                .setFileOffset(fileOffset)
                .setTechnology("UNKNOWN")
                .setLayer("RAW")
                .setParsedContent("Unparseable packet (cmd: 0x" + String.format("%02X", cmdCode) + 
                    ", len: " + length + ", error: " + errorMsg + ")");
            
            return builder.build();
        } catch (Exception e) {
            Log.w(TAG, "Failed to create fallback message", e);
            return null;
        }
    }
    
    /**
     * Parse packet based on message type
     */
    private DiagMessage parseByMessageType(byte[] packet, DiagMessage.MessageType messageType, 
                                         String fileName, long fileOffset) {
        try {
            switch (messageType) {
                case LOG_PACKET:
                    return parseLogPacket(packet, fileName, fileOffset);
                case EVENT_REPORT:
                    return parseEventReport(packet, fileName, fileOffset);
                case EXT_MSG:
                    return parseExtMessage(packet, fileName, fileOffset);
                case QSR_EXT_MSG:
                case QSR4_EXT_MSG:
                    return parseQsrMessage(packet, messageType, fileName, fileOffset);
                case MULTI_RADIO_CMD:
                    return parseMultiRadioCmd(packet, fileName, fileOffset);
                default:
                    return parseGenericMessage(packet, messageType, fileName, fileOffset);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing message type " + messageType, e);
            return parseGenericMessage(packet, messageType, fileName, fileOffset);
        }
    }
    
    /**
     * Parse LOG packet (0x10)
     */
    private DiagMessage parseLogPacket(byte[] packet, String fileName, long fileOffset) {
        if (packet.length < 8) {
            return null; // Minimum viable packet size
        }
        
        try {
            ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
            
            // Parse log header: cmd_code(1) + reserved(1) + length1(2) + length2(2) + log_id(2) + timestamp(8)
            int cmdCode = buffer.get() & 0xFF;
            int reserved = buffer.get() & 0xFF;
            
            // Check if we have enough data for the full header
            if (packet.length < 16) {
                // Partial header - create basic message
                return new DiagMessage.Builder()
                    .setLogId(0)
                    .setTimestamp(System.currentTimeMillis())
                    .setRadioId(0)
                    .setMessageType(DiagMessage.MessageType.LOG_PACKET)
                    .setRawData(packet)
                    .setFileName(fileName)
                    .setFileOffset(fileOffset)
                    .setTechnology("PARTIAL")
                    .setLayer("RAW")
                    .setParsedContent("Partial LOG packet (cmd: 0x" + String.format("%02X", cmdCode) + 
                        ", len: " + packet.length + ")")
                    .build();
            }
            
            int length1 = buffer.getShort() & 0xFFFF;
            int length2 = buffer.getShort() & 0xFFFF;
            int logId = buffer.getShort() & 0xFFFF;
            long timestamp = buffer.getLong();
            
            // Validate lengths
            if (length2 > packet.length || length1 > packet.length) {
                Log.w(TAG, String.format("Invalid packet lengths: length1=%d, length2=%d, packet_len=%d", 
                    length1, length2, packet.length));
                // Still try to parse with available data
            }
            
            // Get remaining data safely
            int remainingBytes = packet.length - 16;
            byte[] logData = new byte[Math.max(0, remainingBytes)];
            if (remainingBytes > 0) {
                buffer.get(logData);
            }
            
            DiagMessage.Builder builder = new DiagMessage.Builder()
                .setLogId(logId)
                .setTimestamp(timestamp)
                .setRadioId(0) // Will be set by multi-radio wrapper if applicable
                .setMessageType(DiagMessage.MessageType.LOG_PACKET)
                .setRawData(packet)
                .setFileName(fileName)
                .setFileOffset(fileOffset);
            
            // Try to determine technology and layer from log ID
            String[] techLayer = determineTechnologyAndLayer(logId);
            builder.setTechnology(techLayer[0]);
            builder.setLayer(techLayer[1]);
            
            // Set basic parsed content
            builder.setParsedContent(String.format("Log ID: 0x%04X, Length: %d, Timestamp: %d", 
                logId, length2, timestamp));
            
            return builder.build();
            
        } catch (Exception e) {
            Log.w(TAG, "Error in parseLogPacket", e);
            return createFallbackMessage(packet, packet.length, fileName, fileOffset, e.getMessage());
        }
    }
    
    /**
     * Parse Event Report (0x60)
     */
    private DiagMessage parseEventReport(byte[] packet, String fileName, long fileOffset) {
        if (packet.length < 3) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int cmdCode = buffer.get() & 0xFF;
        int msgLen = buffer.getShort() & 0xFFFF;
        
        DiagMessage.Builder builder = new DiagMessage.Builder()
            .setLogId(0) // Events don't have log IDs
            .setTimestamp(System.currentTimeMillis()) // Use current time as fallback
            .setRadioId(0)
            .setMessageType(DiagMessage.MessageType.EVENT_REPORT)
            .setRawData(packet)
            .setFileName(fileName)
            .setFileOffset(fileOffset)
            .setTechnology("EVENT")
            .setLayer("SYSTEM")
            .setParsedContent(String.format("Event Report, Message Length: %d", msgLen));
        
        return builder.build();
    }
    
    /**
     * Parse Extended Message (0x79)
     */
    private DiagMessage parseExtMessage(byte[] packet, String fileName, long fileOffset) {
        if (packet.length < 20) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        
        // Parse extended message header
        int cmdCode = buffer.get() & 0xFF;
        int tsType = buffer.get() & 0xFF;
        int numArgs = buffer.get() & 0xFF;
        int dropCnt = buffer.get() & 0xFF;
        long timestamp = buffer.getLong();
        int lineNo = buffer.getShort() & 0xFFFF;
        int subsysId = buffer.getInt();
        int reserved = buffer.getInt();
        
        DiagMessage.Builder builder = new DiagMessage.Builder()
            .setLogId(subsysId)
            .setTimestamp(timestamp)
            .setRadioId(0)
            .setMessageType(DiagMessage.MessageType.EXT_MSG)
            .setRawData(packet)
            .setFileName(fileName)
            .setFileOffset(fileOffset)
            .setTechnology("MSG")
            .setLayer("DEBUG")
            .setParsedContent(String.format("Extended Message, Subsys: 0x%X, Line: %d, Args: %d", 
                subsysId, lineNo, numArgs));
        
        return builder.build();
    }
    
    /**
     * Parse QSR Message (0x7A, 0x7B)
     */
    private DiagMessage parseQsrMessage(byte[] packet, DiagMessage.MessageType messageType, 
                                       String fileName, long fileOffset) {
        DiagMessage.Builder builder = new DiagMessage.Builder()
            .setLogId(0)
            .setTimestamp(System.currentTimeMillis())
            .setRadioId(0)
            .setMessageType(messageType)
            .setRawData(packet)
            .setFileName(fileName)
            .setFileOffset(fileOffset)
            .setTechnology("QSR")
            .setLayer("DEBUG")
            .setParsedContent("QSR Message (requires hash file for decoding)");
        
        return builder.build();
    }
    
    /**
     * Parse Multi-Radio Command (0x98)
     */
    private DiagMessage parseMultiRadioCmd(byte[] packet, String fileName, long fileOffset) {
        if (packet.length < 8) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int cmdCode = buffer.get() & 0xFF;
        int reserved1 = buffer.get() & 0xFF;
        int reserved2 = buffer.getShort() & 0xFFFF;
        int radioId = buffer.getInt();
        
        // Parse nested packet
        byte[] nestedPacket = new byte[packet.length - 8];
        buffer.get(nestedPacket);
        
        if (nestedPacket.length > 0) {
            DiagMessage nestedMessage = parsePacket(nestedPacket, nestedPacket.length, fileName, fileOffset);
            if (nestedMessage != null) {
                // Update radio ID
                DiagMessage.Builder builder = new DiagMessage.Builder()
                    .setLogId(nestedMessage.logId)
                    .setTimestamp(nestedMessage.timestamp)
                    .setRadioId(sanitizeRadioId(radioId))
                    .setMessageType(nestedMessage.messageType)
                    .setRawData(packet) // Use original packet with multi-radio wrapper
                    .setFileName(fileName)
                    .setFileOffset(fileOffset)
                    .setTechnology(nestedMessage.technology)
                    .setLayer(nestedMessage.layer)
                    .setDirection(nestedMessage.direction)
                    .setParsedContent("Radio " + sanitizeRadioId(radioId) + ": " + nestedMessage.parsedContent);
                
                return builder.build();
            }
        }
        
        // Fallback if nested parsing fails
        DiagMessage.Builder builder = new DiagMessage.Builder()
            .setLogId(0)
            .setTimestamp(System.currentTimeMillis())
            .setRadioId(sanitizeRadioId(radioId))
            .setMessageType(DiagMessage.MessageType.MULTI_RADIO_CMD)
            .setRawData(packet)
            .setFileName(fileName)
            .setFileOffset(fileOffset)
            .setParsedContent(String.format("Multi-Radio Command, Radio ID: %d", sanitizeRadioId(radioId)));
        
        return builder.build();
    }
    
    /**
     * Parse generic/unknown message
     */
    private DiagMessage parseGenericMessage(byte[] packet, DiagMessage.MessageType messageType, 
                                          String fileName, long fileOffset) {
        DiagMessage.Builder builder = new DiagMessage.Builder()
            .setLogId(0)
            .setTimestamp(System.currentTimeMillis())
            .setRadioId(0)
            .setMessageType(messageType)
            .setRawData(packet)
            .setFileName(fileName)
            .setFileOffset(fileOffset)
            .setParsedContent(String.format("Generic Message Type: %s (0x%02X)", 
                messageType.name(), messageType.value));
        
        return builder.build();
    }
    
    /**
     * Determine technology and layer from log ID (simplified mapping)
     */
    private String[] determineTechnologyAndLayer(int logId) {
        String technology = "UNKNOWN";
        String layer = "UNKNOWN";
        
        // Simplified mapping based on common log IDs
        if (logId >= 0x4000 && logId <= 0x4FFF) {
            technology = "GSM";
        } else if (logId >= 0x4130 && logId <= 0x41FF) {
            technology = "WCDMA";
        } else if (logId >= 0xB000 && logId <= 0xBFFF) {
            technology = "LTE";
        } else if (logId >= 0xB800 && logId <= 0xB8FF) {
            technology = "NR";
        }
        
        // Layer determination (simplified)
        int layerCode = (logId >> 8) & 0xFF;
        switch (layerCode) {
            case 0x40: layer = "L1"; break;
            case 0x41: layer = "L2"; break;
            case 0x42: layer = "RRC"; break;
            case 0x43: layer = "NAS"; break;
            case 0xB0: layer = "PHY"; break;
            case 0xB1: layer = "MAC"; break;
            case 0xB2: layer = "RLC"; break;
            default: layer = "UNKNOWN"; break;
        }
        
        return new String[]{technology, layer};
    }
    
    /**
     * HDLC decode (remove escape sequences)
     */
    private byte[] hdlcDecode(byte[] encoded, int length) {
        byte[] decoded = new byte[length];
        int decodedLength = 0;
        
        for (int i = 0; i < length; i++) {
            if (encoded[i] == HDLC_ESCAPE && i + 1 < length) {
                // Escape sequence
                decoded[decodedLength++] = (byte) (encoded[++i] ^ HDLC_ESCAPE_MASK);
            } else if (encoded[i] != HDLC_FLAG) {
                decoded[decodedLength++] = encoded[i];
            }
        }
        
        if (decodedLength == 0) {
            return null;
        }
        
        // Return array with correct size
        byte[] result = new byte[decodedLength];
        System.arraycopy(decoded, 0, result, 0, decodedLength);
        return result;
    }
    
    /**
     * Sanitize radio ID (from SCAT)
     */
    private int sanitizeRadioId(int radioId) {
        if (radioId <= 0) {
            return 0;
        } else if (radioId > 2) {
            return 1;
        } else {
            return radioId - 1;
        }
    }
    
    /**
     * Stop parsing operation
     */
    public void stop() {
        shouldStop = true;
    }
    
    /**
     * Reset parser state
     */
    public void reset() {
        shouldStop = false;
        sequenceCounter.set(0);
    }
    
    /**
     * Detect QMDL header size by looking for the first HDLC frame (0x7E)
     */
    private int detectQmdlHeaderSize(byte[] data) {
        if (data == null || data.length < 4) {
            return 0;
        }
        
        // Look for the first HDLC frame marker (0x7E)
        for (int i = 0; i < Math.min(data.length, 1024); i++) { // Check first 1KB
            if ((data[i] & 0xFF) == HDLC_FLAG) {
                Log.d(TAG, "Found first HDLC frame at offset " + i);
                return i;
            }
        }
        
        // If no HDLC frame found in first 1KB, assume no header
        Log.d(TAG, "No HDLC frame found in first 1KB, assuming no header");
        return 0;
    }
}
