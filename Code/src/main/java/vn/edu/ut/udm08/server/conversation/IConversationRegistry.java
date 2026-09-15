package vn.edu.ut.udm08.server.conversation;
import vn.edu.ut.udm08.server.session.ClientSession;
import java.util.List;
import java.util.Set;
public interface IConversationRegistry {
    boolean join(String convId, ClientSession session);
    boolean addSession(String convId, ClientSession session);
    boolean leave(String convId, ClientSession session);
    boolean removeSession(String convId, ClientSession session);
    List<ClientSession> getSessions(String convId);
    Set<ClientSession> getSessionSet(String convId);
    boolean isMember(String convId, ClientSession session);
    boolean isUserOnlineInConv(String convId, String username);
    List<String> getAllConvIds(ClientSession session);
    List<String> getAllConvIdsForUser(String username);
    void removeSessionFromAll(ClientSession session);
    int getOnlineCount(String convId);
    int getActiveConversationCount();
    void clear();
}
