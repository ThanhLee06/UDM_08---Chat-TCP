package vn.edu.ut.udm08.client.network;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@DisplayName("ST-16: Client Disconnect & Connection Loss Error Handling Tests")
class ChatClientDisconnectTest {

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
    @DisplayName("TC_01: Ngắt kết nối chủ động gửi gói tin DISCONNECT lên Server và đóng sạch tài nguyên Socket")
    void testDisconnectSendsDisconnectMessageAndClosesSocket() throws Exception {
        CountDownLatch helloReceivedLatch = new CountDownLatch(1);
        CountDownLatch disconnectReceivedLatch = new CountDownLatch(1);
        AtomicReference<String> disconnectJson = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));

                // 1. Đọc HELLO
                reader.readLine();
                helloReceivedLatch.countDown();

                // 2. Đọc DISCONNECT
                String line = reader.readLine();
                disconnectJson.set(line);
                disconnectReceivedLatch.countDown();
            } catch (IOException ignored) {}
        });
        serverThread.start();

        client.connect(loopbackHost, serverPort, "Alice", "avatar_cat", new StubChatListener());
        assertTrue(helloReceivedLatch.await(5, TimeUnit.SECONDS));
        assertTrue(client.isConnected());

        // Chủ động ngắt kết nối
        client.disconnect();

        assertTrue(disconnectReceivedLatch.await(5, TimeUnit.SECONDS), "Server phải nhận được gói tin DISCONNECT");
        assertNotNull(disconnectJson.get());

        ProtocolMessage disconnectMsg = JsonUtil.fromJson(disconnectJson.get());
        assertEquals(MessageType.DISCONNECT, disconnectMsg.type);
        assertEquals("Alice", disconnectMsg.sender);
        assertTrue(disconnectMsg.timestamp > 0);

        // Trạng thái Client phải được reset sạch sẽ
        assertFalse(client.isConnected(), "Trạng thái isConnected() phải là false sau khi disconnect");
        assertNull(client.getUsername(), "Username phải được reset về null");
        assertNull(client.getAvatarId(), "AvatarId phải được reset về null");
    }

    @Test
    @DisplayName("TC_02: Đảm bảo tính Idempotent - Gọi disconnect() nhiều lần liên tiếp không gây lỗi")
    void testDisconnectIdempotentMultipleCalls() throws Exception {
        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
            } catch (IOException ignored) {}
        });
        serverThread.start();

        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", new StubChatListener());
        assertTrue(client.isConnected());

        // Gọi lặp lại 3 lần
        assertDoesNotThrow(() -> {
            client.disconnect();
            client.disconnect();
            client.disconnect();
        });

        assertFalse(client.isConnected());
        assertNull(client.getUsername());
    }

    @Test
    @DisplayName("TC_03: Gọi disconnect() khi chưa từng kết nối thực thi êm dịu, không ném ngoại lệ")
    void testDisconnectWhenNotConnectedDoesNotThrow() {
        ChatClient freshClient = new ChatClient();
        assertFalse(freshClient.isConnected());

        assertDoesNotThrow(freshClient::disconnect);
        assertFalse(freshClient.isConnected());
    }

    @Test
    @DisplayName("TC_04: Cho phép tái kết nối (Reconnection) thành công sau khi đã ngắt kết nối trước đó")
    void testReconnectionAfterDisconnectSucceeds() throws Exception {
        CountDownLatch firstConnectLatch = new CountDownLatch(1);
        CountDownLatch secondConnectLatch = new CountDownLatch(1);
        AtomicReference<String> secondHelloJson = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try {
                // Lần 1: Alice
                Socket socket1 = mockServer.accept();
                BufferedReader reader1 = new BufferedReader(new InputStreamReader(socket1.getInputStream(), StandardCharsets.UTF_8));
                reader1.readLine();
                firstConnectLatch.countDown();

                // Lần 2: Bob
                Socket socket2 = mockServer.accept();
                BufferedReader reader2 = new BufferedReader(new InputStreamReader(socket2.getInputStream(), StandardCharsets.UTF_8));
                String line2 = reader2.readLine();
                secondHelloJson.set(line2);
                secondConnectLatch.countDown();
            } catch (IOException ignored) {}
        });
        serverThread.start();

        // 1. Kết nối lần 1
        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", new StubChatListener());
        assertTrue(firstConnectLatch.await(5, TimeUnit.SECONDS));
        client.disconnect();
        assertFalse(client.isConnected());

        // 2. Tái kết nối lần 2 với tài khoản Bob
        client.connect(loopbackHost, serverPort, "Bob", "avatar_dog", new StubChatListener());
        assertTrue(secondConnectLatch.await(5, TimeUnit.SECONDS));

        ProtocolMessage helloMsg = JsonUtil.fromJson(secondHelloJson.get());
        assertEquals("Bob", helloMsg.sender);
        assertEquals("avatar_dog", helloMsg.avatarId);
        assertTrue(client.isConnected());
        assertEquals("Bob", client.getUsername());
    }

    @Test
    @DisplayName("TC_05: Khi Server sập/mất kết nối vật lý, kích hoạt onConnectionLost và tự động dọn dẹp client")
    void testServerCrashTriggersConnectionLostAndAutoDisconnect() throws Exception {
        CountDownLatch serverAcceptedLatch = new CountDownLatch(1);
        CountDownLatch connectionLostLatch = new CountDownLatch(1);
        AtomicReference<Throwable> lostCause = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
                serverAcceptedLatch.countDown();
                // Đợi 200ms rồi đóng kết nối đột ngột từ Server (giả lập server sập)
                Thread.sleep(200);
                serverSideClientSocket.close();
            } catch (Exception ignored) {}
        });
        serverThread.start();

        ChatListener listener = new StubChatListener() {
            @Override
            public void onConnectionLost(Throwable cause) {
                lostCause.set(cause);
                connectionLostLatch.countDown();
            }
        };

        client.connect(loopbackHost, serverPort, "Alice", "avatar_1", listener);
        assertTrue(serverAcceptedLatch.await(5, TimeUnit.SECONDS));

        assertTrue(connectionLostLatch.await(5, TimeUnit.SECONDS), "Phải kích hoạt onConnectionLost khi Server đóng kết nối");
        assertNotNull(lostCause.get());

        // Client phải được tự động dọn dẹp về trạng thái ngắt kết nối
        assertFalse(client.isConnected());
        assertNull(client.getUsername());
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
