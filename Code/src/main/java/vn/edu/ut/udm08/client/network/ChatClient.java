package vn.edu.ut.udm08.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import vn.edu.ut.udm08.shared.model.MessageSendStatus;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

public class ChatClient {
    private static final String ERROR_SESSION_INVALID = "SESSION_INVALID";
    private static final String ERROR_SESSION_EXPIRED = "SESSION_EXPIRED";
    private static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    private static final int DEFAULT_HISTORY_TIMEOUT_SECONDS = 15;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ChatReceiver receiver;
    private ChatListener listener;

    private String username;
    private String avatarId;
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingHistoryRequest> pendingHistoryRequests = new ConcurrentHashMap<>();
    private final Map<String, ProtocolMessage> outboundMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ChatClientTimeoutThread");
        thread.setDaemon(true);
        return thread;
    });

    public void connect(String host, int port, String username, String avatarId, ChatListener listener) throws IOException {
        connect(new ClientConfig(host, port), username, avatarId, listener);
    }

    public void connect(ClientConfig config, String username, String avatarId, ChatListener listener) throws IOException {
        if (isConnected()) {
            throw new IllegalStateException("ChatClient đã được kết nối trước đó");
        }
        if (config == null) {
            throw new IllegalArgumentException("ClientConfig không được để null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        if (avatarId == null || avatarId.isBlank()) {
            throw new IllegalArgumentException("AvatarId không được để trống");
        }

        long epoch = sessionEpoch.incrementAndGet();
        cancelPendingRequests("NEW_SESSION");
        cancelPendingHistoryRequests("NEW_SESSION", "Phiên mới đã bắt đầu");
        markAllPendingOutboundUnknown(listener);

        this.socket = new Socket(config.getHost(), config.getPort());
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.username = username.trim();
        this.avatarId = avatarId.trim();
        this.listener = listener;

        this.receiver = new ChatReceiver(this, reader, listener);
        Thread receiverThread = new Thread(this.receiver, "ChatReceiverThread");
        receiverThread.setDaemon(true);
        receiverThread.start();

        ProtocolMessage helloMessage = new ProtocolMessage(MessageType.HELLO);
        helloMessage.sender = this.username;
        helloMessage.avatarId = this.avatarId;
        helloMessage.timestamp = System.currentTimeMillis();
        helloMessage.requestId = "HELLO-" + epoch;

        sendRawMessage(JsonUtil.toJson(helloMessage));
    }

    public void sendRawMessage(String rawMessage) {
        if (writer != null) {
            writer.println(rawMessage);
        }
    }

    public void sendMessage(String target, String content) throws IOException {
        ProtocolMessage chatMessage = new ProtocolMessage(MessageType.CHAT);
        chatMessage.messageId = UUID.randomUUID().toString();
        chatMessage.target = target;
        chatMessage.content = content;
        chatMessage.timestamp = System.currentTimeMillis();
        sendMessage(chatMessage);
    }

    public void sendMessage(ProtocolMessage chatMessage) throws IOException {
        if (!isConnected()) {
            throw new IOException("Không thể gửi tin nhắn: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }
        if (chatMessage == null) {
            throw new IllegalArgumentException("Tin nhắn không được để null");
        }
        if (chatMessage.target == null || chatMessage.target.isBlank()) {
            throw new IllegalArgumentException("Người nhận (target) không được để trống");
        }
        if (chatMessage.content == null || chatMessage.content.isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }
        if (chatMessage.content.length() > 5000) {
            throw new IllegalArgumentException("Nội dung tin nhắn quá dài (tối đa 5000 ký tự)");
        }

        chatMessage.type = MessageType.CHAT;
        if (chatMessage.messageId == null || chatMessage.messageId.isBlank()) {
            chatMessage.messageId = UUID.randomUUID().toString();
        }
        chatMessage.sender = this.username;
        chatMessage.target = chatMessage.target.trim();
        chatMessage.content = chatMessage.content.trim();
        if (chatMessage.timestamp == null) {
            chatMessage.timestamp = System.currentTimeMillis();
        }
        chatMessage.sendStatus = MessageSendStatus.PENDING;

        outboundMessages.put(chatMessage.messageId, chatMessage);
        notifyMessageStatusUpdated(chatMessage);
        sendRawMessage(JsonUtil.toJson(chatMessage));

        if (writer != null && writer.checkError()) {
            markOutboundMessageFailed(chatMessage.messageId, "WRITE_FAILED", "Không thể ghi dữ liệu vào Socket");
            throw new IOException("Không thể gửi tin nhắn: Gặp lỗi vật lý trên luồng truyền dữ liệu TCP Socket.");
        }
    }

    public String requestMessageHistory(String convId, int limit, String cursor,
                                        MessageHistoryCallback callback) throws IOException {
        return requestMessageHistory(convId, limit, cursor, DEFAULT_HISTORY_TIMEOUT_SECONDS, callback);
    }

    public String requestMessageHistory(String convId, int limit, String cursor, int timeoutSeconds,
                                        MessageHistoryCallback callback) throws IOException {
        if (!isConnected()) {
            throw new IOException("Không thể tải lịch sử: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }
        if (convId == null || convId.isBlank()) {
            throw new IllegalArgumentException("ConvId không được để trống");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit phải lớn hơn 0");
        }
        if (limit > 100) {
            throw new IllegalArgumentException("Limit tối đa là 100 tin nhắn mỗi trang");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback lịch sử không được để null");
        }

        String requestId = UUID.randomUUID().toString();
        long epoch = registerPendingRequest(requestId);
        int safeTimeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_HISTORY_TIMEOUT_SECONDS : timeoutSeconds;

        PendingHistoryRequest pending = new PendingHistoryRequest(requestId, convId.trim(), epoch, callback);
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                failHistoryRequest(requestId, "TIMEOUT", "Server không phản hồi lịch sử hội thoại đúng hạn");
            }
        }, safeTimeoutSeconds, TimeUnit.SECONDS);
        pending.timeoutTask = timeoutTask;
        pendingHistoryRequests.put(requestId, pending);

        ProtocolMessage request = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        request.requestId = requestId;
        request.convId = convId.trim();
        request.limit = limit;
        request.cursor = cursor;
        request.sender = username;
        request.timestamp = System.currentTimeMillis();

        sendRawMessage(JsonUtil.toJson(request));
        if (writer != null && writer.checkError()) {
            failHistoryRequest(requestId, "WRITE_FAILED", "Không thể gửi yêu cầu tải lịch sử qua Socket");
            throw new IOException("Không thể tải lịch sử: Gặp lỗi vật lý trên luồng truyền dữ liệu TCP Socket.");
        }
        return requestId;
    }

    public synchronized void disconnect() {
        try {
            if (writer != null && socket != null && !socket.isClosed()) {
                ProtocolMessage disconnectMessage = new ProtocolMessage(MessageType.DISCONNECT);
                disconnectMessage.sender = this.username;
                disconnectMessage.timestamp = System.currentTimeMillis();
                try {
                    writer.println(JsonUtil.toJson(disconnectMessage));
                } catch (Exception ignored) {
                }
            }
        } finally {
            clearLocalSession("DISCONNECT", listener);
        }
    }

    public synchronized void logout() {
        ChatListener activeListener = listener;
        try {
            if (writer != null && socket != null && !socket.isClosed()) {
                ProtocolMessage logoutMessage = new ProtocolMessage(MessageType.LOGOUT);
                logoutMessage.sender = this.username;
                logoutMessage.requestId = UUID.randomUUID().toString();
                logoutMessage.timestamp = System.currentTimeMillis();
                writer.println(JsonUtil.toJson(logoutMessage));
            }
        } catch (Exception ignored) {
        } finally {
            clearLocalSession("LOGOUT", activeListener);
            if (activeListener != null) {
                activeListener.onLogoutSuccess();
            }
        }
    }

    public long registerPendingRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("RequestId không được để trống");
        }
        long epoch = sessionEpoch.get();
        pendingRequests.put(requestId, new PendingRequest(requestId, epoch));
        return epoch;
    }

    public boolean completePendingRequest(String requestId, long expectedEpoch) {
        if (!isCurrentEpoch(expectedEpoch) || requestId == null || requestId.isBlank()) {
            return false;
        }
        return pendingRequests.remove(requestId) != null;
    }

    public boolean isCurrentEpoch(long epoch) {
        return sessionEpoch.get() == epoch;
    }

    public long getSessionEpoch() {
        return sessionEpoch.get();
    }

    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    void handleLogoutOk(ChatListener fallbackListener) {
        ChatListener activeListener = listener != null ? listener : fallbackListener;
        clearLocalSession("LOGOUT_OK", activeListener);
        if (activeListener != null) {
            activeListener.onLogoutSuccess();
        }
    }

    void handleSessionExpired(String errorCode, String errorMessage, ChatListener fallbackListener) {
        ChatListener activeListener = listener != null ? listener : fallbackListener;
        clearLocalSession("SESSION_EXPIRED", activeListener);
        if (activeListener != null) {
            activeListener.onSessionExpired(errorCode, errorMessage);
        }
    }

    boolean isSessionInvalidError(String errorCode) {
        return ERROR_SESSION_INVALID.equals(errorCode)
                || ERROR_SESSION_EXPIRED.equals(errorCode)
                || ERROR_UNAUTHORIZED.equals(errorCode);
    }

    void handleChatOk(ProtocolMessage ackMessage) {
        if (ackMessage == null || ackMessage.messageId == null || ackMessage.messageId.isBlank()) {
            return;
        }
        ProtocolMessage pending = outboundMessages.remove(ackMessage.messageId);
        if (pending == null) {
            return;
        }
        if (ackMessage.timestamp != null) {
            pending.timestamp = ackMessage.timestamp;
        }
        pending.sendStatus = MessageSendStatus.SENT;
        notifyMessageStatusUpdated(pending);
    }

    boolean handleMessageError(ProtocolMessage errorMessage) {
        if (errorMessage == null || errorMessage.messageId == null || errorMessage.messageId.isBlank()) {
            return false;
        }
        ProtocolMessage pending = outboundMessages.remove(errorMessage.messageId);
        if (pending == null) {
            return false;
        }
        pending.errorCode = errorMessage.errorCode;
        pending.errorMessage = errorMessage.errorMessage;
        pending.sendStatus = MessageSendStatus.FAILED;
        notifyMessageStatusUpdated(pending);
        return true;
    }

    void handleHistoryResponse(ProtocolMessage response) {
        if (response == null || response.requestId == null || response.requestId.isBlank()) {
            return;
        }
        PendingHistoryRequest pending = pendingHistoryRequests.remove(response.requestId);
        if (pending == null) {
            return;
        }
        cancelTimeout(pending);
        if (!completePendingRequest(response.requestId, pending.epoch)) {
            return;
        }

        MessageHistoryPage page = new MessageHistoryPage(
                response.requestId,
                response.convId != null ? response.convId : pending.convId,
                response.messages != null ? response.messages : new ArrayList<>(),
                response.nextCursor,
                Boolean.TRUE.equals(response.hasMore));
        pending.callback.onSuccess(page);
    }

    boolean handleHistoryError(ProtocolMessage errorMessage) {
        if (errorMessage == null || errorMessage.requestId == null || errorMessage.requestId.isBlank()) {
            return false;
        }
        return failHistoryRequest(errorMessage.requestId, errorMessage.errorCode, errorMessage.errorMessage);
    }

    private boolean failHistoryRequest(String requestId, String errorCode, String errorMessage) {
        PendingHistoryRequest pending = pendingHistoryRequests.remove(requestId);
        if (pending == null) {
            return false;
        }
        cancelTimeout(pending);
        pendingRequests.remove(requestId);
        pending.callback.onFailure(requestId, errorCode, errorMessage);
        return true;
    }

    private void cancelTimeout(PendingHistoryRequest pending) {
        if (pending.timeoutTask != null) {
            pending.timeoutTask.cancel(false);
        }
    }

    private void closeResources() {
        if (receiver != null) {
            receiver.stop();
            receiver = null;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        } finally {
            reader = null;
        }
        if (writer != null) {
            writer.close();
            writer = null;
        }
        this.username = null;
        this.avatarId = null;
    }

    private void clearLocalSession(String reason, ChatListener cancelListener) {
        sessionEpoch.incrementAndGet();
        markAllPendingOutboundUnknown(cancelListener);
        cancelPendingHistoryRequests(reason, "Phiên kết thúc trước khi Server trả lịch sử");
        cancelPendingRequests(reason, cancelListener);
        closeResources();
        listener = null;
    }

    private void cancelPendingRequests(String reason) {
        cancelPendingRequests(reason, listener);
    }

    private void cancelPendingRequests(String reason, ChatListener cancelListener) {
        if (pendingRequests.isEmpty()) {
            return;
        }
        for (PendingRequest request : pendingRequests.values()) {
            if (cancelListener != null) {
                cancelListener.onRequestCancelled(request.requestId, reason);
            }
        }
        pendingRequests.clear();
    }

    private void cancelPendingHistoryRequests(String errorCode, String errorMessage) {
        if (pendingHistoryRequests.isEmpty()) {
            return;
        }
        for (PendingHistoryRequest request : pendingHistoryRequests.values()) {
            cancelTimeout(request);
            request.callback.onFailure(request.requestId, errorCode, errorMessage);
        }
        pendingHistoryRequests.clear();
    }

    private void markOutboundMessageFailed(String messageId, String errorCode, String errorMessage) {
        ProtocolMessage pending = outboundMessages.remove(messageId);
        if (pending == null) {
            return;
        }
        pending.errorCode = errorCode;
        pending.errorMessage = errorMessage;
        pending.sendStatus = MessageSendStatus.FAILED;
        notifyMessageStatusUpdated(pending);
    }

    private void markAllPendingOutboundUnknown(ChatListener targetListener) {
        if (outboundMessages.isEmpty()) {
            return;
        }
        for (ProtocolMessage pending : outboundMessages.values()) {
            pending.sendStatus = MessageSendStatus.UNKNOWN;
            if (targetListener != null) {
                targetListener.onMessageStatusUpdated(pending);
            }
        }
        outboundMessages.clear();
    }

    private void notifyMessageStatusUpdated(ProtocolMessage message) {
        if (listener != null) {
            listener.onMessageStatusUpdated(message);
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public String getUsername() {
        return username;
    }

    public String getAvatarId() {
        return avatarId;
    }

    private static class PendingRequest {
        private final String requestId;
        private final long epoch;

        private PendingRequest(String requestId, long epoch) {
            this.requestId = requestId;
            this.epoch = epoch;
        }
    }

    private static class PendingHistoryRequest {
        private final String requestId;
        private final String convId;
        private final long epoch;
        private final MessageHistoryCallback callback;
        private ScheduledFuture<?> timeoutTask;

        private PendingHistoryRequest(String requestId, String convId, long epoch,
                                      MessageHistoryCallback callback) {
            this.requestId = requestId;
            this.convId = convId;
            this.epoch = epoch;
            this.callback = callback;
        }
    }
}
