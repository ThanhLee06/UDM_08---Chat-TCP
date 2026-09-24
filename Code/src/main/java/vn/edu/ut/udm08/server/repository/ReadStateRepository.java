package vn.edu.ut.udm08.server.repository;
import java.sql.*;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.shared.protocol.ConvId;
public final class ReadStateRepository {
    private final DatabaseConnectionFactory factory;
    public ReadStateRepository(DatabaseConnectionFactory factory) { this.factory = factory; }
    public void markRead(long userId, String convId, String messageId) {
        if (convId == null || messageId == null) throw new IllegalArgumentException("Thiếu hội thoại hoặc tin nhắn");
        if (!ConvId.isPublicRoom(convId) && !new ConversationDao(factory).isMember(convId, userId)) throw new IllegalArgumentException("Không có quyền đọc hội thoại");
        String sql = "INSERT INTO conversation_reads(conv_id,user_id,last_sequence) SELECT conv_id,?,sequence_id FROM messages WHERE conv_id=? AND message_id=? ON CONFLICT(conv_id,user_id) DO UPDATE SET last_sequence=MAX(last_sequence,excluded.last_sequence)";
        try (Connection c = factory.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1,userId); p.setString(2,convId); p.setString(3,messageId);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("Tin nhắn không thuộc hội thoại");
        } catch (SQLException e) { throw new IllegalStateException("Không lưu được trạng thái đã đọc",e); }
    }
    public int unread(long userId, String username, String convId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE conv_id=? AND sender_username<>? AND sequence_id>COALESCE((SELECT last_sequence FROM conversation_reads WHERE conv_id=? AND user_id=?),0)";
        try (Connection c=factory.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,convId); p.setString(2,username); p.setString(3,convId); p.setLong(4,userId);
            try (ResultSet rs=p.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new IllegalStateException("Không tải được tin chưa đọc",e); }
    }
}
