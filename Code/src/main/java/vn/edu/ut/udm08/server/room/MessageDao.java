package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import java.util.List;

public interface MessageDao {
    ProtocolMessage findById(String messageId);
    void save(ProtocolMessage message);
    boolean isUserInConversation(String username, String convId);
    List<String> getConversationMembers(String convId);
    void addConversationMember(String convId, String username);
}
