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
}
