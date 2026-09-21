package vn.edu.ut.udm08.server.model;

import java.util.List;

public class MessagePagedResult {
    private final List<ChatMessage> messages;
    private final Long nextCursor;
    private final boolean hasMore;

    public MessagePagedResult(List<ChatMessage> messages, Long nextCursor, boolean hasMore) {
        this.messages = messages;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<ChatMessage> getMessages() { return messages; }
    public Long getNextCursor() { return nextCursor; }
    public boolean isHasMore() { return hasMore; }
}