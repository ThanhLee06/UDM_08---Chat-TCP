package vn.edu.ut.udm08.server.repository;
import vn.edu.ut.udm08.server.model.ChatMessage;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
public interface IMessageDao {
    ChatMessage insertMessage(ChatMessage message);
    ChatMessage insertMessage(Connection conn, ChatMessage message);
    Optional<ChatMessage> findByMessageId(String messageId);
    Optional<ChatMessage> findByMessageId(Connection conn, String messageId);
    List<ChatMessage> findByConvId(String convId, Long beforeSequenceId, int limit);
}
