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
    if (session == null || !session.isAuthenticated()) {
        return false;
    }
    String key = normalizeKey(session.getUsername());
    return sessions.remove(key, session);
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
  private String normalizeKey(String username) {
    return Normalizer.normalize(username, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }
}
