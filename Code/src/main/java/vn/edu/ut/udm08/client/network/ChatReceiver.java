package vn.edu.ut.udm08.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

/**
 * Luồng chạy ẩn (Background Reader Thread) liên tục lắng nghe và đọc tin nhắn từ Server.
 */
public class ChatReceiver implements Runnable {
    private final ChatClient client;
    private final BufferedReader reader;
    private final ChatListener listener;
    private volatile boolean running = true;

    public ChatReceiver(ChatClient client, BufferedReader reader, ChatListener listener) {
        this.client = client;
        this.reader = reader;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    ProtocolMessage message = JsonUtil.fromJson(line);
                    if (message != null && message.type != null) {
                        dispatchMessage(message);
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onErrorReceived("JSON_PARSE_ERROR", "Lỗi cú pháp gói tin nhận được: " + e.getMessage());
                    }
                }
            }

            if (running) {
                notifyConnectionLost(new IOException("Máy chủ đã ngắt kết nối"));
            }
        } catch (IOException e) {
            if (running) {
                notifyConnectionLost(e);
            }
        } finally {
            running = false;
        }
    }

    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }

    void dispatchMessage(ProtocolMessage message) {
        if (listener == null || message == null || message.type == null) {
            return;
        }

        switch (message.type) {
            case HELLO_OK:
                listener.onLoginSuccess(message);
                break;
            case USER_LIST:
                listener.onUserListUpdated(message.users != null ? message.users : Collections.emptyList());
                break;
            case CHAT:
                listener.onMessageReceived(message);
                break;
            case CHAT_OK:
                if (client != null) {
                    client.handleChatOk(message);
                }
                listener.onMessageSentSuccess(message.messageId);
                break;
            case HISTORY_RESPONSE:
                if (client != null) {
                    client.handleHistoryResponse(message);
                }
                break;
            case CONVERSATION_LIST_RESPONSE:
                if (client != null) {
                    client.handleConversationListResponse(message);
                }
                break;
            case ERROR:
                if (client != null && client.isSessionInvalidError(message.errorCode)) {
                    client.handleSessionExpired(message.errorCode, message.errorMessage, listener);
                } else if (client != null && client.handleConversationListError(message)) {
                    break;
                } else if (client != null && client.handleHistoryError(message)) {
                    break;
                } else if (client != null && client.handleMessageError(message)) {
                    break;
                } else {
                    listener.onErrorReceived(message.errorCode, message.errorMessage);
                }
                break;
            case LOGOUT_OK:
                if (client != null) {
                    client.handleLogoutOk(listener);
                } else {
                    listener.onLogoutSuccess();
                }
                break;
            case SESSION_EXPIRED:
                if (client != null) {
                    client.handleSessionExpired(message.errorCode, message.errorMessage, listener);
                } else {
                    listener.onSessionExpired(message.errorCode, message.errorMessage);
                }
                break;
            default:
                break;
        }
    }

    private void notifyConnectionLost(Throwable cause) {
        try {
            client.disconnect();
        } finally {
            if (listener != null) {
                listener.onConnectionLost(cause);
            }
        }
    }
}


