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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-11: ChatClient TCP Connect & HELLO Login Tests")
class ChatClientConnectTest {

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
    @DisplayName("TC_01: Kết nối TCP thành công và tự động gửi gói tin HELLO lên Server")
    void testConnectSuccessAndSendsHelloMessage() throws Exception {
        CountDownLatch serverReceivedLatch = new CountDownLatch(1);
        CountDownLatch clientLoginOkLatch = new CountDownLatch(1);
        AtomicReference<String> receivedJson = new AtomicReference<>();
        AtomicReference<ProtocolMessage> clientReceivedHelloOk = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(serverSideClientSocket.getOutputStream(), StandardCharsets.UTF_8), true);

                String line = reader.readLine();
                receivedJson.set(line);
                serverReceivedLatch.countDown();

                ProtocolMessage okMsg = new ProtocolMessage(MessageType.HELLO_OK);
                okMsg.sender = "SERVER";
                okMsg.target = "Alice";
                writer.println(JsonUtil.toJson(okMsg));

            } catch (IOException ignored) {
            }
        });
        serverThread.start();

        ChatListener listener = new StubChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                clientReceivedHelloOk.set(message);
                clientLoginOkLatch.countDown();
            }
        };

        client.connect(loopbackHost, serverPort, "Alice", "avatar_cat", listener);

        assertTrue(serverReceivedLatch.await(5, TimeUnit.SECONDS), "Server phải nhận được gói tin HELLO");
        assertNotNull(receivedJson.get());

        ProtocolMessage helloMsg = JsonUtil.fromJson(receivedJson.get());
        assertEquals(MessageType.HELLO, helloMsg.type);
        assertEquals("Alice", helloMsg.sender);
        assertEquals("avatar_cat", helloMsg.avatarId);
        assertTrue(helloMsg.timestamp > 0);

        assertTrue(clientLoginOkLatch.await(5, TimeUnit.SECONDS), "Client phải nhận được callback onLoginSuccess");
        assertNotNull(clientReceivedHelloOk.get());
        assertEquals("Alice", clientReceivedHelloOk.get().target);

        assertTrue(client.isConnected());
        assertEquals("Alice", client.getUsername());
        assertEquals("avatar_cat", client.getAvatarId());
    }

    @Test
    @DisplayName("TC_02: Ném IOException khi kết nối tới Server không hoạt động")
    void testConnectWhenServerOfflineThrowsIOException() throws IOException {
        mockServer.close();

        assertThrows(IOException.class, () -> {
            client.connect(loopbackHost, serverPort, "Bob", "avatar_dog", new StubChatListener());
        });

        assertFalse(client.isConnected());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("TC_03: Ném IllegalArgumentException khi Username rỗng")
    void testConnectWithBlankUsernameThrowsException(String blankUsername) {
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect(loopbackHost, serverPort, blankUsername, "avatar_1", new StubChatListener());
        });
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("TC_04: Ném IllegalArgumentException khi Username là null")
    void testConnectWithNullUsernameThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect(loopbackHost, serverPort, null, "avatar_1", new StubChatListener());
        });
        assertFalse(client.isConnected());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("TC_05: Ném IllegalArgumentException khi AvatarId rỗng hoặc null")
    void testConnectWithInvalidAvatarThrowsException(String invalidAvatar) {
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect(loopbackHost, serverPort, "Alice", invalidAvatar, new StubChatListener());
        });
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect(loopbackHost, serverPort, "Alice", null, new StubChatListener());
        });
    }

    @Test
    @DisplayName("TC_06: Ném IllegalArgumentException khi Host hoặc Port không hợp lệ")
    void testConnectWithInvalidHostOrPortThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect("", serverPort, "Alice", "avatar_1", new StubChatListener());
        });
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect(loopbackHost, -1, "Alice", "avatar_1", new StubChatListener());
        });
        assertThrows(IllegalArgumentException.class, () -> {
            client.connect(loopbackHost, 70000, "Alice", "avatar_1", new StubChatListener());
        });
    }

    @Test
    @DisplayName("TC_07: Ném IllegalStateException khi client đang kết nối mà gọi connect lần nữa")
    void testConnectWhenAlreadyConnectedThrowsIllegalStateException() throws Exception {
        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
            } catch (IOException ignored) {}
        });
        serverThread.start();

        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", new StubChatListener());
        assertTrue(client.isConnected());

        assertThrows(IllegalStateException.class, () -> {
            client.connect(loopbackHost, serverPort, "Alice2", "avatar_2", new StubChatListener());
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
