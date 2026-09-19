package vn.edu.ut.udm08.server.repository;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.server.model.ChatMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
public class MessageDao implements IMessageDao {
    private final DatabaseConnectionFactory connectionFactory;
    public MessageDao() {
        this(new DatabaseConnectionFactory());
    }
    public MessageDao(String dbUrl) {
        this(new DatabaseConnectionFactory(dbUrl));
    }
    public MessageDao(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
    @Override
    public ChatMessage insertMessage(ChatMessage message) {
        try (Connection conn = connectionFactory.getConnection()) {
            return insertMessage(conn, message);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể kết nối lưu tin nhắn", e);
        }
    }
    @Override
    public ChatMessage insertMessage(Connection conn, ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Tin nhắn không được null");
        }
        String sql = """
                INSERT INTO messages (message_id, conv_id, sender_username, content, timestamp, kind, reply_to_message_id, forward_from_message_id, forward_from_conv_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, message.getMessageId());
            pstmt.setString(2, message.getConvId());
            pstmt.setString(3, message.getSenderUsername());
            pstmt.setString(4, message.getContent());
            pstmt.setLong(5, message.getTimestamp());
            pstmt.setString(6, message.getKind() != null ? message.getKind() : "text");
            pstmt.setString(7, message.getReplyToMessageId());
            pstmt.setString(8, message.getForwardFromMessageId());
            pstmt.setString(9, message.getForwardFromConvId());
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        message.setSequenceId(rs.getLong(1));
                    }
                }
            }
            return message;
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể lưu tin nhắn vào CSDL", e);
        }
    }
    @Override
    public Optional<ChatMessage> findByMessageId(String messageId) {
        try (Connection conn = connectionFactory.getConnection()) {
            return findByMessageId(conn, messageId);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu tin nhắn theo ID", e);
        }
    }
    @Override
    public Optional<ChatMessage> findByMessageId(Connection conn, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT sequence_id, message_id, conv_id, sender_username, content, timestamp, kind, reply_to_message_id, forward_from_message_id, forward_from_conv_id
                FROM messages
                WHERE message_id = ?
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, messageId.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu tin nhắn theo ID", e);
        }
        return Optional.empty();
    }
    @Override
    public List<ChatMessage> findByConvId(String convId, Long beforeSequenceId, int limit) {
        if (convId == null || convId.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        boolean hasCursor = beforeSequenceId != null && beforeSequenceId > 0;
        String sql = hasCursor ? """
                SELECT sequence_id, message_id, conv_id, sender_username, content, timestamp, kind, reply_to_message_id, forward_from_message_id, forward_from_conv_id
                FROM messages
                WHERE conv_id = ? AND sequence_id < ?
                ORDER BY sequence_id DESC
                LIMIT ?
                """ : """
                SELECT sequence_id, message_id, conv_id, sender_username, content, timestamp, kind, reply_to_message_id, forward_from_message_id, forward_from_conv_id
                FROM messages
                WHERE conv_id = ?
                ORDER BY sequence_id DESC
                LIMIT ?
                """;
        List<ChatMessage> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, convId.trim());
            if (hasCursor) {
                pstmt.setLong(2, beforeSequenceId);
                pstmt.setInt(3, limit);
            } else {
                pstmt.setInt(2, limit);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể lấy danh sách tin nhắn theo conv_id", e);
        }
        Collections.reverse(list);
        return list;
    }
    private ChatMessage mapRow(ResultSet rs) throws SQLException {
        ChatMessage msg = new ChatMessage();
        msg.setSequenceId(rs.getLong("sequence_id"));
        msg.setMessageId(rs.getString("message_id"));
        msg.setConvId(rs.getString("conv_id"));
        msg.setSenderUsername(rs.getString("sender_username"));
        msg.setContent(rs.getString("content"));
        msg.setTimestamp(rs.getLong("timestamp"));
        msg.setKind(rs.getString("kind"));
        msg.setReplyToMessageId(rs.getString("reply_to_message_id"));
        msg.setForwardFromMessageId(rs.getString("forward_from_message_id"));
        msg.setForwardFromConvId(rs.getString("forward_from_conv_id"));
        return msg;
    }
}
