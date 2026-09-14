package vn.edu.ut.udm08.client.network;

import vn.edu.ut.udm08.shared.model.ConversationSummary;

public class OpenDmResult {
    private final String requestId;
    private final String targetUserId;
    private final ConversationSummary conversation;

    public OpenDmResult(String requestId, String targetUserId, ConversationSummary conversation) {
        this.requestId = requestId;
        this.targetUserId = targetUserId;
        this.conversation = conversation;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public ConversationSummary getConversation() {
        return conversation;
    }
}
