package vn.edu.ut.udm08.client.network;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-15: ChatListener and JavaFXChatListenerWrapper Callback Tests")
class JavaFXChatListenerWrapperTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX Toolkit đã được khởi tạo trước đó
        }
    }

    @Test
    @DisplayName("TC_01: Callback onLoginSuccess được chuyển tiếp an toàn tới JavaFX Application Thread")
    void testOnLoginSuccessDispatchedToJavaFXThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isFxThread = new AtomicBoolean(false);
        AtomicReference<ProtocolMessage> receivedMsg = new AtomicReference<>();

        ChatListener mockListener = new StubChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                isFxThread.set(Platform.isFxApplicationThread());
                receivedMsg.set(message);
                latch.countDown();
            }
        };

        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(mockListener);
        ProtocolMessage msg = new ProtocolMessage(MessageType.HELLO_OK);
        msg.sender = "SERVER";
        msg.target = "Alice";

        // Kích hoạt từ luồng nền (Background Thread)
        new Thread(() -> wrapper.onLoginSuccess(msg)).start();

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback onLoginSuccess phải được gọi trong thời gian chờ");
        assertTrue(isFxThread.get(), "Callback phải được thực thi trên JavaFX Application Thread");
        assertEquals("Alice", receivedMsg.get().target);
    }

    @Test
    @DisplayName("TC_02: Callback onUserListUpdated chuyển danh sách user lên JavaFX Application Thread")
    void testOnUserListUpdatedDispatched() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isFxThread = new AtomicBoolean(false);
        AtomicReference<List<UserProfile>> receivedUsers = new AtomicReference<>();

        ChatListener mockListener = new StubChatListener() {
            @Override
            public void onUserListUpdated(List<UserProfile> users) {
                isFxThread.set(Platform.isFxApplicationThread());
                receivedUsers.set(users);
                latch.countDown();
            }
        };

        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(mockListener);
        List<UserProfile> users = List.of(new UserProfile("Bob", "avatar1"), new UserProfile("Charlie", "avatar2"));

        new Thread(() -> wrapper.onUserListUpdated(users)).start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(isFxThread.get());
        assertEquals(2, receivedUsers.get().size());
        assertEquals("Bob", receivedUsers.get().get(0).username);
    }

    @Test
    @DisplayName("TC_03: Callback onMessageReceived nhận tin nhắn CHAT trên JavaFX Application Thread")
    void testOnMessageReceivedDispatched() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isFxThread = new AtomicBoolean(false);
        AtomicReference<ProtocolMessage> receivedMsg = new AtomicReference<>();

        ChatListener mockListener = new StubChatListener() {
            @Override
            public void onMessageReceived(ProtocolMessage message) {
                isFxThread.set(Platform.isFxApplicationThread());
                receivedMsg.set(message);
                latch.countDown();
            }
        };

        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(mockListener);
        ProtocolMessage chat = new ProtocolMessage(MessageType.CHAT);
        chat.sender = "Bob";
        chat.content = "Xin chao Alice!";

        new Thread(() -> wrapper.onMessageReceived(chat)).start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(isFxThread.get());
        assertEquals("Xin chao Alice!", receivedMsg.get().content);
        assertEquals("Bob", receivedMsg.get().sender);
    }

    @Test
    @DisplayName("TC_04: Callback onMessageSentSuccess nhận mã messageId xác nhận gửi thành công")
    void testOnMessageSentSuccessDispatched() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isFxThread = new AtomicBoolean(false);
        AtomicReference<String> receivedId = new AtomicReference<>();

        ChatListener mockListener = new StubChatListener() {
            @Override
            public void onMessageSentSuccess(String messageId) {
                isFxThread.set(Platform.isFxApplicationThread());
                receivedId.set(messageId);
                latch.countDown();
            }
        };

        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(mockListener);
        String testId = "msg-uuid-12345";

        new Thread(() -> wrapper.onMessageSentSuccess(testId)).start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(isFxThread.get());
        assertEquals("msg-uuid-12345", receivedId.get());
    }

    @Test
    @DisplayName("TC_05: Callback onErrorReceived nhận mã lỗi và thông điệp lỗi")
    void testOnErrorReceivedDispatched() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isFxThread = new AtomicBoolean(false);
        AtomicReference<String> errCode = new AtomicReference<>();
        AtomicReference<String> errMsg = new AtomicReference<>();

        ChatListener mockListener = new StubChatListener() {
            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                isFxThread.set(Platform.isFxApplicationThread());
                errCode.set(errorCode);
                errMsg.set(errorMessage);
                latch.countDown();
            }
        };

        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(mockListener);

        new Thread(() -> wrapper.onErrorReceived("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng")).start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(isFxThread.get());
        assertEquals("USERNAME_TAKEN", errCode.get());
        assertEquals("Tên đăng nhập đã được sử dụng", errMsg.get());
    }

    @Test
    @DisplayName("TC_06: Callback onConnectionLost thông báo sự cố mất kết nối")
    void testOnConnectionLostDispatched() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isFxThread = new AtomicBoolean(false);
        AtomicReference<Throwable> receivedCause = new AtomicReference<>();

        ChatListener mockListener = new StubChatListener() {
            @Override
            public void onConnectionLost(Throwable cause) {
                isFxThread.set(Platform.isFxApplicationThread());
                receivedCause.set(cause);
                latch.countDown();
            }
        };

        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(mockListener);
        IOException cause = new IOException("Server connection reset");

        new Thread(() -> wrapper.onConnectionLost(cause)).start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(isFxThread.get());
        assertEquals("Server connection reset", receivedCause.get().getMessage());
    }

    @Test
    @DisplayName("TC_07: Xử lý an toàn khi delegate là null không ném NullPointerException")
    void testNullDelegateDoesNotThrow() {
        JavaFXChatListenerWrapper wrapper = new JavaFXChatListenerWrapper(null);

        assertDoesNotThrow(() -> wrapper.onLoginSuccess(new ProtocolMessage(MessageType.HELLO_OK)));
        assertDoesNotThrow(() -> wrapper.onUserListUpdated(List.of()));
        assertDoesNotThrow(() -> wrapper.onMessageReceived(new ProtocolMessage(MessageType.CHAT)));
        assertDoesNotThrow(() -> wrapper.onMessageSentSuccess("id-1"));
        assertDoesNotThrow(() -> wrapper.onErrorReceived("ERR", "msg"));
        assertDoesNotThrow(() -> wrapper.onConnectionLost(new IOException()));
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
