package vn.edu.ut.udm08.client.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

/**
 * Cache tap trung cho tin nhan phia Client.
 * Moi tai khoan co vung du lieu rieng, moi hoi thoai chong trung theo messageId.
 */
public class ConversationCache {
    private String currentUser;
    private ConversationCacheListener listener;
    private final Map<String, ConversationMessages> conversations = new HashMap<>();

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

    public synchronized String getCurrentUser() {
        return currentUser;
    }

    public synchronized void setListener(ConversationCacheListener listener) {
        this.listener = listener;
    }

    public synchronized void clear() {
        currentUser = null;
        conversations.clear();
    }

    public void addRealtimeMessage(ProtocolMessage message) {
        String convId;
        synchronized (this) {
            convId = resolveConversationId(message);
            if (convId == null) {
                return;
            }
            ConversationMessages conversation = getOrCreateConversation(convId);
            conversation.upsertRealtime(message);
        }
        notifyChanged(convId);
    }

    public void addHistoryPage(String convId, List<ProtocolMessage> messages) {
        if (convId == null || convId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }
        String cleanConvId = convId.trim();
        synchronized (this) {
            ConversationMessages conversation = getOrCreateConversation(cleanConvId);
            conversation.mergeHistory(messages);
        }
        notifyChanged(cleanConvId);
    }

    public void updateMessageStatus(ProtocolMessage message) {
        String convId;
        synchronized (this) {
            convId = resolveConversationId(message);
            if (convId == null) {
                return;
            }
            ConversationMessages conversation = getOrCreateConversation(convId);
            conversation.upsertRealtime(message);
        }
        notifyChanged(convId);
    }

    public synchronized List<ProtocolMessage> getMessages(String convId) {
        if (convId == null) {
            return new ArrayList<>();
        }
        ConversationMessages conversation = conversations.get(convId);
        if (conversation == null) {
            return new ArrayList<>();
        }
        return conversation.copyMessages();
    }

    private ConversationMessages getOrCreateConversation(String convId) {
        ConversationMessages conversation = conversations.get(convId);
        if (conversation == null) {
            conversation = new ConversationMessages();
            conversations.put(convId, conversation);
        }
        return conversation;
    }

    private String resolveConversationId(ProtocolMessage message) {
        if (message == null) {
            return null;
        }
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

    private void notifyChanged(String convId) {
        ConversationCacheListener currentListener;
        List<ProtocolMessage> snapshot;
        synchronized (this) {
            currentListener = listener;
            snapshot = getMessages(convId);
        }
        if (currentListener != null) {
            currentListener.onConversationChanged(convId, snapshot);
        }
    }

    private static class ConversationMessages {
        private final List<ProtocolMessage> orderedMessages = new ArrayList<>();
        private final Map<String, ProtocolMessage> messagesById = new HashMap<>();

        private void mergeHistory(List<ProtocolMessage> historyMessages) {
            List<ProtocolMessage> newMessages = new ArrayList<>();
            for (ProtocolMessage message : historyMessages) {
                if (message == null) {
                    continue;
                }
                String messageId = message.messageId;
                if (messageId == null || messageId.isBlank()) {
                    newMessages.add(message);
                    continue;
                }
                ProtocolMessage existing = messagesById.get(messageId);
                if (existing == null) {
                    messagesById.put(messageId, message);
                    newMessages.add(message);
                } else {
                    mergeInto(existing, message);
                }
            }
            orderedMessages.addAll(0, newMessages);
        }

        private void upsertRealtime(ProtocolMessage message) {
            if (message == null) {
                return;
            }
            String messageId = message.messageId;
            if (messageId == null || messageId.isBlank()) {
                orderedMessages.add(message);
                return;
            }
            ProtocolMessage existing = messagesById.get(messageId);
            if (existing == null) {
                messagesById.put(messageId, message);
                orderedMessages.add(message);
            } else {
                mergeInto(existing, message);
            }
        }

        private List<ProtocolMessage> copyMessages() {
            return new ArrayList<>(orderedMessages);
        }

        private static void mergeInto(ProtocolMessage target, ProtocolMessage source) {
            if (source.type != null) target.type = source.type;
            if (source.requestId != null) target.requestId = source.requestId;
            if (source.sender != null) target.sender = source.sender;
            if (source.target != null) target.target = source.target;
            if (source.content != null) target.content = source.content;
            if (source.avatarId != null) target.avatarId = source.avatarId;
            if (source.timestamp != null) target.timestamp = source.timestamp;
            if (source.users != null) target.users = source.users;
            if (source.convId != null) target.convId = source.convId;
            if (source.replyTo != null) target.replyTo = source.replyTo;
            if (source.fwdFrom != null) target.fwdFrom = source.fwdFrom;
            if (source.kind != null) target.kind = source.kind;
            if (source.errorCode != null) target.errorCode = source.errorCode;
            if (source.errorMessage != null) target.errorMessage = source.errorMessage;
            if (source.sendStatus != null) target.sendStatus = source.sendStatus;
        }
    }
}
