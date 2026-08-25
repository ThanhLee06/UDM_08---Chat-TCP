package vn.edu.ut.udm08.client.network;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-12: ChatClient Send Message Tests")
class ChatClientSendMessageTest {

    private ServerSocket mockServer;
    private Thread serverThread;
    private int serverPort;
    private String loopbackHost;
    private ChatClient client;
    private volatile Socket serverSideClientSocket;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        loopbackHost = loopback.getHostAddress();
        mockServer = new ServerSocket(0, 50, loopback);
        serverPort = mockServer.getLocalPort();
        client = new ChatClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.disconnect();
        }
        if (serverSideClientSocket != null && !serverSideClientSocket.isClosed()) {
            serverSideClientSocket.close();
        }
        if (mockServer != null && !mockServer.isClosed()) {
            mockServer.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    @DisplayName("TC_01: Gửi tin nhắn CHAT thành công, Server nhận đúng nội dung, sender, target và messageId")
    void testSendMessageSuccessToServer() throws Exception {
        CountDownLatch helloReceivedLatch = new CountDownLatch(1);
        CountDownLatch chatReceivedLatch = new CountDownLatch(1);
        AtomicReference<String> chatJsonReceived = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));

                // 1. Đọc gói HELLO ban đầu
                reader.readLine();
                helloReceivedLatch.countDown();

                // 2. Đọc gói tin CHAT tiếp theo
                String chatLine = reader.readLine();
                chatJsonReceived.set(chatLine);
                chatReceivedLatch.countDown();

            } catch (IOException ignored) {
            }
        });
        serverThread.start();

        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", new StubChatListener());
        assertTrue(helloReceivedLatch.await(5, TimeUnit.SECONDS), "Server phải nhận được gói HELLO");

        // Client gửi tin nhắn CHAT
        client.sendMessage("Bob", "Xin chao Bob!");

        assertTrue(chatReceivedLatch.await(5, TimeUnit.SECONDS), "Server phải nhận được gói tin CHAT");
        assertNotNull(chatJsonReceived.get());

        ProtocolMessage chatMsg = JsonUtil.fromJson(chatJsonReceived.get());
        assertEquals(MessageType.CHAT, chatMsg.type);
        assertEquals("Alice", chatMsg.sender);
        assertEquals("Bob", chatMsg.target);
        assertEquals("Xin chao Bob!", chatMsg.content);
        assertNotNull(chatMsg.messageId, "MessageId phải được tự động sinh UUID");
        assertFalse(chatMsg.messageId.isBlank());
        assertTrue(chatMsg.timestamp > 0);
    }

    @Test
    @DisplayName("TC_02: Ném IOException khi gọi sendMessage lúc chưa kết nối Server")
    void testSendMessageWhenNotConnectedThrowsIOException() {
        IOException exception = assertThrows(IOException.class, () -> {
            client.sendMessage("Bob", "Xin chao");
        });
        assertTrue(exception.getMessage().contains("Chưa kết nối"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("TC_03: Ném IllegalArgumentException khi Target (người nhận) bị rỗng hoặc khoảng trắng")
    void testSendMessageWithBlankTargetThrowsException(String blankTarget) throws Exception {
        connectClientQuickly();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            client.sendMessage(blankTarget, "Xin chao");
        });
        assertTrue(exception.getMessage().contains("target") || exception.getMessage().contains("Người nhận"));
    }

    @Test
    @DisplayName("TC_04: Ném IllegalArgumentException khi Target là null")
    void testSendMessageWithNullTargetThrowsException() throws Exception {
        connectClientQuickly();

        assertThrows(IllegalArgumentException.class, () -> {
            client.sendMessage(null, "Xin chao");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("TC_05: Ném IllegalArgumentException khi Content (nội dung) bị rỗng hoặc khoảng trắng")
    void testSendMessageWithBlankContentThrowsException(String blankContent) throws Exception {
        connectClientQuickly();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            client.sendMessage("Bob", blankContent);
        });
        assertTrue(exception.getMessage().contains("nội dung") || exception.getMessage().contains("Nội dung"));
    }

    @Test
    @DisplayName("TC_06: Ném IllegalArgumentException khi Content là null")
    void testSendMessageWithNullContentThrowsException() throws Exception {
        connectClientQuickly();

        assertThrows(IllegalArgumentException.class, () -> {
            client.sendMessage("Bob", null);
        });
    }

    @Test
    @DisplayName("TC_07: Ném IllegalArgumentException khi nội dung tin nhắn vượt quá 5000 ký tự")
    void testSendMessageWithContentExceeding5000CharsThrowsException() throws Exception {
        connectClientQuickly();

        String longContent = "A".repeat(5001);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            client.sendMessage("Bob", longContent);
        });
        assertTrue(exception.getMessage().contains("5000"));
    }

    @Test
    @DisplayName("TC_08: Cho phép gửi nội dung đạt đúng giới hạn tối đa 5000 ký tự")
    void testSendMessageWithMaxLength5000CharsSucceeds() throws Exception {
        connectClientQuickly();

        String maxContent = "A".repeat(5000);
        assertDoesNotThrow(() -> client.sendMessage("Bob", maxContent));
    }

    @Test
    @DisplayName("TC_09: Tự động loại bỏ khoảng trắng thừa (trim) ở Target và Content")
    void testSendMessageTrimsWhitespaceInTargetAndContent() throws Exception {
        CountDownLatch chatReceivedLatch = new CountDownLatch(1);
        AtomicReference<String> chatJsonReceived = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));
                reader.readLine(); // hello

                String chatLine = reader.readLine();
                chatJsonReceived.set(chatLine);
                chatReceivedLatch.countDown();
            } catch (IOException ignored) {}
        });
        serverThread.start();

        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", new StubChatListener());
        client.sendMessage("   Bob   ", "   Noi dung can gui   ");

        assertTrue(chatReceivedLatch.await(5, TimeUnit.SECONDS));
        ProtocolMessage chatMsg = JsonUtil.fromJson(chatJsonReceived.get());
        assertEquals("Bob", chatMsg.target);
        assertEquals("Noi dung can gui", chatMsg.content);
    }

    private void connectClientQuickly() throws Exception {
        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
            } catch (IOException ignored) {}
        });
        serverThread.start();
        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", new StubChatListener());
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
