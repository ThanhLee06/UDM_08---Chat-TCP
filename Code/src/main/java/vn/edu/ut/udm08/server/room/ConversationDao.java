package vn.edu.ut.udm08.server.room;

import java.util.List;

/**
 * Interface Quan ly hoi thoai va thanh vien (ST-102)
 */
public interface ConversationDao {
    /**
     * Kiem tra user (userId) co nam trong cuoc tro chuyen (convId) khong.
     */
    boolean isMember(String convId, String userId);

    /**
     * Lay danh sach ID thanh vien cua cuoc tro chuyen.
     */
    List<String> getMembers(String convId);

    /**
     * Them thanh vien vao cuoc tro chuyen.
     */
    void addMember(String convId, String userId);

    /**
     * Lay danh sach tin nhan phan trang theo cursor (ST-102 / ST-113).
     */
    MessagePagedResult getMessages(String convId, String cursor, int limit);

    /**
     * Luu tin nhan vao hoi thoai.
     */
    void addMessage(String convId, vn.edu.ut.udm08.shared.model.ProtocolMessage message);

    /**
     * Lay danh sach tat ca cuoc tro chuyen cua User (ST-102 / ST-114).
     */
    java.util.List<vn.edu.ut.udm08.shared.model.ConversationSummary> getInboxForUser(String userId);

    /**
     * Tao hoac cap nhat hoi thoai.
     */
    void createConversation(String convId, String type, String name);
    void createConversation(String convId, String type, String name, String avatar);

    /**
     * Dang ky avatar cho user (dung de hien thi avatar trong DM).
     */
    void setUserAvatar(String username, String avatar);
    String getUserAvatar(String username);
}
