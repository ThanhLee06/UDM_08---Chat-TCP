package vn.edu.ut.udm08.server.room;

import java.util.List;

/**
 * Interface Quản lý hội thoại và thành viên (ST-102)
 */
public interface ConversationDao {
    /**
     * Kiểm tra user (userId) có nằm trong cuộc trò chuyện (convId) không.
     */
    boolean isMember(String convId, String userId);

    /**
     * Lấy danh sách ID thành viên của cuộc trò chuyện.
     */
    List<String> getMembers(String convId);

    /**
     * Thêm thành viên vào cuộc trò chuyện.
     */
    void addMember(String convId, String userId);

    /**
     * Lưu tin nhắn vào hội thoại.
     */
    void saveMessage(vn.edu.ut.udm08.shared.model.ProtocolMessage message);

    /**
     * Lấy danh sách tin nhắn cũ hơn mốc cursor theo phân trang.
     * @param convId Mã hội thoại
     * @param cursor sequence_id hoặc messageId của tin nhắn làm mốc (null nếu lấy tin mới nhất)
     * @param limit Số lượng tin nhắn tối đa
     * @return Danh sách tin nhắn sắp xếp từ cũ đến mới
     */
    List<vn.edu.ut.udm08.shared.model.ProtocolMessage> getMessages(String convId, String cursor, int limit);
}
