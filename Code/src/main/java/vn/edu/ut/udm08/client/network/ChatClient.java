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

public class ChatClient implements AutoCloseable {
    private final ClientRequests featureRequests = new ClientRequests();
    public java.util.concurrent.CompletableFuture<ProtocolMessage> requestFeature(MessageType type, String content) {
        return featureRequests.send(type, content, request -> {
            if (!isConnected()) throw new IllegalStateException("Mất kết nối với Server");
            sendRawMessage(JsonUtil.toJson(request));
        });
    }
    boolean completeFeatureRequest(ProtocolMessage message) { return featureRequests.complete(message); }
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
    private int requestTimeoutMs = 15000;
    private volatile ScheduledFuture<?> authTimeout;
    private volatile String authRequestId;
    private volatile ScheduledFuture<?> logoutTimeout;
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingHistoryRequest> pendingHistoryRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingConversationListRequest> pendingConversationListRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingUserSearchRequest> pendingUserSearchRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingOpenDmRequest> pendingOpenDmRequests = new ConcurrentHashMap<>();
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
        cancelPendingConversationListRequests("NEW_SESSION", "Phiên mới đã bắt đầu");
        cancelPendingUserSearchRequests("NEW_SESSION", "Phiên mới đã bắt đầu");
        cancelPendingOpenDmRequests("NEW_SESSION", "Phiên mới đã bắt đầu");
        markAllPendingOutboundUnknown(listener);

        this.socket = openSocket(config.getHost(), config.getPort(), config.getConnectTimeoutMs());
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

    public void connectAndAuthLogin(String host, int port, String usernameOrPhone, String password, ChatListener listener) throws IOException {
        connectWithoutHello(host, port, listener);
        this.username = usernameOrPhone;
        sendAuthLogin(usernameOrPhone, password);
    }

    public void connectWithoutHello(String host, int port, ChatListener listener) throws IOException {
        if (isConnected()) {
            disconnect();
        }
        sessionEpoch.incrementAndGet();
        ClientConfig config = new vn.edu.ut.udm08.client.config.ClientConfigStore().load();
        this.requestTimeoutMs = config.getRequestTimeoutMs();
        this.socket = openSocket(host, port, config.getConnectTimeoutMs());
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.listener = listener;

        this.receiver = new ChatReceiver(this, reader, listener);
        Thread receiverThread = new Thread(this.receiver, "ChatReceiverThread");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void sendAuthLogin(String usernameOrPhone, String password) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.AUTH_LOGIN);
        msg.requestId = UUID.randomUUID().toString();
        msg.timestamp = System.currentTimeMillis();
        msg.content = JsonUtil.toJson(new vn.edu.ut.udm08.shared.dto.AuthLoginRequest(usernameOrPhone, password));
        sendAuthRequest(msg);
    }

    public void sendRegisterInit(vn.edu.ut.udm08.shared.dto.RegisterInitRequest req) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.AUTH_REGISTER_INIT);
        msg.requestId = UUID.randomUUID().toString();
        msg.timestamp = System.currentTimeMillis();
        msg.content = JsonUtil.toJson(req);
        sendAuthRequest(msg);
    }

    public void sendVerifyOtp(String registrationId, String otpCode) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.AUTH_REGISTER_VERIFY_OTP);
        msg.requestId = UUID.randomUUID().toString();
        msg.timestamp = System.currentTimeMillis();
        msg.content = JsonUtil.toJson(new vn.edu.ut.udm08.shared.dto.RegisterOtpVerifyRequest(registrationId, otpCode));
        sendAuthRequest(msg);
    }

    public void sendResendOtp(String registrationId) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.AUTH_REGISTER_RESEND_OTP);
        msg.requestId = UUID.randomUUID().toString();
        msg.timestamp = System.currentTimeMillis();
        msg.content = registrationId;
        sendAuthRequest(msg);
    }

    public void sendForgotInit(String phoneOrEmail) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.AUTH_FORGOT_INIT);
        msg.requestId = UUID.randomUUID().toString();
        msg.timestamp = System.currentTimeMillis();
        msg.content = JsonUtil.toJson(new vn.edu.ut.udm08.shared.dto.ForgotInitRequest(phoneOrEmail));
        sendAuthRequest(msg);
    }

    public void sendForgotReset(String resetId, String otpCode, String newPassword) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.AUTH_FORGOT_RESET);
        msg.requestId = UUID.randomUUID().toString();
        msg.timestamp = System.currentTimeMillis();
        msg.content = JsonUtil.toJson(new vn.edu.ut.udm08.shared.dto.ForgotResetRequest(resetId, otpCode, newPassword));
        sendAuthRequest(msg);
    }

    public void sendRawMessage(String rawMessage) {
        if (writer != null) {
            vn.edu.ut.udm08.shared.protocol.SocketWrites.line(socket, writer, rawMessage, requestTimeoutMs);
        }
    }

    private Socket openSocket(String host, int port, int timeoutMs) throws IOException {
        Socket connected = new Socket();
        try {
            connected.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            connected.setTcpNoDelay(true);
            return connected;
        } catch (IOException | RuntimeException e) { connected.close(); throw e; }
    }
    @Override public void close() {
        disconnect();
        timeoutExecutor.shutdownNow();
    }

    private synchronized void sendAuthRequest(ProtocolMessage request) {
        if (authTimeout != null) authTimeout.cancel(false);
        authRequestId = request.requestId;
        final String id = request.requestId;
        final long epoch = sessionEpoch.get();
        authTimeout = timeoutExecutor.schedule(() -> {
            if (isCurrentEpoch(epoch) && id.equals(authRequestId)) {
                authRequestId = null;
                ChatListener active = listener;
                if (active != null) active.onErrorReceived("TIMEOUT", "Server không phản hồi đúng hạn. Vui lòng thử lại.");
            }
        }, requestTimeoutMs, TimeUnit.MILLISECONDS);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isCurrentEpoch(epoch) && id.equals(authRequestId)) sendRawMessage(JsonUtil.toJson(request));
        });
    }

    boolean completeAuthRequest(ProtocolMessage response) {
        if (response.requestId != null && response.requestId.equals(authRequestId)) {
            authRequestId = null;
            if (authTimeout != null) authTimeout.cancel(false);
            return true;
        }
        return false;
    }
    public void cancelAuthRequest() {
        authRequestId = null;
        if (authTimeout != null) authTimeout.cancel(false);
    }

    void acceptAuthenticatedUser(ProtocolMessage response) {
        var user = JsonUtil.fromJson(response.content, vn.edu.ut.udm08.shared.dto.AuthUserDto.class);
        if (user == null || user.getUsername() == null) throw new IllegalArgumentException("Invalid authentication response");
        username = user.getUsername();
        avatarId = user.getAvatarPath();
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
        final String sentId = chatMessage.messageId;
        final long sendEpoch = sessionEpoch.get();
        timeoutExecutor.schedule(() -> {
            if (isCurrentEpoch(sendEpoch)) markOutboundMessageFailed(sentId, "ACK_TIMEOUT", "Chưa nhận được xác nhận từ Server");
        }, requestTimeoutMs, TimeUnit.MILLISECONDS);
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

    public String requestConversationList(ConversationListCallback callback) throws IOException {
        return requestConversationList(DEFAULT_HISTORY_TIMEOUT_SECONDS, callback);
    }

    public String requestConversationList(int timeoutSeconds, ConversationListCallback callback) throws IOException {
        if (!isConnected()) {
            throw new IOException("Không thể tải danh sách hội thoại: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback danh sách hội thoại không được để null");
        }

        String requestId = UUID.randomUUID().toString();
        long epoch = registerPendingRequest(requestId);
        int safeTimeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_HISTORY_TIMEOUT_SECONDS : timeoutSeconds;

        PendingConversationListRequest pending = new PendingConversationListRequest(requestId, epoch, callback);
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                failConversationListRequest(requestId, "TIMEOUT", "Server không phản hồi danh sách hội thoại đúng hạn");
            }
        }, safeTimeoutSeconds, TimeUnit.SECONDS);
        pending.timeoutTask = timeoutTask;
        pendingConversationListRequests.put(requestId, pending);

        ProtocolMessage request = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        request.requestId = requestId;
        request.sender = username;
        request.timestamp = System.currentTimeMillis();

        sendRawMessage(JsonUtil.toJson(request));
        if (writer != null && writer.checkError()) {
            failConversationListRequest(requestId, "WRITE_FAILED", "Không thể gửi yêu cầu tải danh sách hội thoại qua Socket");
            throw new IOException("Không thể tải danh sách hội thoại: Gặp lỗi vật lý trên luồng truyền dữ liệu TCP Socket.");
        }
        return requestId;
    }
    public String searchUsers(String keyword, UserSearchCallback callback) throws IOException {
        return searchUsers(keyword, 20, DEFAULT_HISTORY_TIMEOUT_SECONDS, callback);
    }

    public String searchUsers(String keyword, int limit, int timeoutSeconds,
                              UserSearchCallback callback) throws IOException {
        if (!isConnected()) {
            throw new IOException("Không thể tìm người dùng: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Từ khóa tìm kiếm không được để trống");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit phải lớn hơn 0");
        }
        if (limit > 20) {
            throw new IllegalArgumentException("Limit tìm kiếm tối đa là 20 kết quả");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback tìm kiếm không được để null");
        }

        String requestId = UUID.randomUUID().toString();
        long epoch = registerPendingRequest(requestId);
        String cleanKeyword = keyword.trim();
        int safeTimeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_HISTORY_TIMEOUT_SECONDS : timeoutSeconds;

        PendingUserSearchRequest pending = new PendingUserSearchRequest(requestId, cleanKeyword, epoch, callback);
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                failUserSearchRequest(requestId, "TIMEOUT", "Server không phản hồi tìm kiếm người dùng đúng hạn");
            }
        }, safeTimeoutSeconds, TimeUnit.SECONDS);
        pending.timeoutTask = timeoutTask;
        pendingUserSearchRequests.put(requestId, pending);

        ProtocolMessage request = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
        request.requestId = requestId;
        request.keyword = cleanKeyword;
        request.limit = limit;
        request.sender = username;
        request.timestamp = System.currentTimeMillis();

        sendRawMessage(JsonUtil.toJson(request));
        if (writer != null && writer.checkError()) {
            failUserSearchRequest(requestId, "WRITE_FAILED", "Không thể gửi yêu cầu tìm kiếm qua Socket");
            throw new IOException("Không thể tìm người dùng: Gặp lỗi vật lý trên luồng truyền dữ liệu TCP Socket.");
        }
        return requestId;
    }

    public String openDirectMessage(String targetUserId, OpenDmCallback callback) throws IOException {
        return openDirectMessage(targetUserId, DEFAULT_HISTORY_TIMEOUT_SECONDS, callback);
    }

    public String openDirectMessage(String targetUserId, int timeoutSeconds,
                                    OpenDmCallback callback) throws IOException {
        if (!isConnected()) {
            throw new IOException("Không thể mở hội thoại riêng: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("TargetUserId không được để trống");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback mở DM không được để null");
        }

        String requestId = UUID.randomUUID().toString();
        long epoch = registerPendingRequest(requestId);
        String cleanTargetUserId = targetUserId.trim();
        int safeTimeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_HISTORY_TIMEOUT_SECONDS : timeoutSeconds;

        PendingOpenDmRequest pending = new PendingOpenDmRequest(requestId, cleanTargetUserId, epoch, callback);
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                failOpenDmRequest(requestId, "TIMEOUT", "Server không phản hồi mở hội thoại riêng đúng hạn");
            }
        }, safeTimeoutSeconds, TimeUnit.SECONDS);
        pending.timeoutTask = timeoutTask;
        pendingOpenDmRequests.put(requestId, pending);

        ProtocolMessage request = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        request.requestId = requestId;
        request.targetUserId = cleanTargetUserId;
        request.sender = username;
        request.timestamp = System.currentTimeMillis();

        sendRawMessage(JsonUtil.toJson(request));
        if (writer != null && writer.checkError()) {
            failOpenDmRequest(requestId, "WRITE_FAILED", "Không thể gửi yêu cầu mở hội thoại riêng qua Socket");
            throw new IOException("Không thể mở hội thoại riêng: Gặp lỗi vật lý trên luồng truyền dữ liệu TCP Socket.");
        }
        return requestId;
    }
    public synchronized void disconnect() {
        featureRequests.clear();
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
        if (logoutTimeout != null && !logoutTimeout.isDone()) return;
        if (!isConnected()) { handleLogoutOk(listener); return; }
        final long epoch = sessionEpoch.get();
        logoutTimeout = timeoutExecutor.schedule(() -> {
            if (isCurrentEpoch(epoch)) handleLogoutOk(listener);
        }, 3, TimeUnit.SECONDS);
        ProtocolMessage request = new ProtocolMessage(MessageType.LOGOUT);
        request.requestId = UUID.randomUUID().toString();
        sendRawMessage(JsonUtil.toJson(request));
    }
    boolean isLoggingOut() { return logoutTimeout != null && !logoutTimeout.isDone(); }
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

    void handleConversationListResponse(ProtocolMessage response) {
        if (response == null || response.requestId == null || response.requestId.isBlank()) {
            return;
        }
        PendingConversationListRequest pending = pendingConversationListRequests.remove(response.requestId);
        if (pending == null) {
            return;
        }
        cancelTimeout(pending);
        if (!completePendingRequest(response.requestId, pending.epoch)) {
            return;
        }
        ConversationListResult result = new ConversationListResult(response.requestId, response.conversations);
        pending.callback.onSuccess(result);
    }

    boolean handleConversationListError(ProtocolMessage errorMessage) {
        if (errorMessage == null || errorMessage.requestId == null || errorMessage.requestId.isBlank()) {
            return false;
        }
        return failConversationListRequest(errorMessage.requestId, errorMessage.errorCode, errorMessage.errorMessage);
    }

    private boolean failConversationListRequest(String requestId, String errorCode, String errorMessage) {
        PendingConversationListRequest pending = pendingConversationListRequests.remove(requestId);
        if (pending == null) {
            return false;
        }
        cancelTimeout(pending);
        pendingRequests.remove(requestId);
        pending.callback.onFailure(requestId, errorCode, errorMessage);
        return true;
    }

    private void cancelTimeout(PendingConversationListRequest pending) {
        if (pending.timeoutTask != null) {
            pending.timeoutTask.cancel(false);
        }
    }
    void handleUserSearchResponse(ProtocolMessage response) {
        if (response == null || response.requestId == null || response.requestId.isBlank()) {
            return;
        }
        PendingUserSearchRequest pending = pendingUserSearchRequests.remove(response.requestId);
        if (pending == null) {
            return;
        }
        cancelTimeout(pending);
        if (!completePendingRequest(response.requestId, pending.epoch)) {
            return;
        }
        UserSearchResult result = new UserSearchResult(response.requestId, pending.keyword, response.users);
        pending.callback.onSuccess(result);
    }

    boolean handleUserSearchError(ProtocolMessage errorMessage) {
        if (errorMessage == null || errorMessage.requestId == null || errorMessage.requestId.isBlank()) {
            return false;
        }
        return failUserSearchRequest(errorMessage.requestId, errorMessage.errorCode, errorMessage.errorMessage);
    }

    private boolean failUserSearchRequest(String requestId, String errorCode, String errorMessage) {
        PendingUserSearchRequest pending = pendingUserSearchRequests.remove(requestId);
        if (pending == null) {
            return false;
        }
        cancelTimeout(pending);
        pendingRequests.remove(requestId);
        pending.callback.onFailure(requestId, errorCode, errorMessage);
        return true;
    }

    private void cancelTimeout(PendingUserSearchRequest pending) {
        if (pending.timeoutTask != null) {
            pending.timeoutTask.cancel(false);
        }
    }

    void handleOpenDmResponse(ProtocolMessage response) {
        if (response == null || response.requestId == null || response.requestId.isBlank()) {
            return;
        }
        PendingOpenDmRequest pending = pendingOpenDmRequests.remove(response.requestId);
        if (pending == null) {
            return;
        }
        cancelTimeout(pending);
        if (!completePendingRequest(response.requestId, pending.epoch)) {
            return;
        }
        OpenDmResult result = new OpenDmResult(response.requestId, pending.targetUserId, response.conversation);
        pending.callback.onSuccess(result);
    }

    boolean handleOpenDmError(ProtocolMessage errorMessage) {
        if (errorMessage == null || errorMessage.requestId == null || errorMessage.requestId.isBlank()) {
            return false;
        }
        return failOpenDmRequest(errorMessage.requestId, errorMessage.errorCode, errorMessage.errorMessage);
    }

    private boolean failOpenDmRequest(String requestId, String errorCode, String errorMessage) {
        PendingOpenDmRequest pending = pendingOpenDmRequests.remove(requestId);
        if (pending == null) {
            return false;
        }
        cancelTimeout(pending);
        pendingRequests.remove(requestId);
        pending.callback.onFailure(requestId, errorCode, errorMessage);
        return true;
    }

    private void cancelTimeout(PendingOpenDmRequest pending) {
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

    boolean isCurrentReceiver(ChatReceiver candidate) { return receiver == candidate; }

    private void clearLocalSession(String reason, ChatListener cancelListener) {
        authRequestId = null;
        if (authTimeout != null) authTimeout.cancel(false);
        if (logoutTimeout != null) logoutTimeout.cancel(false);
        sessionEpoch.incrementAndGet();
        markAllPendingOutboundUnknown(cancelListener);
        cancelPendingHistoryRequests(reason, "Phiên kết thúc trước khi Server trả lịch sử");
        cancelPendingConversationListRequests(reason, "Phiên kết thúc trước khi Server trả danh sách hội thoại");
        cancelPendingUserSearchRequests(reason, "Phiên kết thúc trước khi Server trả kết quả tìm kiếm");
        cancelPendingOpenDmRequests(reason, "Phiên kết thúc trước khi Server trả hội thoại riêng");
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

    private void cancelPendingConversationListRequests(String errorCode, String errorMessage) {
        if (pendingConversationListRequests.isEmpty()) {
            return;
        }
        for (PendingConversationListRequest request : pendingConversationListRequests.values()) {
            cancelTimeout(request);
            request.callback.onFailure(request.requestId, errorCode, errorMessage);
        }
        pendingConversationListRequests.clear();
    }
    private void cancelPendingUserSearchRequests(String errorCode, String errorMessage) {
        if (pendingUserSearchRequests.isEmpty()) {
            return;
        }
        for (PendingUserSearchRequest request : pendingUserSearchRequests.values()) {
            cancelTimeout(request);
            request.callback.onFailure(request.requestId, errorCode, errorMessage);
        }
        pendingUserSearchRequests.clear();
    }

    private void cancelPendingOpenDmRequests(String errorCode, String errorMessage) {
        if (pendingOpenDmRequests.isEmpty()) {
            return;
        }
        for (PendingOpenDmRequest request : pendingOpenDmRequests.values()) {
            cancelTimeout(request);
            request.callback.onFailure(request.requestId, errorCode, errorMessage);
        }
        pendingOpenDmRequests.clear();
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

    private static class PendingUserSearchRequest {
        private final String requestId;
        private final String keyword;
        private final long epoch;
        private final UserSearchCallback callback;
        private ScheduledFuture<?> timeoutTask;

        private PendingUserSearchRequest(String requestId, String keyword, long epoch,
                                         UserSearchCallback callback) {
            this.requestId = requestId;
            this.keyword = keyword;
            this.epoch = epoch;
            this.callback = callback;
        }
    }

    private static class PendingOpenDmRequest {
        private final String requestId;
        private final String targetUserId;
        private final long epoch;
        private final OpenDmCallback callback;
        private ScheduledFuture<?> timeoutTask;

        private PendingOpenDmRequest(String requestId, String targetUserId, long epoch,
                                     OpenDmCallback callback) {
            this.requestId = requestId;
            this.targetUserId = targetUserId;
            this.epoch = epoch;
            this.callback = callback;
        }
    }
    private static class PendingConversationListRequest {
        private final String requestId;
        private final long epoch;
        private final ConversationListCallback callback;
        private ScheduledFuture<?> timeoutTask;

        private PendingConversationListRequest(String requestId, long epoch,
                                               ConversationListCallback callback) {
            this.requestId = requestId;
            this.epoch = epoch;
            this.callback = callback;
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






