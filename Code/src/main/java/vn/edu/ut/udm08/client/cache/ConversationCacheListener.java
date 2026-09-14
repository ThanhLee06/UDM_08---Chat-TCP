package vn.edu.ut.udm08.client.cache;

import java.util.List;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public interface ConversationCacheListener {
    void onConversationChanged(String convId, List<ProtocolMessage> messages);
}
