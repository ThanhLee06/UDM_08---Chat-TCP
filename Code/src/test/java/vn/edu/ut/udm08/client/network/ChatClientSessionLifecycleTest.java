package vn.edu.ut.udm08.client.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-099: Client Session Cleanup & Stale Request Guard Tests")
class ChatClientSessionLifecycleTest {

    @Test
    @DisplayName("TC_01: Đăng ký request gắn với session epoch hiện tại và hoàn tất đúng request")
    void testRegisterAndCompletePendingRequest() {
        ChatClient client = new ChatClient();

        long epoch = client.registerPendingRequest("req-1");

        assertEquals(client.getSessionEpoch(), epoch);
        assertEquals(1, client.getPendingRequestCount());
        assertTrue(client.completePendingRequest("req-1", epoch));
        assertEquals(0, client.getPendingRequestCount());
    }

    @Test
    @DisplayName("TC_02: Response đến muộn từ session cũ bị loại bằng session epoch")
    void testRejectsStaleEpochResponse() {
        ChatClient client = new ChatClient();
        long oldEpoch = client.registerPendingRequest("req-old");

        client.logout();

        assertFalse(client.completePendingRequest("req-old", oldEpoch));
        assertFalse(client.isCurrentEpoch(oldEpoch));
        assertEquals(0, client.getPendingRequestCount());
    }

    @Test
    @DisplayName("TC_03: LOGOUT_OK dọn pending request và báo callback đăng xuất thành công")
    void testLogoutOkClearsPendingRequestsAndNotifiesListener() throws Exception {
        ChatClient client = new ChatClient();
        client.registerPendingRequest("req-logout");

        ProtocolMessage logoutOk = new ProtocolMessage(MessageType.LOGOUT_OK);
        String input = JsonUtil.toJson(logoutOk) + "\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        AtomicInteger logoutSuccessCount = new AtomicInteger();
        AtomicInteger cancelledCount = new AtomicInteger();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onLogoutSuccess() {
                logoutSuccessCount.incrementAndGet();
            }

            @Override
            public void onRequestCancelled(String requestId, String reason) {
                cancelledCount.incrementAndGet();
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();
        thread.join(2000);

        assertEquals(1, logoutSuccessCount.get());
        assertEquals(1, cancelledCount.get());
        assertEquals(0, client.getPendingRequestCount());
    }

    @Test
    @DisplayName("TC_04: ERROR SESSION_INVALID được chuyển thành session expired, không gọi onErrorReceived thường")
    void testSessionInvalidErrorTriggersSessionExpired() throws Exception {
        ChatClient client = new ChatClient();
        client.registerPendingRequest("req-session");

        ProtocolMessage error = new ProtocolMessage(MessageType.ERROR);
        error.errorCode = "SESSION_INVALID";
        error.errorMessage = "Session khong hop le";
        String input = JsonUtil.toJson(error) + "\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        AtomicReference<String> expiredCode = new AtomicReference<>();
        AtomicInteger normalErrorCount = new AtomicInteger();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onSessionExpired(String errorCode, String errorMessage) {
                expiredCode.set(errorCode);
            }

            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                normalErrorCount.incrementAndGet();
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();
        thread.join(2000);

        assertEquals("SESSION_INVALID", expiredCode.get());
        assertEquals(0, normalErrorCount.get());
        assertEquals(0, client.getPendingRequestCount());
    }

    private static class StubChatListener implements ChatListener {
        @Override public void onLoginSuccess(ProtocolMessage message) {}
        @Override public void onUserListUpdated(List<UserProfile> users) {}
        @Override public void onMessageReceived(ProtocolMessage message) {}
        @Override public void onMessageSentSuccess(String messageId) {}
        @Override public void onErrorReceived(String errorCode, String errorMessage) {}
        @Override public void onConnectionLost(Throwable cause) {}
    }
}
