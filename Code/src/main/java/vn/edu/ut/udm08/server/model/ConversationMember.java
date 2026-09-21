package vn.edu.ut.udm08.server.model;

import java.sql.Timestamp;

public class ConversationMember {
    private String convId;
    private long userId;
    private Timestamp joinedAt;
    private String role;

    public ConversationMember() {}

    public ConversationMember(String convId, long userId, String role) {
        this.convId = convId;
        this.userId = userId;
        this.role = role;
    }

    public String getConvId() { return convId; }
    public void setConvId(String convId) { this.convId = convId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}