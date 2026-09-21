package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import java.util.List;

/**
 * Interface luu tru va tra cuu tin nhan (ST-101)
 */
public interface MessageDao {
    ProtocolMessage findById(String messageId);
    default ProtocolMessage findByMessageId(String messageId) {
        return findById(messageId);
    }
    void save(ProtocolMessage message);
    boolean isUserInConversation(String username, String convId);
    List<String> getConversationMembers(String convId);
    void addConversationMember(String convId, String username);
}
