package vn.edu.ut.udm08.server.service;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.server.exception.MessageConflictException;
import vn.edu.ut.udm08.server.model.ChatMessage;
import vn.edu.ut.udm08.server.repository.ConversationDao;
import vn.edu.ut.udm08.server.repository.IConversationDao;
import vn.edu.ut.udm08.server.repository.IMessageDao;
import vn.edu.ut.udm08.server.repository.MessageDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
public class ChatStorageService implements IChatStorageService {
    private final DatabaseConnectionFactory connectionFactory;
    private final IMessageDao messageDao;
    private final IConversationDao conversationDao;
    public ChatStorageService() {
        this(new DatabaseConnectionFactory());
    }
    public ChatStorageService(String dbUrl) {
        this(new DatabaseConnectionFactory(dbUrl));
    }
    public ChatStorageService(DatabaseConnectionFactory connectionFactory) {
        this(connectionFactory, new MessageDao(connectionFactory), new ConversationDao(connectionFactory));
    }
    public ChatStorageService(DatabaseConnectionFactory connectionFactory, IMessageDao messageDao, IConversationDao conversationDao) {
        this.connectionFactory = connectionFactory;
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
    }
    @Override
    // SQLite permits one writer. Serialize writes in this server's shared storage service
    // instead of letting a burst of sessions starve each other at the database lock.
    public synchronized ChatMessage saveMessageWithTransaction(ChatMessage message) {
        if (message == null || message.getMessageId() == null || message.getMessageId().isBlank()) {
            throw new IllegalArgumentException("Tin nhắn hoặc messageId không được null");
        }
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean updated = conversationDao.updateLastMessage(conn, message.getConvId(), message.getContent(), message.getTimestamp());
                if (!updated) {
                    throw new IllegalStateException("Cập nhật cuộc trò chuyện thất bại: " + message.getConvId());
                }

                Optional<ChatMessage> existing = messageDao.findByMessageId(conn, message.getMessageId());
                if (existing.isPresent()) {
                    if (!isSameMessage(existing.get(), message)) {
                        throw new MessageConflictException("Trùng messageId nhưng khác nội dung hoặc người gửi");
                    }
                    conn.rollback();
                    return existing.get();
                }
                ensureSenderUserExists(conn, message.getSenderUsername());
                ChatMessage saved = messageDao.insertMessage(conn, message);
                conn.commit();
                return saved;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof MessageConflictException) {
                    throw (MessageConflictException) e;
                }
                if (isDuplicateMessageIdError(e)) {
                    return resolveDuplicate(message);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi kết nối CSDL khi lưu tin nhắn", e);
        }
    }

    private void ensureSenderUserExists(Connection conn, String username) throws SQLException {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Sender is required");
        try (PreparedStatement check = conn.prepareStatement("SELECT 1 FROM users WHERE LOWER(username) = ?")) {
            check.setString(1, username.trim().toLowerCase(java.util.Locale.ROOT));
            try (var rows = check.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Sender account does not exist");
            }
        }
    }
    private ChatMessage resolveDuplicate(ChatMessage message) {
        ChatMessage existing = messageDao.findByMessageId(message.getMessageId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tin nhắn trùng"));
        if (!isSameMessage(existing, message)) {
            throw new MessageConflictException("Xung đột messageId do ghi trùng");
        }
        return existing;
    }
    private boolean isSameMessage(ChatMessage a, ChatMessage b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.getConvId(), b.getConvId())
                && Objects.equals(a.getSenderUsername(), b.getSenderUsername())
                && Objects.equals(a.getContent(), b.getContent())
                && Objects.equals(a.getKind(), b.getKind())
                && Objects.equals(a.getReplyToMessageId(), b.getReplyToMessageId())
                && Objects.equals(a.getForwardFromMessageId(), b.getForwardFromMessageId())
                && Objects.equals(a.getForwardFromConvId(), b.getForwardFromConvId());
    }
    private boolean isDuplicateMessageIdError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SQLException sqlEx) {
                String msg = sqlEx.getMessage();
                if (msg != null && (msg.contains("UNIQUE constraint failed: messages.message_id") || msg.contains("PRIMARY KEY"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
