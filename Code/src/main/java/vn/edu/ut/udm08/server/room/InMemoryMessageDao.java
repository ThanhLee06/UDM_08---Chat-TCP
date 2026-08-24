package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMessageDao implements MessageDao {
    private final ConcurrentHashMap<String, ProtocolMessage> messages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> conversationMembers = new ConcurrentHashMap<>();

    @Override
    public ProtocolMessage findById(String messageId) {
        if (messageId == null) {
            return null;
        }
        return messages.get(messageId);
    }

    @Override
    public void save(ProtocolMessage message) {
        if (message != null && message.messageId != null) {
            messages.put(message.messageId, message);
        }
    }

    @Override
    public boolean isUserInConversation(String username, String convId) {
        if (username == null || convId == null) {
            return false;
        }
        Set<String> members = conversationMembers.get(convId);
        return members != null && members.contains(username);
    }

    @Override
    public List<String> getConversationMembers(String convId) {
        if (convId == null) {
            return Collections.emptyList();
        }
        Set<String> members = conversationMembers.get(convId);
        if (members == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(members);
    }

    @Override
    public void addConversationMember(String convId, String username) {
        if (convId != null && username != null) {
            conversationMembers.computeIfAbsent(convId, k -> ConcurrentHashMap.newKeySet()).add(username);
        }
    }
}
