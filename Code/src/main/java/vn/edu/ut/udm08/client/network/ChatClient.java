package vn.edu.ut.udm08.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import vn.edu.ut.udm08.shared.model.MessageSendStatus;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

public class ChatClient {
    private static final String ERROR_SESSION_INVALID = "SESSION_INVALID";
    private static final String ERROR_SESSION_EXPIRED = "SESSION_EXPIRED";
    private static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ChatReceiver receiver;
    private ChatListener listener;

    private String username;
    private String avatarId;
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, ProtocolMessage> outboundMessages = new ConcurrentHashMap<>();

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

        this.socket = new Socket(config.getHost(), config.getPort());
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.username = username.trim();
        this.avatarId = avatarId.trim();

        ChatListener safeListener = listener;
        this.listener = safeListener;
        this.receiver = new ChatReceiver(this, reader, safeListener);
        Thread receiverThread = new Thread(this.receiver, "ChatReceiverThread");
        receiverThread.setDaemon(true);
        receiverThread.start();

        ProtocolMessage helloMessage = new ProtocolMessage(MessageType.HELLO);
        helloMessage.sender = username;
        helloMessage.avatarId = avatarId;
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
        if (!isConnected()) {
            throw new IOException("Không thể gửi tin nhắn: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Người nhận (target) không được để trống");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("Nội dung tin nhắn quá dài (tối đa 5000 ký tự)");
        }

        ProtocolMessage chatMessage = new ProtocolMessage(MessageType.CHAT);
        chatMessage.messageId = UUID.randomUUID().toString();
        chatMessage.sender = this.username;
        chatMessage.target = target.trim();
        chatMessage.content = content.trim();
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
        markAllPendingOutboundUnknown(activeListener);
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
}
