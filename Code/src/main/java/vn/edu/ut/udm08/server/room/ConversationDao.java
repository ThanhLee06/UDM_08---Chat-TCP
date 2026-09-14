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
}
