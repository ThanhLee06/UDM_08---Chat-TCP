package vn.edu.ut.udm08.client.network;

import java.util.List;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

/**
 * Interface ChatListener định nghĩa các hàm Callback sự kiện mạng.
 * Tầng giao diện (Controller) sẽ implement interface này để nhận dữ liệu từ Client.
 */
public interface ChatListener {

    void onLoginSuccess(ProtocolMessage message);

    void onUserListUpdated(List<UserProfile> users);

    void onMessageReceived(ProtocolMessage message);

    void onMessageSentSuccess(String messageId);

    void onErrorReceived(String errorCode, String errorMessage);

    void onConnectionLost(Throwable cause);

    default void onMessageStatusUpdated(ProtocolMessage message) {
    }

    default void onLogoutSuccess() {
    }

    default void onSessionExpired(String errorCode, String errorMessage) {
    }

    default void onRequestCancelled(String requestId, String reason) {
    }
}
