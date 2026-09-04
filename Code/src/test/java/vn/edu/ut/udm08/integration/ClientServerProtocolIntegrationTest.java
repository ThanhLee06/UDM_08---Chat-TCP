package vn.edu.ut.udm08.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ChatListener;
import vn.edu.ut.udm08.server.core.ChatServer;
import vn.edu.ut.udm08.server.core.ServerConfig;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientServerProtocolIntegrationTest {

    private static final long TIMEOUT_SECONDS = 5;

    private ChatServer server;
    private Thread serverThread;

    private ChatClient alice;
    private ChatClient bob;

    @BeforeEach
    void setUp() throws Exception {

        server = new ChatServer(ServerConfig.load());

        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                if (server.isRunning()) {
                    throw new RuntimeException(e);
                }
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();

        waitForServerToStart();
    }

    @AfterEach
    void tearDown() throws InterruptedException {

        if (alice != null) {
            alice.disconnect();
        }

        if (bob != null) {
            bob.disconnect();
        }

        if (server != null) {
            server.stop();
        }

        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    @Test
    void shouldConnectClientToServer() throws Exception {

        CountDownLatch loginLatch = new CountDownLatch(1);

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                loginLatch.countDown();
            }
        };

        alice = createClient();

        connectClient(
                alice,
                "alice",
                "avatar-alice",
                aliceListener
        );

        assertTrue(
                loginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Client should receive login success from server"
        );

        assertTrue(alice.isConnected());
        assertTrue(server.isRunning());
    }

    @Test
    void shouldRecognizeHelloMessage() throws Exception {

        CountDownLatch loginLatch = new CountDownLatch(1);
        AtomicReference<ProtocolMessage> response =
                new AtomicReference<>();

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                response.set(message);
                loginLatch.countDown();
            }
        };

        alice = createClient();

        connectClient(
                alice,
                "alice",
                "avatar-alice",
                aliceListener
        );

        assertTrue(
                loginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Server should recognize HELLO and respond"
        );

        ProtocolMessage message = response.get();

        assertNotNull(message);
        assertEquals(MessageType.HELLO_OK, message.type);
        assertEquals("SERVER", message.sender);
        assertEquals("alice", message.target);
    }

    @Test
    void shouldReceiveServerResponseThroughClient() throws Exception {

        CountDownLatch loginLatch = new CountDownLatch(1);
        AtomicReference<ProtocolMessage> receivedMessage =
                new AtomicReference<>();

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                receivedMessage.set(message);
                loginLatch.countDown();
            }
        };

        alice = createClient();

        connectClient(
                alice,
                "alice",
                "avatar-alice",
                aliceListener
        );

        assertTrue(
                loginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Client should receive HELLO_OK from server"
        );

        ProtocolMessage message = receivedMessage.get();

        assertNotNull(message);
        assertEquals(MessageType.HELLO_OK, message.type);
    }

    @Test
    void shouldAuthenticateTwoClients() throws Exception {

        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        CountDownLatch bobLoginLatch = new CountDownLatch(1);

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                aliceLoginLatch.countDown();
            }
        };

        TestChatListener bobListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                bobLoginLatch.countDown();
            }
        };

        alice = createClient();
        bob = createClient();

        connectClient(alice, "alice", "avatar-alice", aliceListener);

        connectClient(bob, "bob", "avatar-bob", bobListener);

        assertTrue(aliceLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Alice should authenticate successfully");

        assertTrue(bobLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Bob should authenticate successfully");

        assertTrue(alice.isConnected());
        assertTrue(bob.isConnected());

        assertNotNull(server.getRegistry().find("alice"));
        assertNotNull(server.getRegistry().find("bob"));

        assertEquals("alice", server.getRegistry().find("alice").getUsername());

        assertEquals("bob", server.getRegistry().find("bob").getUsername());
    }

    @Test
    void shouldRouteChatMessageFromAliceToBob() throws Exception {

        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        CountDownLatch bobLoginLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);

        AtomicReference<ProtocolMessage> receivedMessage = new AtomicReference<>();

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                aliceLoginLatch.countDown();
            }
        };

        TestChatListener bobListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                bobLoginLatch.countDown();
            }

            @Override
            public void onMessageReceived(ProtocolMessage message) {
                receivedMessage.set(message);
                messageLatch.countDown();
            }
        };

        alice = createClient();
        bob = createClient();

        connectClient(alice, "alice", "avatar-alice", aliceListener);

        connectClient(bob, "bob", "avatar-bob", bobListener);

        assertTrue(aliceLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertTrue(bobLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        alice.sendMessage("bob", "Hello Bob");

        assertTrue(messageLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Bob should receive Alice's CHAT message");

        ProtocolMessage message = receivedMessage.get();

        assertNotNull(message);
        assertEquals(MessageType.CHAT, message.type);
        assertEquals("alice", message.sender);
        assertEquals("bob", message.target);
        assertEquals("Hello Bob", message.content);
    }

    @Test
    void shouldPreserveChatMessageMetadata() throws Exception {

        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        CountDownLatch bobLoginLatch = new CountDownLatch(1);
        CountDownLatch sentLatch = new CountDownLatch(1);
        CountDownLatch receivedLatch = new CountDownLatch(1);

        AtomicReference<String> sentMessageId = new AtomicReference<>();

        AtomicReference<ProtocolMessage> receivedMessage = new AtomicReference<>();

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                aliceLoginLatch.countDown();
            }

            @Override
            public void onMessageSentSuccess(String messageId) {
                sentMessageId.set(messageId);
                sentLatch.countDown();
            }
        };

        TestChatListener bobListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                bobLoginLatch.countDown();
            }

            @Override
            public void onMessageReceived(ProtocolMessage message) {
                receivedMessage.set(message);
                receivedLatch.countDown();
            }
        };

        alice = createClient();
        bob = createClient();

        connectClient(alice, "alice", "avatar-alice", aliceListener);

        connectClient(bob, "bob", "avatar-bob", bobListener);

        assertTrue(aliceLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertTrue(bobLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        alice.sendMessage("bob", "Metadata test");

        assertTrue(sentLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Alice should receive CHAT_OK");

        assertTrue(receivedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Bob should receive CHAT");

        ProtocolMessage message = receivedMessage.get();

        assertNotNull(message);
        assertNotNull(sentMessageId.get());

        assertEquals(MessageType.CHAT, message.type);
        assertEquals(sentMessageId.get(), message.messageId);
        assertEquals("alice", message.sender);
        assertEquals("bob", message.target);
        assertEquals("Metadata test", message.content);
        assertNotNull(message.timestamp);
        assertTrue(message.timestamp > 0);
    }

    @Test
    void shouldPreserveUnicodeChatContent() throws Exception {

        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        CountDownLatch bobLoginLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);

        AtomicReference<ProtocolMessage> receivedMessage = new AtomicReference<>();

        String unicodeContent = "Xin chào Bob 👋 — Tiếng Việt có dấu";

        TestChatListener aliceListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                aliceLoginLatch.countDown();
            }
        };

        TestChatListener bobListener = new TestChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                bobLoginLatch.countDown();
            }

            @Override
            public void onMessageReceived(ProtocolMessage message) {
                receivedMessage.set(message);
                messageLatch.countDown();
            }
        };

        alice = createClient();
        bob = createClient();

        connectClient(alice, "alice", "avatar-alice", aliceListener);

        connectClient(bob, "bob", "avatar-bob", bobListener);

        assertTrue(aliceLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertTrue(bobLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        alice.sendMessage("bob", unicodeContent);

        assertTrue(messageLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Bob should receive Unicode message");

        ProtocolMessage message = receivedMessage.get();

        assertNotNull(message);
        assertEquals(unicodeContent, message.content);
        assertEquals("alice", message.sender);
        assertEquals("bob", message.target);
    }

    @Test
    void shouldDisconnectAndReconnectClient() throws Exception {

        CountDownLatch aliceLoginLatch = new CountDownLatch(1);

        TestChatListener firstAliceListener =
                new TestChatListener() {
                    @Override
                    public void onLoginSuccess(
                            ProtocolMessage message) {
                        aliceLoginLatch.countDown();
                    }
                };

        alice = createClient();

        connectClient(alice, "alice", "avatar-alice", firstAliceListener);

        assertTrue(aliceLoginLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertNotNull(server.getRegistry().find("alice"));

        alice.disconnect();

        waitUntilUserRemoved("alice");

        assertNull(server.getRegistry().find("alice"));

        assertTrue(server.isRunning());

        CountDownLatch reconnectLatch = new CountDownLatch(1);

        TestChatListener reconnectListener = new TestChatListener() {
                    @Override
                    public void onLoginSuccess(
                            ProtocolMessage message) {
                        reconnectLatch.countDown();
                    }
                };

        alice = createClient();

        connectClient(alice, "alice", "avatar-alice", reconnectListener);

        assertTrue(reconnectLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client should reconnect successfully");

        assertTrue(alice.isConnected());

        assertNotNull(server.getRegistry().find("alice"));

        assertTrue(server.isRunning());
    }

    private ChatClient createClient() {
        return new ChatClient();
    }

    private void connectClient(ChatClient client, String username, String avatarId, ChatListener listener) throws IOException {
        client.connect(
                "localhost",
                server.getPort(),
                username,
                avatarId,
                listener
        );
    }

    private void waitForServerToStart() throws InterruptedException {

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);

        while (!server.isRunning()) {

            if (System.currentTimeMillis() >= deadline) {
                fail("ChatServer did not start within timeout");
            }

            Thread.sleep(20);
        }
    }

    private void waitUntilUserRemoved(String username) throws InterruptedException {

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);

        while (server.getRegistry().find(username) != null) {

            if (System.currentTimeMillis() >= deadline) {
                fail("User was not removed from OnlineUserRegistry");
            }

            Thread.sleep(20);
        }
    }

    private static class TestChatListener implements ChatListener {

        @Override
        public void onLoginSuccess(
                ProtocolMessage message) {
        }

        @Override
        public void onUserListUpdated(
                List<UserProfile> users) {
        }

        @Override
        public void onMessageReceived(
                ProtocolMessage message) {
        }

        @Override
        public void onMessageSentSuccess(
                String messageId) {
        }

        @Override
        public void onErrorReceived(
                String errorCode,
                String errorMessage) {
        }

        @Override
        public void onConnectionLost(
                Throwable cause) {
        }
    }
}