package vn.edu.ut.udm08.server.conversation;
import vn.edu.ut.udm08.server.session.ClientSession;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public class ConversationRegistry implements IConversationRegistry {
    private final ConcurrentHashMap<String, Set<ClientSession>> conversations = new ConcurrentHashMap<>();
    @Override
    public boolean join(String convId, ClientSession session) {
        return addSession(convId, session);
    }
    @Override
    public boolean addSession(String convId, ClientSession session) {
        if (convId == null || convId.isBlank() || session == null || !session.isAuthenticated()) {
            return false;
        }
        String key = normalizeConvId(convId);
        Set<ClientSession> sessions = conversations.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        return sessions.add(session);
    }
    @Override
    public boolean leave(String convId, ClientSession session) {
        return removeSession(convId, session);
    }
    @Override
    public boolean removeSession(String convId, ClientSession session) {
        if (convId == null || convId.isBlank() || session == null) {
            return false;
        }
        String key = normalizeConvId(convId);
        Set<ClientSession> sessions = conversations.get(key);
        if (sessions == null) {
            return false;
        }
        boolean removed = sessions.remove(session);
        if (sessions.isEmpty()) {
            conversations.remove(key, sessions);
        }
        return removed;
    }
    @Override
    public List<ClientSession> getSessions(String convId) {
        if (convId == null || convId.isBlank()) {
            return Collections.emptyList();
        }
        String key = normalizeConvId(convId);
        Set<ClientSession> sessions = conversations.get(key);
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClientSession> activeSessions = new ArrayList<>();
        for (ClientSession session : sessions) {
            if (session != null && session.isAuthenticated()) {
                activeSessions.add(session);
            }
        }
        return activeSessions;
    }
    @Override
    public Set<ClientSession> getSessionSet(String convId) {
        if (convId == null || convId.isBlank()) {
            return Collections.emptySet();
        }
        String key = normalizeConvId(convId);
        Set<ClientSession> sessions = conversations.get(key);
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ClientSession> copy = ConcurrentHashMap.newKeySet();
        for (ClientSession s : sessions) {
            if (s != null && s.isAuthenticated()) {
                copy.add(s);
            }
        }
        return copy;
    }
    @Override
    public boolean isMember(String convId, ClientSession session) {
        if (convId == null || convId.isBlank() || session == null) {
            return false;
        }
        String key = normalizeConvId(convId);
        Set<ClientSession> sessions = conversations.get(key);
        return sessions != null && sessions.contains(session);
    }
    @Override
    public boolean isUserOnlineInConv(String convId, String username) {
        if (convId == null || convId.isBlank() || username == null || username.isBlank()) {
            return false;
        }
        List<ClientSession> active = getSessions(convId);
        for (ClientSession session : active) {
            if (session.getUsername() != null && session.getUsername().equalsIgnoreCase(username.trim())) {
                return true;
            }
        }
        return false;
    }
    @Override
    public List<String> getAllConvIds(ClientSession session) {
        if (session == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        conversations.forEach((convId, sessions) -> {
            if (sessions.contains(session)) {
                result.add(convId);
            }
        });
        return result;
    }
    @Override
    public List<String> getAllConvIdsForUser(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        conversations.forEach((convId, sessions) -> {
            for (ClientSession s : sessions) {
                if (s.getUsername() != null && s.getUsername().equalsIgnoreCase(username.trim())) {
                    result.add(convId);
                    break;
                }
            }
        });
        return result;
    }
    @Override
    public void removeSessionFromAll(ClientSession session) {
        if (session == null) {
            return;
        }
        conversations.forEach((convId, sessions) -> {
            if (sessions.remove(session)) {
                if (sessions.isEmpty()) {
                    conversations.remove(convId, sessions);
                }
            }
        });
    }
    @Override
    public int getOnlineCount(String convId) {
        return getSessions(convId).size();
    }
    @Override
    public int getActiveConversationCount() {
        return conversations.size();
    }
    @Override
    public void clear() {
        conversations.clear();
    }
    private String normalizeConvId(String convId) {
        return Normalizer.normalize(convId.trim(), Normalizer.Form.NFC);
    }
}
