package vn.edu.ut.udm08.client.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-13: Background Reader Thread (ChatReceiver) Tests")
class ChatReceiverTest {

    private ChatClient client;

    @BeforeEach
    void setUp() {
        client = new ChatClient();
    }

    @Test
    @DisplayName("TC_01: Luồng đọc ngầm đọc tuần tự và chuyển giao nhiều thông điệp JSON Lines")
    void testReceiverReadsMultipleMessagesSequentially() throws Exception {
        ProtocolMessage msg1 = new ProtocolMessage(MessageType.HELLO_OK);
        msg1.target = "Alice";

        ProtocolMessage msg2 = new ProtocolMessage(MessageType.CHAT);
        msg2.sender = "Bob";
        msg2.content = "Xin chao Alice";

        String jsonLines = JsonUtil.toJson(msg1) + "\n" + JsonUtil.toJson(msg2) + "\n";
        BufferedReader reader = new BufferedReader(new StringReader(jsonLines));

        CountDownLatch loginLatch = new CountDownLatch(1);
        CountDownLatch chatLatch = new CountDownLatch(1);

        ChatListener listener = new StubChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                loginLatch.countDown();
            }

            @Override
            public void onMessageReceived(ProtocolMessage message) {
                chatLatch.countDown();
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();

        assertTrue(loginLatch.await(3, TimeUnit.SECONDS), "Phải nhận được callback onLoginSuccess");
        assertTrue(chatLatch.await(3, TimeUnit.SECONDS), "Phải nhận được callback onMessageReceived");
        thread.join(2000);
        assertFalse(receiver.isRunning(), "Luồng phải kết thúc sau khi đọc hết dữ liệu");
    }

    @Test
    @DisplayName("TC_02: Luồng đọc tự động bỏ qua các dòng trắng, dòng trống rác")
    void testReceiverIgnoresBlankLines() throws Exception {
        ProtocolMessage chatMsg = new ProtocolMessage(MessageType.CHAT);
        chatMsg.sender = "Bob";
        chatMsg.content = "Tin nhan sau dong trong";

        String jsonWithBlanks = "\n   \n\t\n" + JsonUtil.toJson(chatMsg) + "\n\n   \n";
        BufferedReader reader = new BufferedReader(new StringReader(jsonWithBlanks));

        CountDownLatch chatLatch = new CountDownLatch(1);
        List<String> receivedErrors = Collections.synchronizedList(new ArrayList<>());

        ChatListener listener = new StubChatListener() {
            @Override
            public void onMessageReceived(ProtocolMessage message) {
                chatLatch.countDown();
            }

            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                receivedErrors.add(errorCode);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();

        assertTrue(chatLatch.await(3, TimeUnit.SECONDS));
        thread.join(2000);
        assertTrue(receivedErrors.isEmpty(), "Không được báo lỗi JSON parse cho các dòng trắng");
    }

    @Test
    @DisplayName("TC_03: Xử lý êm dịu khi gặp gói tin JSON sai cú pháp và tiếp tục đọc các gói sau")
    void testReceiverHandlesJsonSyntaxErrorGracefully() throws Exception {
        ProtocolMessage validChat = new ProtocolMessage(MessageType.CHAT);
        validChat.sender = "Bob";
        validChat.content = "Tin nhan hop le";

        String mixedStream = "DAY_LA_CHUOI_JSON_LOI\n" + JsonUtil.toJson(validChat) + "\n";
        BufferedReader reader = new BufferedReader(new StringReader(mixedStream));

        CountDownLatch errorLatch = new CountDownLatch(1);
        CountDownLatch chatLatch = new CountDownLatch(1);
        AtomicReference<String> caughtErrorCode = new AtomicReference<>();

        ChatListener listener = new StubChatListener() {
            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                caughtErrorCode.set(errorCode);
                errorLatch.countDown();
            }

            @Override
            public void onMessageReceived(ProtocolMessage message) {
                chatLatch.countDown();
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();

        assertTrue(errorLatch.await(3, TimeUnit.SECONDS), "Phải bắt được lỗi cú pháp JSON");
        assertEquals("JSON_PARSE_ERROR", caughtErrorCode.get());
        assertTrue(chatLatch.await(3, TimeUnit.SECONDS), "Phải đọc tiếp và xử lý tin nhắn hợp lệ sau đó");
        thread.join(2000);
    }

    @Test
    @DisplayName("TC_04: Tự động phát hiện khi Server ngắt kết nối (EOF line == null)")
    void testReceiverDetectsServerEofDisconnect() throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(""));

        CountDownLatch lostLatch = new CountDownLatch(1);
        AtomicReference<Throwable> caughtCause = new AtomicReference<>();

        ChatListener listener = new StubChatListener() {
            @Override
            public void onConnectionLost(Throwable cause) {
                caughtCause.set(cause);
                lostLatch.countDown();
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();

        assertTrue(lostLatch.await(3, TimeUnit.SECONDS), "Phải kích hoạt onConnectionLost khi gặp EOF");
        assertNotNull(caughtCause.get());
        thread.join(2000);
        assertFalse(receiver.isRunning());
    }

    @Test
    @DisplayName("TC_05: Dừng luồng an toàn khi gọi stop() mà không báo mất kết nối giả")
    void testReceiverStopTerminatesLoopCleanly() throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(""));

        AtomicBoolean connectionLostCalled = new AtomicBoolean(false);
        ChatListener listener = new StubChatListener() {
            @Override
            public void onConnectionLost(Throwable cause) {
                connectionLostCalled.set(true);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        receiver.stop();
        assertFalse(receiver.isRunning());

        Thread thread = new Thread(receiver);
        thread.start();
        thread.join(2000);

        assertFalse(connectionLostCalled.get(), "Không được gọi onConnectionLost khi client chủ động dừng");
    }

    @Test
    @DisplayName("TC_06: Bắt ngoại lệ IOException từ tầng mạng và thông báo onConnectionLost")
    void testReceiverHandlesIoExceptionWhileReading() throws Exception {
        Reader faultyReader = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("Socket connection reset by peer");
            }

            @Override
            public void close() throws IOException {}
        };

        BufferedReader reader = new BufferedReader(faultyReader);
        CountDownLatch lostLatch = new CountDownLatch(1);
        AtomicReference<Throwable> caughtCause = new AtomicReference<>();

        ChatListener listener = new StubChatListener() {
            @Override
            public void onConnectionLost(Throwable cause) {
                caughtCause.set(cause);
                lostLatch.countDown();
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, reader, listener);
        Thread thread = new Thread(receiver);
        thread.start();

        assertTrue(lostLatch.await(3, TimeUnit.SECONDS));
        assertNotNull(caughtCause.get());
        assertTrue(caughtCause.get().getMessage().contains("Socket connection reset"));
        thread.join(2000);
        assertFalse(receiver.isRunning());
    }

    @Test
    @DisplayName("TC_07: An toàn 100% khi ChatListener là null")
    void testReceiverNullListenerSafety() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.sender = "Bob";
        msg.content = "Test";

        String input = "BAD_JSON\n" + JsonUtil.toJson(msg) + "\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        ChatReceiver receiver = new ChatReceiver(client, reader, null);
        assertDoesNotThrow(() -> {
            Thread thread = new Thread(receiver);
            thread.start();
            thread.join(2000);
        });
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
