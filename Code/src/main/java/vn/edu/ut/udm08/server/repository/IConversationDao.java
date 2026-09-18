package vn.edu.ut.udm08.server.repository;

import vn.edu.ut.udm08.server.model.Conversation;
import vn.edu.ut.udm08.server.model.MessagePagedResult;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface IConversationDao {
    boolean createConversation(Conversation conversation);
    boolean createConversation(Connection conn, Conversation conversation);
    boolean addMember(String convId, long userId, String role);
    boolean addMember(Connection conn, String convId, long userId, String role);
    boolean isMember(String convId, long userId);
    Optional<Conversation> findById(String convId);
    boolean updateLastMessage(String convId, String preview, long activityTimestamp);
    boolean updateLastMessage(Connection conn, String convId, String preview, long activityTimestamp);
    List<Conversation> getInboxForUser(long userId);
    Optional<String> findDmBetween(long userId1, long userId2);
    MessagePagedResult getMessages(String convId, Long beforeSequenceId, int limit);
}
