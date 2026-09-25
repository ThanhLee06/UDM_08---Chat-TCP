package vn.edu.ut.udm08.server.core;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import vn.edu.ut.udm08.support.TestServer;
import static org.junit.jupiter.api.Assertions.*;
class ReadStateIntegrationTest {
    @Test
    void readStatePersistsAndRejectsNonMembers() throws Exception {
        ChatServer server = TestServer.create();
        Thread thread = new Thread(() -> { try { server.start(); } catch (IOException ignored) {} });
        thread.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertTrue(server.isRunning());
        try (Peer alice = new Peer(server.getPort()); Peer bob = new Peer(server.getPort())) {
            alice.send(TestServer.login("alice")); alice.read(MessageType.AUTH_LOGIN_OK);
            bob.send(TestServer.login("bob")); bob.read(MessageType.AUTH_LOGIN_OK);
            ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
            message.messageId = UUID.randomUUID().toString();
            message.sender = "alice"; message.target = "bob"; message.content = "Gửi lại an toàn";
            alice.send(message); alice.read(MessageType.CHAT_OK);
            ProtocolMessage received=bob.read(MessageType.CHAT);
            bob.request(MessageType.CONVERSATION_LIST_REQUEST, "");
            var inbox=bob.read(MessageType.CONVERSATION_LIST_RESPONSE);
            assertEquals(1,inbox.conversations.stream().filter(c -> c.convId.equals(received.convId)).findFirst().orElseThrow().unreadCount);
            bob.request(MessageType.CONVERSATION_READ,JsonUtil.toJson(received));
            bob.read(MessageType.CONVERSATION_READ_OK);
            bob.request(MessageType.CONVERSATION_LIST_REQUEST, "");
            inbox=bob.read(MessageType.CONVERSATION_LIST_RESPONSE);
            assertEquals(0,inbox.conversations.stream().filter(c -> c.convId.equals(received.convId)).findFirst().orElseThrow().unreadCount);
            try (Peer charlie=new Peer(server.getPort())) {
                charlie.send(TestServer.login("charlie")); charlie.read(MessageType.AUTH_LOGIN_OK);
                charlie.request(MessageType.CONVERSATION_READ,JsonUtil.toJson(received));
                assertEquals("READ_FAILED",charlie.read(MessageType.ERROR).errorCode);
            }
            try (Peer reconnected=new Peer(server.getPort())) {
                reconnected.send(TestServer.login("bob")); reconnected.read(MessageType.AUTH_LOGIN_OK);
                reconnected.request(MessageType.CONVERSATION_LIST_REQUEST, "");
                var restored=reconnected.read(MessageType.CONVERSATION_LIST_RESPONSE);
                assertEquals(0,restored.conversations.stream().filter(c -> c.convId.equals(received.convId)).findFirst().orElseThrow().unreadCount);
            }
        } finally { server.stop(); thread.join(3000); }
    }
    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        void send(ProtocolMessage message) { writer.println(JsonUtil.toJson(message)); }
        void request(MessageType type, String content) {
            ProtocolMessage message = new ProtocolMessage(type);
            message.requestId = UUID.randomUUID().toString();
            message.content = content;
            send(message);
        }
        ProtocolMessage read(MessageType type) throws IOException {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                assertNotNull(line);
                ProtocolMessage message = JsonUtil.fromJson(line);
                if (message.type == type) return message;
            }
            throw new IOException("Missing response " + type);
        }
        public void close() throws IOException { socket.close(); }
    }
}
