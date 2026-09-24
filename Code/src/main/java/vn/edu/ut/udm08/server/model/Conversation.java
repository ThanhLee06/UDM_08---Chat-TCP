package vn.edu.ut.udm08.server.model;

import java.sql.Timestamp;

public class Conversation {
    private String convId;
    private String type;
    private String name;
    private Timestamp createdAt;
    private String lastMessagePreview;
    private Long lastActivity;

    public Conversation() {}

    public Conversation(String convId, String type, String name) {
        this.convId = convId;
        this.type = type;
        this.name = name;
    }

    public String getConvId() { return convId; }
    public void setConvId(String convId) { this.convId = convId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
    public Long getLastActivity() { return lastActivity; }
    public void setLastActivity(Long lastActivity) { this.lastActivity = lastActivity; }
}
