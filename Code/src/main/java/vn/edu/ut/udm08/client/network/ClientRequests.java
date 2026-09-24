package vn.edu.ut.udm08.client.network;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
public final class ClientRequests {
    private final Map<String, CompletableFuture<ProtocolMessage>> pending = new ConcurrentHashMap<>();
    public CompletableFuture<ProtocolMessage> send(MessageType type, String content, Consumer<ProtocolMessage> sender) {
        ProtocolMessage request = new ProtocolMessage(type);
        request.requestId = UUID.randomUUID().toString();
        request.content = content;
        CompletableFuture<ProtocolMessage> result = new CompletableFuture<>();
        pending.put(request.requestId, result);
        result.orTimeout(15, TimeUnit.SECONDS).whenComplete((value, error) -> pending.remove(request.requestId));
        CompletableFuture.runAsync(() -> {
            try {
                if (!result.isDone()) sender.accept(request);
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }
    public boolean complete(ProtocolMessage message) {
        if (message.requestId == null) return false;
        var result = pending.remove(message.requestId);
        if (result == null) return false;
        if (message.type == MessageType.ERROR) result.completeExceptionally(new IllegalStateException(message.errorMessage));
        else result.complete(message);
        return true;
    }
    public void clear() {
        pending.values().forEach(result -> result.completeExceptionally(new IllegalStateException("Kết nối đã đóng")));
        pending.clear();
    }
}
