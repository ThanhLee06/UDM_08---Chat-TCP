package vn.edu.ut.udm08.client.network;

import java.util.ArrayList;
import java.util.List;
import vn.edu.ut.udm08.shared.model.ConversationSummary;

public class ConversationListResult {
    private final String requestId;
    private final List<ConversationSummary> conversations;

    public ConversationListResult(String requestId, List<ConversationSummary> conversations) {
        this.requestId = requestId;
        this.conversations = conversations == null ? new ArrayList<>() : new ArrayList<>(conversations);
    }

    public String getRequestId() {
        return requestId;
    }

    public List<ConversationSummary> getConversations() {
        return new ArrayList<>(conversations);
    }
}
