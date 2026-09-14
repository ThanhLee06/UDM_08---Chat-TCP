package vn.edu.ut.udm08.client.cache;

import java.util.List;
import vn.edu.ut.udm08.shared.model.ConversationSummary;

public interface ConversationListListener {
    void onConversationListChanged(List<ConversationSummary> conversations);
}
