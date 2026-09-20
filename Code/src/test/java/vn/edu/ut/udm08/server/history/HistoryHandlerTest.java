package vn.edu.ut.udm08.server.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryHandlerTest {

    private ConversationDao conversationDao;
    private HistoryHandler historyHandler;

    private TestConnection aliceConn;
    private TestConnection charlieConn;

    @BeforeEach
    void setUp() throws Exception {
        conversationDao = new InMemoryConversationDao();
        historyHandler = new HistoryHandler(conversationDao);

        aliceConn = new TestConnection("alice", "avatar-alice");
        charlieConn = new TestConnection("charlie", "avatar-charlie");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (aliceConn != null) aliceConn.close();
        if (charlieConn != null) charlieConn.close();
    }

    @Test
    void testHistoryPagingSuccess() throws Exception {
        String convId = "conv-work";
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "bob");

        // Tạo 35 tin nhắn từ sequence 1 đến 35
        for (int i = 1; i <= 35; i++) {
            ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
            msg.messageId = "msg-" + i;
            msg.convId = convId;
            msg.sender = (i % 2 == 1) ? "alice" : "bob";
            msg.content = "Noi dung tin " + i;
            msg.sequence = (long) i;
            msg.timestamp = 10000L + i;
            conversationDao.addMessage(convId, msg);
        }

        // Trang 1: cursor = null, limit = 30
        ProtocolMessage reqPage1 = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        reqPage1.requestId = "req-page-1";
        reqPage1.convId = convId;
        reqPage1.limit = 30;

        historyHandler.handleHistoryRequest(aliceConn.session, reqPage1);

        ProtocolMessage resPage1 = aliceConn.readMessage();
        assertNotNull(resPage1);
        assertEquals(MessageType.HISTORY_RESPONSE, resPage1.type);
        assertEquals("req-page-1", resPage1.requestId);
        assertEquals(convId, resPage1.convId);
        assertNotNull(resPage1.messages);
        assertEquals(30, resPage1.messages.size());
        assertTrue(resPage1.hasMore);
        assertEquals("6", resPage1.nextCursor);

        // Tin nhắn xếp từ cũ đến mới (sequence 6 -> 35)
        assertEquals(6L, resPage1.messages.get(0).sequence);
        assertEquals(35L, resPage1.messages.get(29).sequence);

        // Trang 2: cursor = "6", limit = 30
        ProtocolMessage reqPage2 = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        reqPage2.requestId = "req-page-2";
        reqPage2.convId = convId;
        reqPage2.cursor = resPage1.nextCursor;
        reqPage2.limit = 30;

        historyHandler.handleHistoryRequest(aliceConn.session, reqPage2);

        ProtocolMessage resPage2 = aliceConn.readMessage();
        assertNotNull(resPage2);
        assertEquals(MessageType.HISTORY_RESPONSE, resPage2.type);
        assertEquals("req-page-2", resPage2.requestId);
        assertEquals(5, resPage2.messages.size());
        assertFalse(resPage2.hasMore);
        assertEquals("1", resPage2.nextCursor);

        // Tin nhắn trang 2: sequence 1 -> 5
        assertEquals(1L, resPage2.messages.get(0).sequence);
        assertEquals(5L, resPage2.messages.get(4).sequence);
    }

    @Test
    void testHistoryForbiddenForNonMember() throws Exception {
        String convId = "conv-private";
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "bob");

        // Charlie không phải thành viên gửi yêu cầu đọc trộm lịch sử
        ProtocolMessage req = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req.requestId = "req-snoop";
        req.convId = convId;

        historyHandler.handleHistoryRequest(charlieConn.session, req);

        ProtocolMessage error = charlieConn.readMessage();
        assertNotNull(error);
        assertEquals(MessageType.ERROR, error.type);
        assertEquals("FORBIDDEN", error.errorCode);
        assertEquals("req-snoop", error.requestId);
    }

    @Test
    void testEmptyConversationReturnsEmptyList() throws Exception {
        String convId = "conv-empty";
        conversationDao.addMember(convId, "alice");

        ProtocolMessage req = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req.requestId = "req-empty";
        req.convId = convId;

        historyHandler.handleHistoryRequest(aliceConn.session, req);

        ProtocolMessage res = aliceConn.readMessage();
        assertNotNull(res);
        assertEquals(MessageType.HISTORY_RESPONSE, res.type);
        assertEquals("req-empty", res.requestId);
        assertNotNull(res.messages);
        assertTrue(res.messages.isEmpty());
        assertFalse(res.hasMore);
        assertNull(res.nextCursor);
    }

    @Test
    void testInvalidCursorReturnsBadRequest() throws Exception {
        String convId = "conv-valid";
        conversationDao.addMember(convId, "alice");

        ProtocolMessage req = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req.requestId = "req-invalid-cursor";
        req.convId = convId;
        req.cursor = "not-a-number-or-messageId";

        // Thêm 1 tin nhắn để hội thoại không rỗng
        ProtocolMessage dummy = new ProtocolMessage(MessageType.CHAT);
        dummy.messageId = "dummy-1";
        dummy.sequence = 1L;
        conversationDao.addMessage(convId, dummy);

        historyHandler.handleHistoryRequest(aliceConn.session, req);

        ProtocolMessage err = aliceConn.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("BAD_REQUEST", err.errorCode);
        assertEquals("req-invalid-cursor", err.requestId);
    }

    @Test
    void testMissingConvIdReturnsBadRequest() throws Exception {
        ProtocolMessage req = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req.requestId = "req-missing-conv";
        req.convId = "";

        historyHandler.handleHistoryRequest(aliceConn.session, req);

        ProtocolMessage err = aliceConn.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("BAD_REQUEST", err.errorCode);
        assertEquals("req-missing-conv", err.requestId);
    }

    @Test
    void testCursorByMessageIdSupported() throws Exception {
        String convId = "conv-msgid";
        conversationDao.addMember(convId, "alice");

        for (int i = 1; i <= 5; i++) {
            ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
            msg.messageId = "msg-id-" + i;
            msg.convId = convId;
            msg.sequence = (long) i;
            msg.content = "Tin " + i;
            conversationDao.addMessage(convId, msg);
        }

        // Dùng messageId của tin 4 làm cursor -> lấy tin cũ hơn tin 4 (tức 1, 2, 3)
        ProtocolMessage req = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req.requestId = "req-cursor-msgid";
        req.convId = convId;
        req.cursor = "msg-id-4";

        historyHandler.handleHistoryRequest(aliceConn.session, req);

        ProtocolMessage res = aliceConn.readMessage();
        assertNotNull(res);
        assertEquals(MessageType.HISTORY_RESPONSE, res.type);
        assertEquals(3, res.messages.size());
        assertEquals("msg-id-1", res.messages.get(0).messageId);
        assertEquals("msg-id-3", res.messages.get(2).messageId);
    }

    private static class TestConnection implements AutoCloseable {
        private Socket clientSocket;
        private Socket serverSocket;
        private ClientSession session;
        private BufferedReader reader;

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
