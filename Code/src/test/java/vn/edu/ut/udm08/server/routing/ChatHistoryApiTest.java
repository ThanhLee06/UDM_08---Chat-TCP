package vn.edu.ut.udm08.server.routing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryMessageDao;
import vn.edu.ut.udm08.server.room.MessageDao;
import vn.edu.ut.udm08.server.room.Transaction;
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

public class ChatHistoryApiTest {

    private OnlineUserRegistry registry;
    private MessageDao messageDao;
    private ConversationDao conversationDao;
    private Transaction transaction;
    private MessageRouter router;

    private TestConnection aliceConnection;
    private TestConnection bobConnection;
    private TestConnection charlieConnection;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        messageDao = new InMemoryMessageDao();
        conversationDao = new InMemoryConversationDao();
        transaction = new Transaction(messageDao, conversationDao);
        router = new MessageRouter(registry, messageDao, conversationDao, transaction);

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
    public void testLayLichSuTrangDauVaPhanTrangCursor() throws Exception {
        String convId = "conv-history-1";
        conversationDao.addMember(convId, "Alice");
        conversationDao.addMember(convId, "Bob");

        // Tạo 5 tin nhắn vào DB từ seq 1 đến seq 5
        for (int i = 1; i <= 5; i++) {
            ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
            msg.messageId = "msg-h-" + i;
            msg.sender = "Alice";
            msg.target = "Bob";
            msg.convId = convId;
            msg.content = "Nội dung tin nhắn " + i;
            transaction.saveMessage(msg);
        }

        // Yêu cầu lấy trang 1 (cursor = null, limit = 3)
        ProtocolMessage req1 = new ProtocolMessage(MessageType.GET_HISTORY);
        req1.requestId = "req-101";
        req1.convId = convId;
        req1.cursor = null;
        req1.limit = 3;

        router.handleGetHistoryMessage(aliceConnection.session, req1);

        ProtocolMessage resp1 = aliceConnection.readMessage();
        assertNotNull(resp1);
        assertEquals(MessageType.GET_HISTORY_OK, resp1.type);
        assertEquals("req-101", resp1.requestId);
        assertEquals(3, resp1.history.size());
        assertTrue(resp1.hasMore);

        // Tin nhắn trả về phải được sắp xếp từ cũ đến mới (msg-h-3, msg-h-4, msg-h-5)
        assertEquals("msg-h-3", resp1.history.get(0).messageId);
        assertEquals("msg-h-4", resp1.history.get(1).messageId);
        assertEquals("msg-h-5", resp1.history.get(2).messageId);

        // nextCursor là sequence của tin cũ nhất trong trang (msg-h-3)
        String nextCursor = resp1.nextCursor;
        assertNotNull(nextCursor);

        // Yêu cầu lấy trang 2 dùng cursor từ trang 1
        ProtocolMessage req2 = new ProtocolMessage(MessageType.GET_HISTORY);
        req2.requestId = "req-102";
        req2.convId = convId;
        req2.cursor = nextCursor;
        req2.limit = 3;

        router.handleGetHistoryMessage(aliceConnection.session, req2);

        ProtocolMessage resp2 = aliceConnection.readMessage();
        assertNotNull(resp2);
        assertEquals(MessageType.GET_HISTORY_OK, resp2.type);
        assertEquals(2, resp2.history.size()); // Còn 2 tin nhắn (msg-h-1, msg-h-2)
        assertFalse(resp2.hasMore); // Đã hết tin nhắn cũ hơn
        assertEquals("msg-h-1", resp2.history.get(0).messageId);
        assertEquals("msg-h-2", resp2.history.get(1).messageId);
    }

    @Test
    public void testPhanQuyenForbiddenKhiKhongPhaiThanhVien() throws Exception {
        String convId = "conv-private-99";
        conversationDao.addMember(convId, "Alice");
        conversationDao.addMember(convId, "Bob");

        // Charlie (người lạ) gửi yêu cầu đọc lịch sử conv-private-99
        ProtocolMessage req = new ProtocolMessage(MessageType.GET_HISTORY);
        req.requestId = "req-spy-001";
        req.convId = convId;

        router.handleGetHistoryMessage(charlieConnection.session, req);

        ProtocolMessage err = charlieConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN", err.errorCode);
    }

    @Test
    public void testHoiThoaiChuaCoTinNhan() throws Exception {
        String convId = "conv-empty-000";
        conversationDao.addMember(convId, "Alice");

        ProtocolMessage req = new ProtocolMessage(MessageType.GET_HISTORY);
        req.requestId = "req-empty-001";
        req.convId = convId;

        router.handleGetHistoryMessage(aliceConnection.session, req);

        ProtocolMessage resp = aliceConnection.readMessage();
        assertNotNull(resp);
        assertEquals(MessageType.GET_HISTORY_OK, resp.type);
        assertTrue(resp.history.isEmpty());
        assertFalse(resp.hasMore);
        assertNull(resp.nextCursor);
    }

    @Test
    public void testCursorKhongHopLeReturnsBadRequest() throws Exception {
        String convId = "conv-bad-cursor";
        conversationDao.addMember(convId, "Alice");

        // Lưu 1 tin nhắn
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-1";
        msg.sender = "Alice";
        msg.target = "Alice";
        msg.convId = convId;
        msg.content = "Test";
        transaction.saveMessage(msg);

        // Truyền cursor không tồn tại
        ProtocolMessage req = new ProtocolMessage(MessageType.GET_HISTORY);
        req.requestId = "req-invalid-cursor";
        req.convId = convId;
        req.cursor = "invalid_cursor_99999";

        router.handleGetHistoryMessage(aliceConnection.session, req);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("BAD_REQUEST", err.errorCode);
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
            clientSocket.setSoTimeout(300);
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
