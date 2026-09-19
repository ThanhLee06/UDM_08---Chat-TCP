package vn.edu.ut.udm08.server.repository;

import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.server.model.ChatMessage;
import vn.edu.ut.udm08.server.model.Conversation;
import vn.edu.ut.udm08.server.model.MessagePagedResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ConversationDao implements IConversationDao {
    private final DatabaseConnectionFactory connectionFactory;
    private final IMessageDao messageDao;

    public ConversationDao() {
        this(new DatabaseConnectionFactory());
    }

    public ConversationDao(String dbUrl) {
        this(new DatabaseConnectionFactory(dbUrl), new MessageDao(dbUrl));
    }

    public ConversationDao(DatabaseConnectionFactory connectionFactory) {
        this(connectionFactory, new MessageDao(connectionFactory));
    }

    public ConversationDao(DatabaseConnectionFactory connectionFactory, IMessageDao messageDao) {
        this.connectionFactory = connectionFactory;
        this.messageDao = messageDao;
    }

    @Override
    public boolean createConversation(Conversation conversation) {
        try (Connection conn = connectionFactory.getConnection()) {
            return createConversation(conn, conversation);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể kết nối tạo hội thoại", e);
        }
    }

    @Override
    public boolean createConversation(Connection conn, Conversation conversation) {
        if (conversation == null || conversation.getConvId() == null || conversation.getConvId().isBlank()) {
            return false;
        }
        String sql = """
                INSERT INTO conversations (conv_id, type, name, last_message_preview, last_activity)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, conversation.getConvId().trim());
            pstmt.setString(2, conversation.getType() != null ? conversation.getType().trim() : "DM");
            pstmt.setString(3, conversation.getName());
            pstmt.setString(4, conversation.getLastMessagePreview());
            if (conversation.getLastActivity() != null) {
                pstmt.setLong(5, conversation.getLastActivity());
            } else {
                pstmt.setNull(5, java.sql.Types.BIGINT);
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tạo hội thoại trong DB", e);
        }
    }

    @Override
    public boolean addMember(String convId, long userId, String role) {
        try (Connection conn = connectionFactory.getConnection()) {
            return addMember(conn, convId, userId, role);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể kết nối thêm thành viên", e);
        }
    }

    @Override
    public boolean addMember(Connection conn, String convId, long userId, String role) {
        if (convId == null || convId.isBlank() || userId <= 0) {
            return false;
        }
        String sql = """
                INSERT INTO conversation_members (conv_id, user_id, role)
                VALUES (?, ?, ?)
                ON CONFLICT(conv_id, user_id) DO UPDATE SET role = excluded.role
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, convId.trim());
            pstmt.setLong(2, userId);
            pstmt.setString(3, (role != null && !role.isBlank()) ? role.trim() : "member");
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể thêm thành viên vào hội thoại", e);
        }
    }

    @Override
    public boolean isMember(String convId, long userId) {
        if (convId == null || convId.isBlank() || userId <= 0) {
            return false;
        }
        String sql = "SELECT 1 FROM conversation_members WHERE conv_id = ? AND user_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, convId.trim());
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể kiểm tra thành viên hội thoại", e);
        }
    }

    @Override
    public Optional<Conversation> findById(String convId) {
        if (convId == null || convId.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT conv_id, type, name, created_at, last_message_preview, last_activity FROM conversations WHERE conv_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, convId.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Conversation conv = new Conversation();
                    conv.setConvId(rs.getString("conv_id"));
                    conv.setType(rs.getString("type"));
                    conv.setName(rs.getString("name"));
                    conv.setCreatedAt(rs.getTimestamp("created_at"));
                    conv.setLastMessagePreview(rs.getString("last_message_preview"));
                    long act = rs.getLong("last_activity");
                    conv.setLastActivity(rs.wasNull() ? null : act);
                    return Optional.of(conv);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu hội thoại", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean updateLastMessage(String convId, String preview, long activityTimestamp) {
        try (Connection conn = connectionFactory.getConnection()) {
            return updateLastMessage(conn, convId, preview, activityTimestamp);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể kết nối cập nhật tin nhắn cuối", e);
        }
    }

    @Override
    public boolean updateLastMessage(Connection conn, String convId, String preview, long activityTimestamp) {
        if (convId == null || convId.isBlank()) {
            return false;
        }
        String sql = "UPDATE conversations SET last_message_preview = ?, last_activity = ? WHERE conv_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, preview);
            pstmt.setLong(2, activityTimestamp);
            pstmt.setString(3, convId.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể cập nhật tin nhắn cuối cho hội thoại", e);
        }
    }

    @Override
    public List<Conversation> getInboxForUser(long userId) {
        if (userId <= 0) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT c.conv_id, c.type, c.name, c.created_at, c.last_message_preview, c.last_activity
                FROM conversations c
                JOIN conversation_members m ON c.conv_id = m.conv_id
                WHERE m.user_id = ?
                ORDER BY COALESCE(c.last_activity, 0) DESC, c.created_at DESC
                """;
        List<Conversation> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Conversation conv = new Conversation();
                    conv.setConvId(rs.getString("conv_id"));
                    conv.setType(rs.getString("type"));
                    conv.setName(rs.getString("name"));
                    conv.setCreatedAt(rs.getTimestamp("created_at"));
                    conv.setLastMessagePreview(rs.getString("last_message_preview"));
                    long act = rs.getLong("last_activity");
                    conv.setLastActivity(rs.wasNull() ? null : act);
                    list.add(conv);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể lấy danh sách hộp thư cho user", e);
        }
        return list;
    }

    @Override
    public Optional<String> findDmBetween(long userId1, long userId2) {
        if (userId1 <= 0 || userId2 <= 0) {
            return Optional.empty();
        }
        String sql = """
                SELECT m1.conv_id
                FROM conversation_members m1
                JOIN conversation_members m2 ON m1.conv_id = m2.conv_id
                JOIN conversations c ON m1.conv_id = c.conv_id
                WHERE c.type = 'DM'
                  AND m1.user_id = ?
                  AND m2.user_id = ?
                LIMIT 1
                """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId1);
            pstmt.setLong(2, userId2);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("conv_id"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu DM giữa 2 user", e);
        }
        return Optional.empty();
    }

    @Override
    public MessagePagedResult getMessages(String convId, Long beforeSequenceId, int limit) {
        if (convId == null || convId.isBlank()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }
        int safeLimit = (limit <= 0) ? 30 : Math.min(limit, 100);
        List<ChatMessage> rows = new ArrayList<>(messageDao.findByConvId(convId, beforeSequenceId, safeLimit + 1));
        boolean hasMore = rows.size() > safeLimit;
        if (hasMore) {
            rows.remove(0);
        }
        if (rows.isEmpty()) {
            return new MessagePagedResult(Collections.emptyList(), null, false);
        }
        Long nextCursor = rows.get(0).getSequenceId();
        return new MessagePagedResult(rows, nextCursor, hasMore);
    }
}
