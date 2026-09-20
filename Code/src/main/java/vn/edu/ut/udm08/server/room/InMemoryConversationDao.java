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

    private final ConcurrentHashMap<String, List<vn.edu.ut.udm08.shared.model.ProtocolMessage>> messagesMap = new ConcurrentHashMap<>();

    @Override
    public void addMember(String convId, String userId) {
        if (convId != null && userId != null) {
            membersMap.computeIfAbsent(convId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        }
    }

    @Override
    public void addMessage(String convId, vn.edu.ut.udm08.shared.model.ProtocolMessage message) {
        if (convId != null && message != null) {
            messagesMap.computeIfAbsent(convId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(message);
        }
    }

    @Override
    public MessagePagedResult getMessages(String convId, String cursor, int limit) {
        if (convId == null || convId.isBlank()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }

        List<vn.edu.ut.udm08.shared.model.ProtocolMessage> all = messagesMap.get(convId);
        if (all == null || all.isEmpty()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }

        Long cursorSeq = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorSeq = Long.parseLong(cursor.trim());
            } catch (NumberFormatException e) {
                for (vn.edu.ut.udm08.shared.model.ProtocolMessage m : all) {
                    if (cursor.trim().equals(m.messageId)) {
                        cursorSeq = m.sequence;
                        break;
                    }
                }
                if (cursorSeq == null) {
                    throw new IllegalArgumentException("Invalid cursor: " + cursor);
                }
            }
            if (cursorSeq <= 0) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
        }

        int safeLimit = (limit <= 0) ? 30 : Math.min(limit, 100);

        List<vn.edu.ut.udm08.shared.model.ProtocolMessage> olderMessages = new ArrayList<>();
        for (vn.edu.ut.udm08.shared.model.ProtocolMessage m : all) {
            long seq = (m.sequence != null) ? m.sequence : 0L;
            if (cursorSeq == null || seq < cursorSeq) {
                olderMessages.add(m);
            }
        }

        if (olderMessages.isEmpty()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }

        olderMessages.sort((a, b) -> {
            long s1 = a.sequence != null ? a.sequence : (a.timestamp != null ? a.timestamp : 0);
            long s2 = b.sequence != null ? b.sequence : (b.timestamp != null ? b.timestamp : 0);
            return Long.compare(s1, s2);
        });

        int total = olderMessages.size();
        boolean hasMore = total > safeLimit;

        int startIndex = Math.max(0, total - safeLimit);
        List<vn.edu.ut.udm08.shared.model.ProtocolMessage> page = new ArrayList<>(olderMessages.subList(startIndex, total));

        String nextCursor = null;
        if (!page.isEmpty()) {
            vn.edu.ut.udm08.shared.model.ProtocolMessage oldestInPage = page.get(0);
            nextCursor = (oldestInPage.sequence != null) ? String.valueOf(oldestInPage.sequence) : oldestInPage.messageId;
        }

        return new MessagePagedResult(page, nextCursor, hasMore);
    }
}
