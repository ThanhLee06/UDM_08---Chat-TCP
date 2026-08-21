package vn.edu.ut.udm08.server.core;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ChatListener;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ChatServerIntegrationTest {
    private ChatServer server;
    private int port;
    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket freeSocket = new ServerSocket(0)) {
            port = freeSocket.getLocalPort();
        }
        Properties props = new Properties();
        props.setProperty("server.port", String.valueOf(port));
        ServerConfig config = ServerConfig.fromProperties(props);
        server = new ChatServer(config);
        server.start();
        assertTrue(server.isRunning());
    }
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }
    @Test
    void testEndToEndLoginAndUserListBroadcast() throws Exception {
        ChatClient client1 = new ChatClient();
        CountDownLatch loginLatch1 = new CountDownLatch(1);
        CountDownLatch userListLatch1 = new CountDownLatch(1);
        AtomicReference<List<UserProfile>> onlineUsersRef1 = new AtomicReference<>();
        client1.connect("127.0.0.1", port, "UserOne", "avatar1", new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                loginLatch1.countDown();
            }
            @Override
            public void onUserListUpdated(List<UserProfile> users) {
                onlineUsersRef1.set(users);
                userListLatch1.countDown();
            }
        });
        assertTrue(loginLatch1.await(3, TimeUnit.SECONDS));
        assertTrue(userListLatch1.await(3, TimeUnit.SECONDS));
        assertEquals(1, onlineUsersRef1.get().size());
        assertEquals("UserOne", onlineUsersRef1.get().get(0).username);
        ChatClient client2 = new ChatClient();
        CountDownLatch loginLatch2 = new CountDownLatch(1);
        client2.connect("127.0.0.1", port, "UserTwo", "avatar2", new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                loginLatch2.countDown();
            }
        });
        assertTrue(loginLatch2.await(3, TimeUnit.SECONDS));
        client1.disconnect();
        client2.disconnect();
    }
    @Test
    void testDuplicateUsernameRejection() throws Exception {
        ChatClient client1 = new ChatClient();
        CountDownLatch loginLatch1 = new CountDownLatch(1);
        client1.connect("127.0.0.1", port, "Alice", "avatar1", new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                loginLatch1.countDown();
            }
        });
        assertTrue(loginLatch1.await(3, TimeUnit.SECONDS));
        ChatClient clientDuplicate = new ChatClient();
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> errorCodeRef = new AtomicReference<>();
        clientDuplicate.connect("127.0.0.1", port, "ALICE", "avatar2", new TestChatListener() {
            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                errorCodeRef.set(errorCode);
                errorLatch.countDown();
            }
        });
        assertTrue(errorLatch.await(3, TimeUnit.SECONDS));
        assertEquals("USERNAME_TAKEN", errorCodeRef.get());
        client1.disconnect();
        clientDuplicate.disconnect();
    }
    private static class TestChatListener implements ChatListener {
        @Override
        public void onLoginSuccess(ProtocolMessage message) {}
        @Override
        public void onUserListUpdated(List<UserProfile> users) {}
        @Override
        public void onMessageReceived(ProtocolMessage message) {}
        @Override
        public void onMessageSentSuccess(String messageId) {}
        @Override
        public void onErrorReceived(String errorCode, String errorMessage) {}
        @Override
        public void onConnectionLost(Throwable cause) {}
    }
}
