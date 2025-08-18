package com.example.modiv3;

import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Represents a single diagnostic message parsed from QMDL data
 * Based on the SCAT project's DIAG message structure
 */
public class DiagMessage {
    private static final SimpleDateFormat DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    
    // Message identification
    public final int logId;
    public final long timestamp;
    public final int radioId;
    public final MessageType messageType;
    
    // Raw data
    public final byte[] rawData;
    public final int dataLength;
    
    // Parsed content
    public String parsedContent;
    public String technology; // GSM, WCDMA, LTE, NR, etc.
    public String layer; // PHY, MAC, RLC, RRC, NAS, etc.
    public String direction; // UL, DL, or empty
    
    // Additional metadata
    public String fileName; // Source file name
    public long fileOffset; // Offset in original file
    public int sequenceNumber;
    
    public enum MessageType {
        LOG_PACKET(0x10),
        EVENT_REPORT(0x60),
        EXT_MSG(0x79),
        QSR_EXT_MSG(0x7A),
        QSR4_EXT_MSG(0x7B),
        MULTI_RADIO_CMD(0x98),
        UNKNOWN(0xFF);
        
        public final int value;
        
        MessageType(int value) {
            this.value = value;
        }
        
        public static MessageType fromValue(int value) {
            for (MessageType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
    
    public DiagMessage(int logId, long timestamp, int radioId, MessageType messageType, 
                      byte[] rawData, int dataLength) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.radioId = radioId;
        this.messageType = messageType;
        this.rawData = rawData != null ? rawData.clone() : new byte[0];
        this.dataLength = dataLength;
        this.parsedContent = "";
        this.technology = "";
        this.layer = "";
        this.direction = "";
        this.fileName = "";
        this.fileOffset = 0;
        this.sequenceNumber = 0;
    }
    
    /**
     * Convert to JSON representation
     */
    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            
            // Basic information
            json.put("logId", String.format("0x%04X", logId));
            json.put("timestamp", timestamp);
            json.put("timestampFormatted", formatTimestamp(timestamp));
            json.put("radioId", radioId);
            json.put("messageType", messageType.name());
            json.put("messageTypeValue", String.format("0x%02X", messageType.value));
            
            // Content
            json.put("technology", technology);
            json.put("layer", layer);
            json.put("direction", direction);
            json.put("parsedContent", parsedContent);
            
            // Raw data (limited to prevent huge JSON)
            if (rawData != null && rawData.length > 0) {
                int maxRawBytes = Math.min(64, rawData.length); // Limit to 64 bytes
                StringBuilder hexData = new StringBuilder();
                for (int i = 0; i < maxRawBytes; i++) {
                    if (i > 0 && i % 16 == 0) hexData.append("\n");
                    hexData.append(String.format("%02X ", rawData[i]));
                }
                if (rawData.length > maxRawBytes) {
                    hexData.append("... (").append(rawData.length - maxRawBytes).append(" more bytes)");
                }
                json.put("rawDataHex", hexData.toString().trim());
            }
            
            json.put("dataLength", dataLength);
            
            // Metadata
            json.put("fileName", fileName);
            json.put("fileOffset", fileOffset);
            json.put("sequenceNumber", sequenceNumber);
            
            return json;
            
        } catch (JSONException e) {
            // Fallback to minimal JSON
            try {
                JSONObject json = new JSONObject();
                json.put("error", "JSON conversion failed: " + e.getMessage());
                json.put("logId", String.format("0x%04X", logId));
                json.put("timestamp", timestamp);
                json.put("messageType", messageType.name());
                return json;
            } catch (JSONException e2) {
                return new JSONObject();
            }
        }
    }
    
    /**
     * Format timestamp for human reading
     */
    private String formatTimestamp(long timestamp) {
        try {
            // Convert QXDM timestamp (if applicable) or use as milliseconds
            Date date = new Date(timestamp);
            return DATE_FORMAT.format(date);
        } catch (Exception e) {
            return "Invalid timestamp: " + timestamp;
        }
    }
    
    /**
     * Create a compact string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[0x%04X] ", logId));
        sb.append(formatTimestamp(timestamp)).append(" ");
        sb.append("R").append(radioId).append(" ");
        sb.append(messageType.name()).append(" ");
        
        if (!technology.isEmpty()) {
            sb.append(technology).append(" ");
        }
        if (!layer.isEmpty()) {
            sb.append(layer).append(" ");
        }
        if (!direction.isEmpty()) {
            sb.append(direction).append(" ");
        }
        
        if (!parsedContent.isEmpty()) {
            // Limit content length for display
            String content = parsedContent.length() > 100 ? 
                parsedContent.substring(0, 97) + "..." : parsedContent;
            sb.append("\"").append(content).append("\"");
        } else {
            sb.append("(").append(dataLength).append(" bytes)");
        }
        
        return sb.toString();
    }
    
    /**
     * Create a detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DiagMessage Details ===\n");
        sb.append("Log ID: 0x").append(String.format("%04X", logId)).append("\n");
        sb.append("Timestamp: ").append(formatTimestamp(timestamp)).append(" (").append(timestamp).append(")\n");
        sb.append("Radio ID: ").append(radioId).append("\n");
        sb.append("Message Type: ").append(messageType.name()).append(" (0x").append(String.format("%02X", messageType.value)).append(")\n");
        sb.append("Technology: ").append(technology.isEmpty() ? "Unknown" : technology).append("\n");
        sb.append("Layer: ").append(layer.isEmpty() ? "Unknown" : layer).append("\n");
        sb.append("Direction: ").append(direction.isEmpty() ? "N/A" : direction).append("\n");
        sb.append("Data Length: ").append(dataLength).append(" bytes\n");
        
        if (!fileName.isEmpty()) {
            sb.append("Source File: ").append(fileName).append("\n");
            sb.append("File Offset: ").append(fileOffset).append("\n");
        }
        
        if (sequenceNumber > 0) {
            sb.append("Sequence: ").append(sequenceNumber).append("\n");
        }
        
        if (!parsedContent.isEmpty()) {
            sb.append("Parsed Content:\n").append(parsedContent).append("\n");
        }
        
        if (rawData != null && rawData.length > 0) {
            sb.append("Raw Data (hex):\n");
            int maxBytes = Math.min(256, rawData.length); // Show up to 256 bytes
            for (int i = 0; i < maxBytes; i += 16) {
                sb.append(String.format("%08X: ", i));
                
                // Hex bytes
                for (int j = 0; j < 16 && (i + j) < maxBytes; j++) {
                    sb.append(String.format("%02X ", rawData[i + j]));
                }
                
                // Padding for alignment
                for (int j = maxBytes - i; j < 16; j++) {
                    sb.append("   ");
                }
                
                // ASCII representation
                sb.append(" |");
                for (int j = 0; j < 16 && (i + j) < maxBytes; j++) {
                    byte b = rawData[i + j];
                    char c = (b >= 32 && b < 127) ? (char) b : '.';
                    sb.append(c);
                }
                sb.append("|\n");
            }
            
            if (rawData.length > maxBytes) {
                sb.append("... (").append(rawData.length - maxBytes).append(" more bytes)\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this message is from a specific technology
     */
    public boolean isTechnology(String tech) {
        return technology.equalsIgnoreCase(tech);
    }
    
    /**
     * Check if this message is from a specific layer
     */
    public boolean isLayer(String layerName) {
        return layer.equalsIgnoreCase(layerName);
    }
    
    /**
     * Check if this message is uplink
     */
    public boolean isUplink() {
        return "UL".equalsIgnoreCase(direction);
    }
    
    /**
     * Check if this message is downlink
     */
    public boolean isDownlink() {
        return "DL".equalsIgnoreCase(direction);
    }
    
    /**
     * Get a short summary for UI display
     */
    public String getShortSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("0x%04X", logId));
        
        if (!technology.isEmpty()) {
            sb.append(" ").append(technology);
        }
        if (!layer.isEmpty()) {
            sb.append("-").append(layer);
        }
        if (!direction.isEmpty()) {
            sb.append(" ").append(direction);
        }
        
        return sb.toString();
    }
    
    /**
     * Builder pattern for creating DiagMessage instances
     */
    public static class Builder {
        private int logId;
        private long timestamp;
        private int radioId;
        private MessageType messageType = MessageType.UNKNOWN;
        private byte[] rawData;
        private int dataLength;
        private String parsedContent = "";
        private String technology = "";
        private String layer = "";
        private String direction = "";
        private String fileName = "";
        private long fileOffset = 0;
        private int sequenceNumber = 0;
        
        public Builder setLogId(int logId) {
            this.logId = logId;
            return this;
        }
        
        public Builder setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder setRadioId(int radioId) {
            this.radioId = radioId;
            return this;
        }
        
        public Builder setMessageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }
        
        public Builder setRawData(byte[] rawData) {
            this.rawData = rawData;
            this.dataLength = rawData != null ? rawData.length : 0;
            return this;
        }
        
        public Builder setParsedContent(String parsedContent) {
            this.parsedContent = parsedContent != null ? parsedContent : "";
            return this;
        }
        
        public Builder setTechnology(String technology) {
            this.technology = technology != null ? technology : "";
            return this;
        }
        
        public Builder setLayer(String layer) {
            this.layer = layer != null ? layer : "";
            return this;
        }
        
        public Builder setDirection(String direction) {
            this.direction = direction != null ? direction : "";
            return this;
        }
        
        public Builder setFileName(String fileName) {
            this.fileName = fileName != null ? fileName : "";
            return this;
        }
        
        public Builder setFileOffset(long fileOffset) {
            this.fileOffset = fileOffset;
            return this;
        }
        
        public Builder setSequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }
        
        public DiagMessage build() {
            DiagMessage message = new DiagMessage(logId, timestamp, radioId, messageType, rawData, dataLength);
            message.parsedContent = this.parsedContent;
            message.technology = this.technology;
            message.layer = this.layer;
            message.direction = this.direction;
            message.fileName = this.fileName;
            message.fileOffset = this.fileOffset;
            message.sequenceNumber = this.sequenceNumber;
            return message;
        }
    }
}
