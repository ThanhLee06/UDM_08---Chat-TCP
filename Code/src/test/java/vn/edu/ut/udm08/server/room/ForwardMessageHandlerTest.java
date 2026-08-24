package vn.edu.ut.udm08.server.room;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
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

import static org.junit.jupiter.api.Assertions.*;

public class ForwardMessageHandlerTest {

    private OnlineUserRegistry registry;
    private InMemoryMessageDao messageDao;
    private ForwardMessageHandler handler;

    private TestConnection aliceConnection;
    private TestConnection bobConnection;
    private TestConnection charlieConnection;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        messageDao = new InMemoryMessageDao();
        handler = new ForwardMessageHandler(registry, messageDao);

        aliceConnection = new TestConnection("Alice", "01");
        bobConnection = new TestConnection("Bob", "02");
        charlieConnection = new TestConnection("Charlie", "03");

        registry.register(aliceConnection.session);
        registry.register(bobConnection.session);
        registry.register(charlieConnection.session);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (aliceConnection != null) aliceConnection.close();
        if (bobConnection != null) bobConnection.close();
        if (charlieConnection != null) charlieConnection.close();
    }

    @Test
    public void testForwardHopLeTaoTinMoiVaBroadcast() throws Exception {
        // Setup:
        // Room 1 (room-src): Alice, Bob
        // Room 2 (room-dst): Alice, Charlie
        messageDao.addConversationMember("room-src", "Alice");
        messageDao.addConversationMember("room-src", "Bob");

        messageDao.addConversationMember("room-dst", "Alice");
        messageDao.addConversationMember("room-dst", "Charlie");

        // Tin nhan nguoi dung goc trong room-src
        ProtocolMessage srcMsg = new ProtocolMessage(MessageType.CHAT);
        srcMsg.messageId = "msg-src-100";
        srcMsg.sender = "Bob";
        srcMsg.convId = "room-src";
        srcMsg.target = "room-src";
        srcMsg.content = "Tin nhắn gốc quan trọng";
        srcMsg.timestamp = System.currentTimeMillis();
        messageDao.save(srcMsg);

        // Request forward tu Alice sang room-dst
        ProtocolMessage fwdReq = new ProtocolMessage(MessageType.CHAT_FORWARD);
        fwdReq.messageId = "fwd-req-001";
        fwdReq.sender = "Alice";
        fwdReq.convId = "room-dst";
        fwdReq.fwdFrom = "msg-src-100";
        fwdReq.content = "Nội dung chuyển tiếp";

        handler.handleForwardMessage(aliceConnection.session, fwdReq);

        // 1. Sender (Alice) nhận CHAT_OK
        ProtocolMessage aliceResp = aliceConnection.readMessage();
        assertNotNull(aliceResp);
        assertEquals(MessageType.CHAT_OK, aliceResp.type);
        assertEquals("fwd-req-001", aliceResp.messageId);

        // 2. Charlie (thành viên room-dst) nhận được tin nhắn mới broadcast
        ProtocolMessage charlieReceived = charlieConnection.readMessage();
        assertNotNull(charlieReceived);
        assertEquals(MessageType.CHAT, charlieReceived.type);
        assertEquals("Alice", charlieReceived.sender);
        assertEquals("room-dst", charlieReceived.convId);
        assertEquals("msg-src-100", charlieReceived.fwdFrom);
        assertEquals("forward", charlieReceived.kind);
        assertEquals("Nội dung chuyển tiếp", charlieReceived.content);

        // 3. Tin nhắn mới đã được lưu vào DB
        ProtocolMessage savedMsg = messageDao.findById("fwd-req-001");
        assertNotNull(savedMsg);
        assertEquals("forward", savedMsg.kind);
        assertEquals("msg-src-100", savedMsg.fwdFrom);
    }

    @Test
    public void testForwardTinNguonKhongTonTaiTraLoi() throws Exception {
        messageDao.addConversationMember("room-dst", "Alice");

        ProtocolMessage fwdReq = new ProtocolMessage(MessageType.CHAT_FORWARD);
        fwdReq.messageId = "fwd-req-002";
        fwdReq.sender = "Alice";
        fwdReq.convId = "room-dst";
        fwdReq.fwdFrom = "msg-not-found-999";
        fwdReq.content = "Noi dung forward";

        handler.handleForwardMessage(aliceConnection.session, fwdReq);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("SOURCE_MSG_NOT_FOUND", err.errorCode);
        assertEquals("Tin nguồn không tồn tại", err.errorMessage);
    }

    @Test
    public void testForwardUserKhongCoQuyenDocTinNguonTraLoi() throws Exception {
        // Room 1 (room-secret): Bob, Charlie (Alice không thuộc phòng này)
        // Room 2 (room-dst): Alice, Charlie
        messageDao.addConversationMember("room-secret", "Bob");
        messageDao.addConversationMember("room-secret", "Charlie");

        messageDao.addConversationMember("room-dst", "Alice");
        messageDao.addConversationMember("room-dst", "Charlie");

        ProtocolMessage secretMsg = new ProtocolMessage(MessageType.CHAT);
        secretMsg.messageId = "msg-secret-1";
        secretMsg.sender = "Bob";
        secretMsg.convId = "room-secret";
        secretMsg.content = "Bí mật phòng tin";
        messageDao.save(secretMsg);

        // Alice cố gắng forward tin secretMsg
        ProtocolMessage fwdReq = new ProtocolMessage(MessageType.CHAT_FORWARD);
        fwdReq.messageId = "fwd-req-003";
        fwdReq.sender = "Alice";
        fwdReq.convId = "room-dst";
        fwdReq.fwdFrom = "msg-secret-1";
        fwdReq.content = "Cố gắng forward tin mật";

        handler.handleForwardMessage(aliceConnection.session, fwdReq);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN_READ_SOURCE", err.errorCode);
        assertEquals("Không có quyền đọc tin nguồn", err.errorMessage);
    }

    @Test
    public void testForwardVaoHoiThoaiKhongCoQuyenTraLoi() throws Exception {
        // Room 1 (room-src): Alice, Bob
        // Room 2 (room-private): Bob, Charlie (Alice không thuộc phòng này)
        messageDao.addConversationMember("room-src", "Alice");
        messageDao.addConversationMember("room-src", "Bob");

        messageDao.addConversationMember("room-private", "Bob");
        messageDao.addConversationMember("room-private", "Charlie");

        ProtocolMessage srcMsg = new ProtocolMessage(MessageType.CHAT);
        srcMsg.messageId = "msg-src-2";
        srcMsg.sender = "Bob";
        srcMsg.convId = "room-src";
        srcMsg.content = "Tin nhắn phòng 1";
        messageDao.save(srcMsg);

        // Alice cố gắng forward vào room-private mà Alice không có quyền
        ProtocolMessage fwdReq = new ProtocolMessage(MessageType.CHAT_FORWARD);
        fwdReq.messageId = "fwd-req-004";
        fwdReq.sender = "Alice";
        fwdReq.convId = "room-private";
        fwdReq.fwdFrom = "msg-src-2";
        fwdReq.content = "Forward vào room không có quyền";

        handler.handleForwardMessage(aliceConnection.session, fwdReq);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN_SEND_TARGET", err.errorCode);
        assertEquals("Không có quyền gửi vào hội thoại này", err.errorMessage);
    }

    private static class TestConnection implements AutoCloseable {
        private Socket clientSocket;
        private Socket serverSocket;
        private ClientSession session;
        private BufferedReader reader;
        private PrintWriter writer;

        private TestConnection(String username, String avatarId) throws IOException {
            InetAddress address = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, address)) {
                clientSocket = new Socket(address, listener.getLocalPort());
                serverSocket = listener.accept();
            }
            clientSocket.setSoTimeout(500);
            session = ClientSession.createAnonymous(serverSocket);
            session.authenticate(username, avatarId);

            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        private ProtocolMessage readMessage() throws IOException {
            String json = reader.readLine();
            if (json == null) return null;
            return JsonUtil.fromJson(json);
        }

        @Override
        public void close() throws IOException {
            session.close();
            clientSocket.close();
        }
    }
}
