package vn.edu.ut.udm08.client.network.event;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

/**
 * Sự kiện nhận được tin nhắn mới từ Server (CHAT, REPLY, FORWARD).
 */
public class MessageReceivedEvent implements ChatEvent {

    private final String convId;
    private final ProtocolMessage message;
    private final long timestamp;

    public MessageReceivedEvent(String convId, ProtocolMessage message) {
        this.convId = convId;
        this.message = message;
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

    public ProtocolMessage getMessage() {
        return message;
    }
}
