package vn.edu.ut.udm08.client.network;

import java.util.ArrayList;
import java.util.List;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class MessageHistoryPage {
    private final String requestId;
    private final String convId;
    private final List<ProtocolMessage> messages;
    private final String nextCursor;
    private final boolean hasMore;

    public MessageHistoryPage(String requestId, String convId, List<ProtocolMessage> messages,
                              String nextCursor, boolean hasMore) {
        this.requestId = requestId;
        this.convId = convId;
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getConvId() {
        return convId;
    }

    public List<ProtocolMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public boolean isEmptyAndComplete() {
        return messages.isEmpty() && !hasMore;
    }
}
