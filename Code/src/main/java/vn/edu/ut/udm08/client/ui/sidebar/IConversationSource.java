package vn.edu.ut.udm08.client.ui.sidebar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
public interface IConversationSource {
    CompletableFuture<List<ConversationSummary>> load();
}
