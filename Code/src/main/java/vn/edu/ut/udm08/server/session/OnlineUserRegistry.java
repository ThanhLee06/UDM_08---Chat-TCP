package vn.edu.ut.udm08.server.session;
import vn.edu.ut.udm08.shared.model.UserProfile;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
public class OnlineUserRegistry {
  private ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();
  public boolean register(ClientSession session) {
    if (session == null || !session.isAuthenticated()) {
        return false;
    }
    String key = normalizeKey(session.getUsername());
    ClientSession existingSession = sessions.putIfAbsent(key, session);
    return existingSession == null;
  }
  public ClientSession find(String username) {
    if (!UsernameValidator.isValid(username)) {
        return null;
    }
    String key = normalizeKey(username);
    return sessions.get(key);
  }
  public boolean remove(ClientSession session) {
    if (session == null) {
        return false;
    }
    String username = session.getUsername();
    if (username == null || username.isBlank()) {
        return false;
    }
    String key = normalizeKey(username);
    ClientSession existing = sessions.get(key);
    if (existing != null && existing.getSessionId() != null && session.getSessionId() != null
            && existing.getSessionId().equals(session.getSessionId())) {
        return sessions.remove(key, existing);
    }
    return false;
  }
  public List<UserProfile> getOnlineUsers() {
    List<UserProfile> users = new ArrayList<>();
    for (ClientSession session : sessions.values()) {
        users.add(new UserProfile(session.getUsername(), session.getAvatarId()));
    }
    return users;
  }
  public List<ClientSession> getSessions() {
    List<ClientSession> result = new ArrayList<>();
    for (ClientSession session : sessions.values()) {
        result.add(session);
    }
    return result;
  }
  public boolean kickSession(String usernameOrPhone, String reason) {
    return kickSession(usernameOrPhone, reason, null);
  }
  public boolean kickSession(String usernameOrPhone, String reason, ClientSession newSession) {
    if (usernameOrPhone == null || usernameOrPhone.isBlank()) {
        return false;
    }
    String targetKey = normalizeKey(usernameOrPhone);
    ClientSession targetSession = sessions.get(targetKey);
    if (targetSession == null) {
        for (ClientSession session : sessions.values()) {
            if (session.getUser() != null && session.getUser().getPhoneNumber() != null) {
                if (usernameOrPhone.trim().equalsIgnoreCase(session.getUser().getPhoneNumber().trim())) {
                    targetSession = session;
                    targetKey = normalizeKey(session.getUsername());
                    break;
                }
            }
        }
    }
    if (targetSession != null && (newSession == null || (targetSession != newSession && !targetSession.getSessionId().equals(newSession.getSessionId())))) {
        sessions.remove(targetKey, targetSession);
        targetSession.kick(reason != null ? reason : "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác");
        return true;
    }
    return false;
  }
  private String normalizeKey(String username) {
    return Normalizer.normalize(username, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }
}
