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
class AttachmentIntegrationTest {
    @Test
    void fileRoundTripAndPhoneLookupEnforceAccess() throws Exception {
        ChatServer server = TestServer.create();
        Thread thread = new Thread(() -> { try { server.start(); } catch (IOException ignored) {} });
        thread.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertTrue(server.isRunning());
        try (Peer alice = new Peer(server.getPort()); Peer bob = new Peer(server.getPort())) {
            alice.send(TestServer.login("alice")); alice.read(MessageType.AUTH_LOGIN_OK);
            bob.send(TestServer.login("bob")); bob.read(MessageType.AUTH_LOGIN_OK);
            alice.request(MessageType.PHONE_LOOKUP,"0900000002");assertEquals("bob",alice.read(MessageType.PHONE_LOOKUP_RESPONSE).users.get(0).username);
            alice.request(MessageType.PHONE_LOOKUP,"0900");assertEquals("INVALID_PHONE",alice.read(MessageType.ERROR).errorCode);
            alice.request(MessageType.PHONE_LOOKUP,"+84900000002");assertEquals("bob",alice.read(MessageType.PHONE_LOOKUP_RESPONSE).users.get(0).username);
            ProtocolMessage message=new ProtocolMessage(MessageType.CHAT);message.messageId=UUID.randomUUID().toString();message.sender="alice";message.target="bob";message.content="Tạo hội thoại";alice.send(message);alice.read(MessageType.CHAT_OK);String convId=bob.read(MessageType.CHAT).convId;
            byte[] bytes="Tệp tiếng Việt 😀".getBytes(StandardCharsets.UTF_8);
            var file=new vn.edu.ut.udm08.shared.dto.Attachment();file.action="BEGIN";file.convId=convId;file.name="ghi-chu.txt";file.size=bytes.length;
            alice.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));file=JsonUtil.fromJson(alice.read(MessageType.FILE_RESPONSE).content,vn.edu.ut.udm08.shared.dto.Attachment.class);
            file.action="CHUNK";file.offset=0;file.data=java.util.Base64.getEncoder().encodeToString(bytes);alice.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));alice.read(MessageType.FILE_RESPONSE);
            file.action="FINISH";file.data=null;alice.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));alice.read(MessageType.FILE_RESPONSE);
            file.action="GET";file.offset=0;bob.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));var downloaded=JsonUtil.fromJson(bob.read(MessageType.FILE_RESPONSE).content,vn.edu.ut.udm08.shared.dto.Attachment.class);assertArrayEquals(bytes,java.util.Base64.getDecoder().decode(downloaded.data));
            try(Peer charlie=new Peer(server.getPort())){charlie.send(TestServer.login("charlie"));charlie.read(MessageType.AUTH_LOGIN_OK);charlie.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));assertEquals("FILE_ERROR",charlie.read(MessageType.ERROR).errorCode);}
            file.action="BEGIN";file.name="../../outside.txt";alice.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));alice.read(MessageType.ERROR);
            file.name="large.txt";file.size=6*1024*1024;alice.request(MessageType.FILE_REQUEST,JsonUtil.toJson(file));alice.read(MessageType.ERROR);
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
