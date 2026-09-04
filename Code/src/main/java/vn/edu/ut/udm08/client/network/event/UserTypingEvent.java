package vn.edu.ut.udm08.client.network.event;

/**
 * Sự kiện thông báo người dùng đang gõ tin nhắn (Typing Indicator).
 */
public class UserTypingEvent implements ChatEvent {

    private final String convId;
    private final String username;
    private final boolean isTyping;
    private final long timestamp;

    public UserTypingEvent(String convId, String username, boolean isTyping) {
        this.convId = convId;
        this.username = username;
        this.isTyping = isTyping;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getConvId() {
        return convId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public boolean isTyping() {
        return isTyping;
    }
}
