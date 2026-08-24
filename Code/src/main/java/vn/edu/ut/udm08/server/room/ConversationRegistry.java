package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.server.session.ClientSession;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý mapping giữa convId (Hội thoại) và danh sách ClientSession của các thành viên đang online.
 * Đảm bảo thread-safe và tự động dọn dẹp khi client disconnect.
 */
public class ConversationRegistry {

    // Mapping: convId -> Set<ClientSession>
    private final ConcurrentHashMap<String, Set<ClientSession>> convSessionsMap = new ConcurrentHashMap<>();

    // Mapping: ClientSession -> Set<convId> (Dùng để dọn dẹp nhanh tất cả convId của session khi disconnect)
    private final ConcurrentHashMap<ClientSession, Set<String>> sessionConvsMap = new ConcurrentHashMap<>();

    /**
     * Thêm session vào hội thoại convId.
     * @param convId Mã hội thoại
     * @param session Phiên làm việc của client
     * @return true nếu thêm mới thành công, false nếu tham số không hợp lệ hoặc đã tồn tại
     */
    public boolean join(String convId, ClientSession session) {
        if (convId == null || convId.trim().isEmpty() || session == null) {
            return false;
        }

        Set<ClientSession> sessions = convSessionsMap.computeIfAbsent(convId, k -> ConcurrentHashMap.newKeySet());
        boolean added = sessions.add(session);

        Set<String> convs = sessionConvsMap.computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet());
        convs.add(convId);

        return added;
    }

    /**
     * Xóa session khỏi hội thoại convId.
     * @param convId Mã hội thoại
     * @param session Phiên làm việc của client
     * @return true nếu xóa thành công, false nếu không tồn tại
     */
    public boolean leave(String convId, ClientSession session) {
        if (convId == null || session == null) {
            return false;
        }

        Set<ClientSession> sessions = convSessionsMap.get(convId);
        if (sessions == null) {
            return false;
        }

        boolean removed = sessions.remove(session);

        if (sessions.isEmpty()) {
            convSessionsMap.remove(convId, Collections.emptySet());
        }

        Set<String> convs = sessionConvsMap.get(session);
        if (convs != null) {
            convs.remove(convId);
            if (convs.isEmpty()) {
                sessionConvsMap.remove(session);
            }
        }

        return removed;
    }

    /**
     * Lấy danh sách các ClientSession đang online trong hội thoại convId.
     * @param convId Mã hội thoại
     * @return Snapshot Set<ClientSession> không null, thread-safe
     */
    public Set<ClientSession> getSessions(String convId) {
        if (convId == null) {
            return Collections.emptySet();
        }
        Set<ClientSession> sessions = convSessionsMap.get(convId);
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(sessions);
    }

    /**
     * Lấy tất cả convId mà một ClientSession đang tham gia.
     * @param session Phiên làm việc của client
     * @return Set<String> các convId
     */
    public Set<String> getAllConvIds(ClientSession session) {
        if (session == null) {
            return Collections.emptySet();
        }
        Set<String> convs = sessionConvsMap.get(session);
        if (convs == null || convs.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(convs);
    }

    /**
     * Lấy tất cả convId mà một username đang tham gia.
     * @param username Tên người dùng
     * @return Set<String> các convId
     */
    public Set<String> getAllConvIds(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (ClientSession session : sessionConvsMap.keySet()) {
            if (username.equalsIgnoreCase(session.getUsername())) {
                Set<String> convs = sessionConvsMap.get(session);
                if (convs != null) {
                    result.addAll(convs);
                }
            }
        }
        return result;
    }

    /**
     * Dọn dẹp và xóa session khỏi tất cả các convId khi client disconnect.
     * @param session Phiên làm việc ngắt kết nối
     */
    public void unregisterSession(ClientSession session) {
        if (session == null) {
            return;
        }

        Set<String> convs = sessionConvsMap.remove(session);
        if (convs != null) {
            for (String convId : convs) {
                Set<ClientSession> sessions = convSessionsMap.get(convId);
                if (sessions != null) {
                    sessions.remove(session);
                }
            }
        }
    }

    /**
     * Lấy số lượng session online trong một convId.
     */
    public int getSessionCount(String convId) {
        if (convId == null) {
            return 0;
        }
        Set<ClientSession> sessions = convSessionsMap.get(convId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Xóa toàn bộ dữ liệu trong registry.
     */
    public void clear() {
        convSessionsMap.clear();
        sessionConvsMap.clear();
    }
}
