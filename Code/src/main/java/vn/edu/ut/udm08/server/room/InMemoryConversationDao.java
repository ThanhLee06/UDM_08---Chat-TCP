package vn.edu.ut.udm08.server.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation luu tru in-memory cho ConversationDao (ST-102)
 */
public class InMemoryConversationDao implements ConversationDao {
    private final ConcurrentHashMap<String, Set<String>> membersMap = new ConcurrentHashMap<>();

    @Override
    public boolean isMember(String convId, String userId) {
        if (convId == null || userId == null) {
            return false;
        }
        Set<String> members = membersMap.get(convId);
        return members != null && members.contains(userId);
    }

    @Override
    public List<String> getMembers(String convId) {
        if (convId == null) {
            return Collections.emptyList();
        }
        Set<String> members = membersMap.get(convId);
        if (members == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(members);
    }

    @Override
    public void addMember(String convId, String userId) {
        if (convId != null && userId != null) {
            membersMap.computeIfAbsent(convId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        }
    }
}
