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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MessageDbSaveRoutingTest {

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
        transaction = new Transaction(messageDao);
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
    public void testLuuDbTruocKhiBanTinVaPhanHoiAck() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-db-001";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.convId = "conv-100";
        msg.content = "Tin nhắn cần lưu DB trước!";
        msg.timestamp = 1000L; // Client timestamp (sẽ bị thay thế bởi DB)

        // Đăng ký thành viên cuộc trò chuyện conv-100
        conversationDao.addMember("conv-100", "Alice");
        conversationDao.addMember("conv-100", "Bob");

        router.handleChatMessage(aliceConnection.session, msg);

        // 1. Kiểm tra tin nhắn đã được lưu vào DB trước
        ProtocolMessage dbMsg = messageDao.findById("msg-db-001");
        assertNotNull(dbMsg, "Tin nhắn phải được lưu trong DB");
        assertNotNull(dbMsg.timestamp, "Timestamp từ DB không được null");
        assertNotEquals(1000L, dbMsg.timestamp, "Timestamp phải là timestamp chính thức từ DB, không dùng của Client");
        assertNotNull(dbMsg.sequence, "Sequence ID từ DB phải được cấp");

        // 2. Người nhận Bob nhận tin nhắn Realtime có thông tin từ DB
        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("Alice", receivedByBob.sender);
        assertEquals(dbMsg.timestamp, receivedByBob.timestamp);
        assertEquals(dbMsg.sequence, receivedByBob.sequence);

        // 3. Người gửi Alice nhận MESSAGE_ACK kèm status SENT và timestamp chính thức từ DB
        ProtocolMessage ackToAlice = aliceConnection.readMessage();
        assertNotNull(ackToAlice);
        assertEquals(MessageType.MESSAGE_ACK, ackToAlice.type);
        assertEquals("msg-db-001", ackToAlice.messageId);
        assertEquals("SENT", ackToAlice.status);
        assertEquals(dbMsg.timestamp, ackToAlice.timestamp);
        assertEquals(dbMsg.sequence, ackToAlice.sequence);
    }

    @Test
    public void testPhanQuyenNotMemberTuChoiGuiTin() throws Exception {
        // Đăng ký cuộc trò chuyện conv-group chỉ có Bob và Charlie
        conversationDao.addMember("conv-group", "Bob");
        conversationDao.addMember("conv-group", "Charlie");

        // Alice cố tình gửi tin vào conv-group mặc dù không phải thành viên
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-unauth-002";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.convId = "conv-group";
        msg.content = "Xâm nhập trái phép cuộc trò chuyện!";

        router.handleChatMessage(aliceConnection.session, msg);

        // Alice nhận lỗi FORBIDDEN
        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN", err.errorCode);

        // Tin nhắn KHÔNG được lưu DB
        assertNull(messageDao.findById("msg-unauth-002"));

        // Bob KHÔNG nhận được tin nhắn
        assertThrows(SocketTimeoutException.class, () -> bobConnection.readMessage());
    }

    @Test
    public void testXuLyRotMangNguoiNhanSilentHandling() throws Exception {
        conversationDao.addMember("conv-200", "Alice");
        conversationDao.addMember("conv-200", "Bob");

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-drop-003";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.convId = "conv-200";
        msg.content = "Gửi tin khi người nhận sắp rớt mạng";

        // Đóng kết nối của Bob ngay trước khi broadcast
        bobConnection.clientSocket.close();

        // Router xử lý không tung ngoại lệ
        assertDoesNotThrow(() -> router.handleChatMessage(aliceConnection.session, msg));

        // Tin nhắn vẫn nằm an toàn trong DB
        ProtocolMessage dbMsg = messageDao.findById("msg-drop-003");
        assertNotNull(dbMsg, "Tin nhắn vẫn được lưu an toàn trong DB");

        // Alice vẫn nhận được MESSAGE_ACK xác nhận thành công
        ProtocolMessage ackToAlice = aliceConnection.readMessage();
        assertNotNull(ackToAlice);
        assertEquals(MessageType.MESSAGE_ACK, ackToAlice.type);
        assertEquals("msg-drop-003", ackToAlice.messageId);
        assertEquals("SENT", ackToAlice.status);
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
