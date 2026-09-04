package vn.edu.ut.udm08.client.network.event;

/**
 * Sự kiện Server xác nhận đã gửi tin nhắn thành công (CHAT_OK).
 */
public class MessageSentSuccessEvent implements ChatEvent {

    private final String convId;
    private final String messageId;
    private final long timestamp;

    public MessageSentSuccessEvent(String convId, String messageId) {
        this.convId = convId;
        this.messageId = messageId;
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

    public String getMessageId() {
        return messageId;
    }
}
