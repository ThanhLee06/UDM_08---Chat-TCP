package vn.edu.ut.udm08.client.ui.sidebar;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ConversationListCallback;
import vn.edu.ut.udm08.client.network.ConversationListResult;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
class ClientConversationSourceTest {
    @Test
    void requestRunsOffCallerThreadAndReturnsServerConversations() throws Exception {
        AtomicReference<Thread> requestThread = new AtomicReference<>();
        ConversationSummary conversation = new ConversationSummary();
        conversation.convId = "dm:alice:bob";
        ChatClient client = new ChatClient() {
            @Override
            public String requestConversationList(ConversationListCallback callback) {
                requestThread.set(Thread.currentThread());
                callback.onSuccess(new ConversationListResult("request", List.of(conversation)));
                return "request";
            }
        };
        var result = new ClientConversationSource(client).load().get(5, TimeUnit.SECONDS);
        assertNotSame(Thread.currentThread(), requestThread.get());
        assertEquals("dm:alice:bob", result.getFirst().convId);
    }
    @Test
    void socketFailureCompletesExceptionally() {
        ChatClient client = new ChatClient() {
            @Override
            public String requestConversationList(ConversationListCallback callback) throws IOException {
                throw new IOException("disconnected");
            }
        };
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> new ClientConversationSource(client).load().get(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, error.getCause());
    }
    @Test
    void serverFailureDoesNotBecomeAnEmptyList() {
        ChatClient client = new ChatClient() {
            @Override
            public String requestConversationList(ConversationListCallback callback) {
                callback.onFailure("request", "TIMEOUT", "timeout");
                return "request";
            }
        };
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> new ClientConversationSource(client).load().get(5, TimeUnit.SECONDS));
        assertEquals("timeout", error.getCause().getMessage());
    }
}
