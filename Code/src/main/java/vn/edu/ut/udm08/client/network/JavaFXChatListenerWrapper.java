package vn.edu.ut.udm08.client.network;
import javafx.application.Platform;
import java.util.List;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
public class JavaFXChatListenerWrapper implements ChatListener {
    private final ChatListener delegate;
    public JavaFXChatListenerWrapper(ChatListener delegate) {
        this.delegate = delegate;
    }
    private void safeRunLater(Runnable action) {
        if (delegate == null) {
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException e) {
            action.run();
        }
    }
    @Override
    public void onLoginSuccess(ProtocolMessage message) {
        safeRunLater(() -> delegate.onLoginSuccess(message));
    }
    @Override
    public void onUserListUpdated(List<UserProfile> users) {
        safeRunLater(() -> delegate.onUserListUpdated(users));
    }
    @Override
    public void onMessageReceived(ProtocolMessage message) {
        safeRunLater(() -> delegate.onMessageReceived(message));
    }
    @Override
    public void onMessageSentSuccess(String messageId) {
        safeRunLater(() -> delegate.onMessageSentSuccess(messageId));
    }
    @Override
    public void onErrorReceived(String errorCode, String errorMessage) {
        safeRunLater(() -> delegate.onErrorReceived(errorCode, errorMessage));
    }
    @Override
    public void onConnectionLost(Throwable cause) {
        safeRunLater(() -> delegate.onConnectionLost(cause));
    }
}
