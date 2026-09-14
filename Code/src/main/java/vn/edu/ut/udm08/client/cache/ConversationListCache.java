package vn.edu.ut.udm08.client.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.model.MessageSendStatus;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class ConversationListCache {
    private String currentUser;
    private ConversationListListener listener;
    private final Map<String, ConversationSummary> conversations = new HashMap<>();

    public synchronized void setCurrentUser(String username) {
        if (username == null || username.isBlank()) {
            clear();
            return;
        }
        String cleanUsername = username.trim();
        if (!cleanUsername.equals(currentUser)) {
            currentUser = cleanUsername;
            conversations.clear();
        }
    }

    public synchronized void setListener(ConversationListListener listener) {
        this.listener = listener;
    }

    public synchronized void clear() {
        currentUser = null;
        conversations.clear();
    }

    public void replaceAll(List<ConversationSummary> newConversations) {
        synchronized (this) {
            conversations.clear();
            if (newConversations != null) {
                for (ConversationSummary conversation : newConversations) {
                    if (conversation != null && conversation.convId != null && !conversation.convId.isBlank()) {
                        conversations.put(conversation.convId, conversation);
                    }
                }
            }
        }
        notifyChanged();
    }

    public void upsert(ConversationSummary conversation) {
        if (conversation == null || conversation.convId == null || conversation.convId.isBlank()) {
            return;
        }
        synchronized (this) {
            conversations.put(conversation.convId, conversation);
        }
        notifyChanged();
    }

    public void updateLastMessage(ProtocolMessage message) {
        if (message == null || message.sendStatus == MessageSendStatus.FAILED) {
            return;
        }
        String convId = resolveConversationId(message);
        if (convId == null) {
            return;
        }
        synchronized (this) {
            ConversationSummary summary = conversations.get(convId);
            if (summary == null) {
                summary = new ConversationSummary();
                summary.convId = convId;
                summary.chatType = "DM";
                summary.displayName = convId;
                conversations.put(convId, summary);
            }
            summary.lastMessage = message.content;
            summary.lastActivity = message.timestamp;
        }
        notifyChanged();
    }

    public synchronized List<ConversationSummary> getConversations() {
        List<ConversationSummary> result = new ArrayList<>(conversations.values());
        result.sort((left, right) -> Long.compare(valueOf(right.lastActivity), valueOf(left.lastActivity)));
        return result;
    }

    private String resolveConversationId(ProtocolMessage message) {
        if (message.convId != null && !message.convId.isBlank()) {
            return message.convId.trim();
        }
        if (message.target != null && !message.target.isBlank()) {
            return message.target.trim();
        }
        if (message.sender != null && !message.sender.isBlank()) {
            return message.sender.trim();
        }
        return null;
    }

    private static long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    private void notifyChanged() {
        ConversationListListener currentListener;
        List<ConversationSummary> snapshot;
        synchronized (this) {
            currentListener = listener;
            snapshot = getConversations();
        }
        if (currentListener != null) {
            currentListener.onConversationListChanged(snapshot);
        }
    }
}
