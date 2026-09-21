package vn.edu.ut.udm08.client.ui.sidebar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ConversationListCallback;
import vn.edu.ut.udm08.client.network.ConversationListResult;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
public final class ClientConversationSource implements IConversationSource {
    private final ChatClient client;
    public ClientConversationSource(ChatClient client) {
        this.client = Objects.requireNonNull(client);
    }
    @Override
    public CompletableFuture<List<ConversationSummary>> load() {
        CompletableFuture<List<ConversationSummary>> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            if (result.isCancelled()) {
                return;
            }
            try {
                client.requestConversationList(new ConversationListCallback() {
                    @Override
                    public void onSuccess(ConversationListResult response) {
                        result.complete(response != null ? response.getConversations() : List.of());
                    }
                    @Override
                    public void onFailure(String requestId, String errorCode, String message) {
                        result.completeExceptionally(new IllegalStateException(message));
                    }
                });
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }
    public ChatClient getClient() {
        return client;
    }
}
