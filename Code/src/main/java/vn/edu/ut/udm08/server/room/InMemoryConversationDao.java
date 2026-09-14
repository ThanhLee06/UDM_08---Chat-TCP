package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation lưu trữ in-memory cho ConversationDao (ST-102)
 */
public class InMemoryConversationDao implements ConversationDao {
    private final ConcurrentHashMap<String, Set<String>> membersMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ProtocolMessage>> messagesMap = new ConcurrentHashMap<>();

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

    @Override
    public void saveMessage(ProtocolMessage message) {
        if (message == null || message.convId == null || message.convId.trim().isEmpty()) {
            return;
        }
        messagesMap.computeIfAbsent(message.convId.trim(), k -> new CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public List<ProtocolMessage> getMessages(String convId, String cursor, int limit) {
        if (convId == null || convId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ProtocolMessage> allMessages = messagesMap.get(convId.trim());
        if (allMessages == null || allMessages.isEmpty()) {
            return Collections.emptyList();
        }

        // Tạo bản sao và sắp xếp từ cũ đến mới theo sequence/timestamp
        List<ProtocolMessage> sortedList = new ArrayList<>(allMessages);
        sortedList.sort(Comparator.comparing((ProtocolMessage m) -> m.sequence != null ? m.sequence : 0L)
                .thenComparing(m -> m.timestamp != null ? m.timestamp : 0L));

        int effectiveLimit = limit <= 0 ? 30 : Math.min(limit, 100);
        int cursorIndex = sortedList.size();

        if (cursor != null && !cursor.trim().isEmpty()) {
            String trimmedCursor = cursor.trim();
            cursorIndex = -1;
            for (int i = 0; i < sortedList.size(); i++) {
                ProtocolMessage m = sortedList.get(i);
                if (trimmedCursor.equalsIgnoreCase(m.messageId) ||
                    (m.sequence != null && trimmedCursor.equals(String.valueOf(m.sequence)))) {
                    cursorIndex = i;
                    break;
                }
            }
            if (cursorIndex == -1) {
                throw new IllegalArgumentException("Cursor không hợp lệ: " + cursor);
            }
        }

        // Lấy danh sách các tin nhắn nằm trước mốc cursor (cũ hơn cursor)
        int startIndex = Math.max(0, cursorIndex - effectiveLimit);
        List<ProtocolMessage> pageResult = new ArrayList<>(sortedList.subList(startIndex, cursorIndex));

        return pageResult;
    }
}
