package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import java.util.Collections;
import java.util.List;

/**
 * Ket qua phan trang tin nhan (ST-102 / ST-113)
 */
public class MessagePagedResult {
    private final List<ProtocolMessage> messages;
    private final String nextCursor;
    private final boolean hasMore;

    public MessagePagedResult(List<ProtocolMessage> messages, String nextCursor, boolean hasMore) {
        this.messages = messages != null ? messages : Collections.emptyList();
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<ProtocolMessage> getMessages() {
        return messages;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
