package com.example.modiv3;

public class SignalingEvent {
    public enum Technology {
        LTE("#4CAF50"), NR("#FF5722"), UMTS("#2196F3"), GSM("#9C27B0");
        
        private final String color;
        
        Technology(String color) {
            this.color = color;
        }
        
        public String getColor() {
            return color;
        }
    }
    
    public enum Direction {
        UP("↑", "#4CAF50"), DOWN("↓", "#2196F3");
        
        private final String arrow;
        private final String color;
        
        Direction(String arrow, String color) {
            this.arrow = arrow;
            this.color = color;
        }
        
        public String getArrow() {
            return arrow;
        }
        
        public String getColor() {
            return color;
        }
    }
    
    private Technology technology;
    private Direction direction;
    private String messageTitle;
    private String description;
    private String timestamp;
    private String fullData;
    
    public SignalingEvent(Technology technology, Direction direction, String messageTitle, 
                         String description, String timestamp, String fullData) {
        this.technology = technology;
        this.direction = direction;
        this.messageTitle = messageTitle;
        this.description = description;
        this.timestamp = timestamp;
        this.fullData = fullData;
    }
    
    // Getters
    public Technology getTechnology() { return technology; }
    public Direction getDirection() { return direction; }
    public String getMessageTitle() { return messageTitle; }
    public String getDescription() { return description; }
    public String getTimestamp() { return timestamp; }
    public String getFullData() { return fullData; }
}
