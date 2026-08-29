package vn.edu.ut.udm08.server.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChatServerTest {

    private ChatServer server;
    private Thread serverThread;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }

        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    void shouldStartServerOnConfiguredPort() throws Exception {
        server = createServer();

        startServer();

        assertTrue(server.isRunning());
        assertTrue(server.getPort() > 0);
    }

    @Test
    void shouldAcceptTcpClientConnection() throws Exception {
        server = createServer();
        startServer();

        try (Socket client = new Socket("localhost", server.getPort())) {
            assertTrue(client.isConnected());
            assertFalse(client.isClosed());
        }
    }

    @Test
    void shouldCreateSessionAndHandleHello() throws Exception {
        server = createServer();
        startServer();

        try (Socket client = new Socket("localhost", server.getPort())) {

            client.setSoTimeout(2000);

            BufferedReader reader = reader(client);
            PrintWriter writer = writer(client);

            ProtocolMessage hello = new ProtocolMessage(MessageType.HELLO);

            hello.sender = "alice";
            hello.avatarId = "avatar-01";

            writer.println(JsonUtil.toJson(hello));

            ProtocolMessage helloOk = readMessage(reader);

            ProtocolMessage userList = readMessage(reader);

            assertEquals(MessageType.HELLO_OK, helloOk.type);
            assertEquals("SERVER", helloOk.sender);
            assertEquals("alice", helloOk.target);

            assertEquals(MessageType.USER_LIST, userList.type);
            assertEquals(1, userList.users.size());
            assertEquals("alice", userList.users.get(0).username);
        }
    }

    @Test
    void shouldDispatchChatToMessageRouter() throws Exception {
        server = createServer();
        startServer();

        try (Socket alice = new Socket("localhost", server.getPort());
             Socket bob = new Socket("localhost", server.getPort())) {

            alice.setSoTimeout(2000);
            bob.setSoTimeout(2000);

            BufferedReader aliceReader = reader(alice);
            PrintWriter aliceWriter = writer(alice);

            BufferedReader bobReader = reader(bob);
            PrintWriter bobWriter = writer(bob);

            sendHello(aliceWriter, "alice", "avatar-01");

            readMessage(aliceReader);
            readMessage(aliceReader);

            sendHello(bobWriter, "bob", "avatar-02");

            readMessage(bobReader);
            readMessage(bobReader);

            // Updated online list sent to Alice.
            readMessage(aliceReader);

            ProtocolMessage chat = new ProtocolMessage(MessageType.CHAT);

            chat.messageId = "chat-001";
            chat.sender = "alice";
            chat.target = "bob";
            chat.content = "Hello Bob";

            aliceWriter.println(JsonUtil.toJson(chat));

            ProtocolMessage received = readMessage(bobReader);

            ProtocolMessage chatOk = readMessage(aliceReader);

            assertEquals(MessageType.CHAT, received.type);
            assertEquals("alice", received.sender);
            assertEquals("bob", received.target);
            assertEquals("Hello Bob", received.content);

            assertEquals(MessageType.CHAT_OK, chatOk.type);
            assertEquals("chat-001", chatOk.messageId);
            assertEquals("SERVER", chatOk.sender);
        }
    }

    @Test
    void shouldAcceptMultipleClientsConcurrently() throws Exception {
        server = createServer();
        startServer();

        try (Socket client1 = new Socket("localhost", server.getPort());
             Socket client2 = new Socket("localhost", server.getPort());
             Socket client3 = new Socket("localhost", server.getPort())) {

            assertTrue(client1.isConnected());
            assertTrue(client2.isConnected());
            assertTrue(client3.isConnected());

            assertTrue(server.isRunning());
        }
    }

    @Test
    void shouldHandleClientDisconnectWithoutStoppingServer() throws Exception {
        server = createServer();
        startServer();

        Socket client = new Socket("localhost", server.getPort());

        client.close();

        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(server.isRunning());

        try (Socket anotherClient = new Socket("localhost", server.getPort())) {
            assertTrue(anotherClient.isConnected());
        }
    }

    @Test
    void shouldRemoveAuthenticatedClientAfterDisconnect() throws Exception {
        server = createServer();
        startServer();

        Socket client = new Socket("localhost", server.getPort());

        client.setSoTimeout(2000);

        BufferedReader reader = reader(client);
        PrintWriter writer = writer(client);

        sendHello(writer, "alice", "avatar-01");

        readMessage(reader);
        readMessage(reader);

        assertNotNull(server.getRegistry().find("alice"));

        client.close();

        waitUntilUserRemoved("alice");

        assertNull(server.getRegistry().find("alice"));
    }

    @Test
    void shouldStopServerGracefully() throws Exception {
        server = createServer();
        startServer();

        assertTrue(server.isRunning());

        server.stop();

        assertFalse(server.isRunning());
    }

    @Test
    void shouldRejectSecondStartWhileRunning() throws Exception {
        server = createServer();
        startServer();

        assertThrows(IllegalStateException.class, () -> server.start());
    }

    private ChatServer createServer() {

        Properties properties = new Properties();

        /*
         * Port 0 lets the operating system assign
         * an available ephemeral port.
         */
        properties.setProperty("server.port", "0");

        ServerConfig config = ServerConfig.fromProperties(properties);

        return new ChatServer(config);
    }

    private void startServer() throws Exception {

        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                if (server.isRunning()) {
                    throw new RuntimeException(e);
                }
            }
        });

        serverThread.start();

        long timeout = System.currentTimeMillis() + 2000;

        while (!server.isRunning() && System.currentTimeMillis() < timeout) {
            TimeUnit.MILLISECONDS.sleep(10);
        }

        assertTrue(server.isRunning(), "Server did not start");

        assertTrue(server.getPort() > 0, "Server did not bind to a valid port");
    }

    private BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private PrintWriter writer(Socket socket) throws IOException {
        return new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void sendHello(PrintWriter writer, String username, String avatarId) throws IOException {
        ProtocolMessage hello = new ProtocolMessage(MessageType.HELLO);

        hello.sender = username;
        hello.avatarId = avatarId;

        writer.println(JsonUtil.toJson(hello));
    }

    private ProtocolMessage readMessage(BufferedReader reader) throws IOException {

        String json = reader.readLine();

        assertNotNull(json, "Expected a message from server");

        return JsonUtil.fromJson(json);
    }

    private void waitUntilUserRemoved(String username) throws InterruptedException {
        long timeout = System.currentTimeMillis() + 2000;

        while (server.getRegistry().find(username) != null && System.currentTimeMillis() < timeout) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }

    //=============
    @Test
    void shouldKeepServerRunningAfterInvalidJsonFromClient() throws Exception {
        server = createServer();
        startServer();

        try (Socket invalidClient = new Socket("localhost", server.getPort())) {
            PrintWriter writer = writer(invalidClient);
            writer.println("this-is-not-valid-json");

            TimeUnit.MILLISECONDS.sleep(100);
        }

        assertTrue(server.isRunning());

        try (Socket anotherClient = new Socket("localhost", server.getPort())) {
            assertTrue(anotherClient.isConnected());
            assertFalse(anotherClient.isClosed());
        }
    }

    @Test
    void shouldKeepOtherClientWorkingAfterOneClientSendsInvalidJson() throws Exception {
        server = createServer();
        startServer();

        try (Socket invalidClient = new Socket("localhost", server.getPort());
             Socket validClient = new Socket("localhost", server.getPort())) {

            invalidClient.setSoTimeout(2000);
            validClient.setSoTimeout(2000);

            BufferedReader validReader = reader(validClient);
            PrintWriter invalidWriter = writer(invalidClient);
            PrintWriter validWriter = writer(validClient);

            // Client A sends malformed JSON.
            invalidWriter.println("this-is-not-valid-json");

            TimeUnit.MILLISECONDS.sleep(100);

            // Client B must still be able to communicate normally.
            ProtocolMessage hello = new ProtocolMessage(MessageType.HELLO);
            hello.sender = "bob";
            hello.avatarId = "avatar-02";

            validWriter.println(JsonUtil.toJson(hello));

            ProtocolMessage helloOk = readMessage(validReader);
            ProtocolMessage userList = readMessage(validReader);

            assertEquals(MessageType.HELLO_OK, helloOk.type);
            assertEquals("bob", helloOk.target);

            assertEquals(MessageType.USER_LIST, userList.type);
            assertTrue(userList.users.stream()
                    .anyMatch(user -> "bob".equals(user.username)));

            assertTrue(server.isRunning());
        }
    }

    @Test
    void shouldKeepServerAcceptingClientsAfterOneClientSessionFails() throws Exception {
        server = createServer();
        startServer();

        try (Socket failedClient = new Socket("localhost", server.getPort())) {

            PrintWriter failedWriter = writer(failedClient);

            failedWriter.println("malformed-json");

            TimeUnit.MILLISECONDS.sleep(100);

            assertTrue(server.isRunning());

            try (Socket newClient = new Socket("localhost", server.getPort())) {

                newClient.setSoTimeout(2000);

                BufferedReader reader = reader(newClient);
                PrintWriter writer = writer(newClient);

                sendHello(writer, "charlie", "avatar-03");

                ProtocolMessage helloOk = readMessage(reader);
                ProtocolMessage userList = readMessage(reader);

                assertEquals(MessageType.HELLO_OK, helloOk.type);
                assertEquals("charlie", helloOk.target);

                assertEquals(MessageType.USER_LIST, userList.type);
                assertTrue(userList.users.stream()
                        .anyMatch(user -> "charlie".equals(user.username)));
            }
        }

        assertTrue(server.isRunning());
    }
}