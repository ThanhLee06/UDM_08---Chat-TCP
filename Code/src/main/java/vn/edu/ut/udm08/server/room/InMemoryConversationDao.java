package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.ConvId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation lưu trữ in-memory cho ConversationDao (ST-102 / ST-113 / ST-114)
 */
public class InMemoryConversationDao implements ConversationDao {

    public static class ConversationData {
        public String convId;
        public String type; // "DM" hoặc "ROOM"
        public String name;
        public String avatar;
        public final Set<String> members = ConcurrentHashMap.newKeySet();
        public String lastMessagePreview;
        public Long lastActivity;
    }

    private final ConcurrentHashMap<String, Set<String>> membersMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ProtocolMessage>> messagesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConversationData> conversationsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userAvatars = new ConcurrentHashMap<>();

    @Override
    public boolean isMember(String convId, String userId) {
        if (convId == null || userId == null) {
            return false;
        }
        Set<String> members = membersMap.get(convId);
        if (members != null && members.contains(userId)) {
            return true;
        }
        ConversationData data = conversationsMap.get(convId);
        return data != null && data.members.contains(userId);
    }

    @Override
    public List<String> getMembers(String convId) {
        if (convId == null) {
            return Collections.emptyList();
        }
        Set<String> members = membersMap.get(convId);
        if (members == null || members.isEmpty()) {
            ConversationData data = conversationsMap.get(convId);
            if (data != null && !data.members.isEmpty()) {
                return new ArrayList<>(data.members);
            }
            return Collections.emptyList();
        }
        return new ArrayList<>(members);
    }

    @Override
    public void addMember(String convId, String userId) {
        if (convId != null && userId != null) {
            membersMap.computeIfAbsent(convId, k -> ConcurrentHashMap.newKeySet()).add(userId);
            ConversationData data = conversationsMap.computeIfAbsent(convId, id -> {
                ConversationData cd = new ConversationData();
                cd.convId = id;
                cd.type = ConvId.isDm(id) ? "DM" : "ROOM";
                return cd;
            });
            data.members.add(userId);
        }
    }

    @Override
    public void addMessage(String convId, ProtocolMessage message) {
        if (convId != null && message != null) {
            messagesMap.computeIfAbsent(convId, k -> new CopyOnWriteArrayList<>()).add(message);
            ConversationData data = conversationsMap.computeIfAbsent(convId, id -> {
                ConversationData cd = new ConversationData();
                cd.convId = id;
                cd.type = ConvId.isDm(id) ? "DM" : "ROOM";
                return cd;
            });
            if (message.content != null) {
                data.lastMessagePreview = message.content;
            }
            if (message.timestamp != null) {
                data.lastActivity = message.timestamp;
            } else {
                data.lastActivity = System.currentTimeMillis();
            }
            if (message.sender != null) {
                addMember(convId, message.sender);
            }
            if (message.target != null && !message.target.isBlank() && !message.target.equals(message.sender)) {
                addMember(convId, message.target.trim());
            }
        }
    }

    @Override
    public void createConversation(String convId, String type, String name) {
        createConversation(convId, type, name, null);
    }

    @Override
    public void createConversation(String convId, String type, String name, String avatar) {
        if (convId == null || convId.isBlank()) {
            return;
        }
        ConversationData data = conversationsMap.computeIfAbsent(convId, id -> new ConversationData());
        data.convId = convId;
        data.type = (type != null && !type.isBlank()) ? type.toUpperCase() : (ConvId.isDm(convId) ? "DM" : "ROOM");
        if (name != null) data.name = name;
        if (avatar != null) data.avatar = avatar;
    }

    @Override
    public void setUserAvatar(String username, String avatar) {
        if (username != null && avatar != null) {
            userAvatars.put(username.toLowerCase(), avatar);
            userAvatars.put(username, avatar);
        }
    }

    @Override
    public String getUserAvatar(String username) {
        if (username == null) return null;
        String a = userAvatars.get(username);
        if (a != null) return a;
        return userAvatars.get(username.toLowerCase());
    }

    @Override
    public List<ConversationSummary> getInboxForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }

        List<ConversationSummary> inbox = new ArrayList<>();

        for (ConversationData cd : conversationsMap.values()) {
            // Kiểm tra user có thuộc cuộc trò chuyện này không
            if (!cd.members.contains(userId) && !isMember(cd.convId, userId)) {
                continue;
            }

            ConversationSummary summary = new ConversationSummary();
            summary.convId = cd.convId;

            String type = cd.type != null ? cd.type : (ConvId.isDm(cd.convId) ? "DM" : "ROOM");
            summary.chatType = type;

            if ("DM".equalsIgnoreCase(type)) {
                // Với DM: Tên và avatar phải là của người đối diện
                String otherUser = null;
                for (String m : cd.members) {
                    if (!m.equalsIgnoreCase(userId)) {
                        otherUser = m;
                        break;
                    }
                }
                if (otherUser == null) {
                    Set<String> set = membersMap.get(cd.convId);
                    if (set != null) {
                        for (String m : set) {
                            if (!m.equalsIgnoreCase(userId)) {
                                otherUser = m;
                                break;
                            }
                        }
                    }
                }
                if (otherUser == null && ConvId.isDm(cd.convId)) {
                    otherUser = ConvId.getOtherUser(cd.convId, userId);
                }

                summary.displayName = (otherUser != null) ? otherUser : userId;
                summary.avatar = getUserAvatar(otherUser);
            } else {
                // Với Room/Group: Tên nhóm và avatar nhóm
                summary.displayName = (cd.name != null && !cd.name.isBlank()) ? cd.name : cd.convId;
                summary.avatar = cd.avatar;
            }

            summary.lastMessage = cd.lastMessagePreview;
            summary.lastActivity = cd.lastActivity;

            inbox.add(summary);
        }

        // Sắp xếp theo lastActivity giảm dần (mới nhất lên đầu)
        inbox.sort((a, b) -> {
            long t1 = a.lastActivity != null ? a.lastActivity : 0L;
            long t2 = b.lastActivity != null ? b.lastActivity : 0L;
            return Long.compare(t2, t1);
        });

        return inbox;
    }

    @Override
    public MessagePagedResult getMessages(String convId, String cursor, int limit) {
        if (convId == null || convId.isBlank()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }

        List<ProtocolMessage> all = messagesMap.get(convId);
        if (all == null || all.isEmpty()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }

        Long cursorSeq = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorSeq = Long.parseLong(cursor.trim());
            } catch (NumberFormatException e) {
                for (ProtocolMessage m : all) {
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

        List<ProtocolMessage> olderMessages = new ArrayList<>();
        for (ProtocolMessage m : all) {
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
        List<ProtocolMessage> page = new ArrayList<>(olderMessages.subList(startIndex, total));

        String nextCursor = null;
        if (!page.isEmpty()) {
            ProtocolMessage oldestInPage = page.get(0);
            nextCursor = (oldestInPage.sequence != null) ? String.valueOf(oldestInPage.sequence) : oldestInPage.messageId;
        }

        return new MessagePagedResult(page, nextCursor, hasMore);
    }
}
