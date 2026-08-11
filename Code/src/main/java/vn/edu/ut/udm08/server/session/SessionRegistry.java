package vn.edu.ut.udm08.server.session;

import vn.edu.ut.udm08.shared.model.UserProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lop quan ly danh sach cac Client dang online
public class SessionRegistry {
    // Luu tru danh sach session theo username
    private final Map<String, ClientSession> activeSessions = new HashMap<>();

    // Them client moi vao danh sach
    public synchronized boolean register(ClientSession session) {
        if (session == null || session.getUsername() == null) {
            return false;
        }
        activeSessions.put(session.getUsername(), session);
        return true;
    }

    // Xoa client khoi danh sach khi ngat ket noi
    public synchronized void unregister(String username) {
        if (username != null) {
            activeSessions.remove(username);
        }
    }

    // Tim kiem session cua nguoi nhan theo username
    public synchronized ClientSession getSession(String username) {
        if (username == null) {
            return null;
        }
        return activeSessions.get(username);
    }

    // Lay danh sach tat ca nguoi dung online
    public synchronized List<UserProfile> getActiveUserProfiles() {
        List<UserProfile> list = new ArrayList<>();
        for (ClientSession session : activeSessions.values()) {
            if (session.isConnected()) {
                list.add(new UserProfile(session.getUsername(), session.getAvatarId()));
            }
        }
        return list;
    }

    public synchronized int count() {
        return activeSessions.size();
    }
}
