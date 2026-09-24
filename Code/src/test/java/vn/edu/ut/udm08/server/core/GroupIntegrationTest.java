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
class GroupIntegrationTest {
    @Test
    void groupPermissionsAndDeliveryUsePersistedMembership() throws Exception {
        ChatServer server = TestServer.create();
        Thread thread = new Thread(() -> { try { server.start(); } catch (IOException ignored) {} });
        thread.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertTrue(server.isRunning());
        try (Peer alice = new Peer(server.getPort()); Peer bob = new Peer(server.getPort())) {
            alice.send(TestServer.login("alice")); alice.read(MessageType.AUTH_LOGIN_OK);
            bob.send(TestServer.login("bob")); bob.read(MessageType.AUTH_LOGIN_OK);
            var command=new vn.edu.ut.udm08.shared.dto.GroupCommand();command.action="CREATE";command.name="Nhóm học";command.users=java.util.List.of("bob");
            alice.request(MessageType.GROUP_REQUEST,JsonUtil.toJson(command));
            var group=JsonUtil.fromJson(alice.read(MessageType.GROUP_RESPONSE).content,vn.edu.ut.udm08.shared.dto.GroupDetails.class);
            assertEquals(2,group.members.size());
            command.action="RENAME";command.convId=group.convId;command.name="Đổi trái phép";
            bob.request(MessageType.GROUP_REQUEST,JsonUtil.toJson(command));assertEquals("GROUP_ERROR",bob.read(MessageType.ERROR).errorCode);
            ProtocolMessage message=new ProtocolMessage(MessageType.CHAT);message.messageId=UUID.randomUUID().toString();message.sender="alice";message.target=group.convId;message.convId=group.convId;message.content="Xin chào nhóm";
            alice.send(message);alice.read(MessageType.CHAT_OK);assertEquals(message.messageId,bob.read(MessageType.CHAT).messageId);
            try(Peer charlie=new Peer(server.getPort())) {
                charlie.send(TestServer.login("charlie"));charlie.read(MessageType.AUTH_LOGIN_OK);
                message.sender="charlie";message.messageId=UUID.randomUUID().toString();charlie.send(message);assertEquals("NOT_A_MEMBER",charlie.read(MessageType.ERROR).errorCode);
                command.action="GET";charlie.request(MessageType.GROUP_REQUEST,JsonUtil.toJson(command));charlie.read(MessageType.ERROR);
            }
            command.action="LEAVE";alice.request(MessageType.GROUP_REQUEST,JsonUtil.toJson(command));alice.read(MessageType.GROUP_RESPONSE);
            command.action="RENAME";command.name="Bob làm trưởng nhóm";bob.request(MessageType.GROUP_REQUEST,JsonUtil.toJson(command));
            var renamed=JsonUtil.fromJson(bob.read(MessageType.GROUP_RESPONSE).content,vn.edu.ut.udm08.shared.dto.GroupDetails.class);
            assertEquals("Bob làm trưởng nhóm",renamed.name);assertEquals("owner",renamed.members.get(0).role);
            message.sender="alice";message.messageId=UUID.randomUUID().toString();alice.send(message);alice.read(MessageType.ERROR);
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
