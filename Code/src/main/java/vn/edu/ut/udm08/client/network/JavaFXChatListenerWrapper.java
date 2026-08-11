package vn.edu.ut.udm08.client.network;

import javafx.application.Platform;
import java.util.List;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

/**
 * Một Wrapper bọc quanh ChatListener nhằm tự động chuyển các tác vụ gọi Callback
 * về chạy trên luồng giao diện của JavaFX (JavaFX Application Thread).
 * Thiết kế này giúp chống đơ giao diện và loại bỏ lỗi IllegalStateException.
 */
public class JavaFXChatListenerWrapper implements ChatListener {
    private final ChatListener delegate;

    /**
     * Khởi tạo Wrapper với một listener thực tế (Controller).
     *
     * @param delegate ChatListener thực tế nhận sự kiện.
     */
    public JavaFXChatListenerWrapper(ChatListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onLoginSuccess(ProtocolMessage message) {
        if (delegate != null) {
            Platform.runLater(() -> delegate.onLoginSuccess(message));
        }
    }

    @Override
    public void onUserListUpdated(List<UserProfile> users) {
        if (delegate != null) {
            Platform.runLater(() -> delegate.onUserListUpdated(users));
        }
    }

    @Override
    public void onMessageReceived(ProtocolMessage message) {
        if (delegate != null) {
            Platform.runLater(() -> delegate.onMessageReceived(message));
        }
    }

    @Override
    public void onMessageSentSuccess(String messageId) {
        if (delegate != null) {
            Platform.runLater(() -> delegate.onMessageSentSuccess(messageId));
        }
    }

    @Override
    public void onErrorReceived(String errorCode, String errorMessage) {
        if (delegate != null) {
            Platform.runLater(() -> delegate.onErrorReceived(errorCode, errorMessage));
        }
    }

    @Override
    public void onConnectionLost(Throwable cause) {
        if (delegate != null) {
            Platform.runLater(() -> delegate.onConnectionLost(cause));
        }
    }
}
