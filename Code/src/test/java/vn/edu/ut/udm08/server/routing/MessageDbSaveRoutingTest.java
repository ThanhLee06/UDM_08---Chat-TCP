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

/**
 * Kiem thu tich hop luu tin vao DB truoc khi phat tin realtime va tra ve MESSAGE_ACK (ST-112).
 */
public class MessageDbSaveRoutingTest {

    private OnlineUserRegistry registry;
    private ConversationDao conversationDao;
    private MessageDao messageDao;
    private Transaction transaction;
    private MessageRouter router;

    private TestConnection aliceConnection;
    private TestConnection bobConnection;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        conversationDao = new InMemoryConversationDao();
        messageDao = new InMemoryMessageDao();
        transaction = new Transaction(messageDao);
        router = new MessageRouter(registry, messageDao, conversationDao, transaction);

        aliceConnection = new TestConnection("Alice", "avatar_alice");
        bobConnection = new TestConnection("Bob", "avatar_bob");

        registry.register(aliceConnection.session);
        registry.register(bobConnection.session);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (aliceConnection != null) aliceConnection.close();
        if (bobConnection != null) bobConnection.close();
    }

    @Test
    public void testLuuDbTruocKhiBanTinVaAckNguoiGui() throws Exception {
        // Alice va Bob tham gia cuoc tro chuyen conv-100
        conversationDao.addMember("conv-100", "Alice");
        conversationDao.addMember("conv-100", "Bob");

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-db-001";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.convId = "conv-100";
        msg.content = "Xin chao Bob tu DB";
        msg.timestamp = 1000L; // Client truyen len timestamp gia

        router.handleChatMessage(aliceConnection.session, msg);

        // 1. Kiem tra tin nhan da duoc Transaction luu vao DB truoc
        ProtocolMessage dbMsg = messageDao.findById("msg-db-001");
        assertNotNull(dbMsg, "Tin nhan phai duoc luu trong DB");
        assertNotNull(dbMsg.timestamp, "Timestamp tu DB khong duoc null");
        assertNotEquals(1000L, dbMsg.timestamp, "Timestamp phai la timestamp chinh thuc tu DB, khong dung cua Client");
        assertNotNull(dbMsg.sequence, "Sequence ID tu DB phai duoc cap");

        // 2. Nguoi nhan Bob nhan tin nhan Realtime co thong tin tu DB
        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("Alice", receivedByBob.sender);
        assertEquals(dbMsg.timestamp, receivedByBob.timestamp);
        assertEquals(dbMsg.sequence, receivedByBob.sequence);

        // 3. Nguoi gui Alice nhan MESSAGE_ACK kem status SENT va timestamp chinh thuc tu DB
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
        // Dang ky cuoc tro chuyen conv-group chi co Bob va Charlie
        conversationDao.addMember("conv-group", "Bob");
        conversationDao.addMember("conv-group", "Charlie");

        // Alice co tinh gui tin vao conv-group mac du khong phai thanh vien
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-unauth-002";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.convId = "conv-group";
        msg.content = "Xam nhap trai phep cuoc tro chuyen!";

        router.handleChatMessage(aliceConnection.session, msg);

        // Alice nhan loi FORBIDDEN
        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN", err.errorCode);

        // Tin nhan KHONG duoc luu DB
        assertNull(messageDao.findById("msg-unauth-002"));

        // Bob KHONG nhan duoc tin nhan
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
        msg.content = "Gui tin khi nguoi nhan sap rot mang";

        // Dong ket noi cua Bob ngay truoc khi broadcast
        bobConnection.clientSocket.close();

        // Router xu ly khong tung ngoai le
        assertDoesNotThrow(() -> router.handleChatMessage(aliceConnection.session, msg));

        // Tin nhan van nam an toan trong DB
        ProtocolMessage dbMsg = messageDao.findById("msg-drop-003");
        assertNotNull(dbMsg, "Tin nhan van duoc luu an toan trong DB");

        // Alice van nhan duoc MESSAGE_ACK xac nhan thanh cong
        ProtocolMessage ackToAlice = aliceConnection.readMessage();
        assertNotNull(ackToAlice);
        assertEquals(MessageType.MESSAGE_ACK, ackToAlice.type);
        assertEquals("msg-drop-003", ackToAlice.messageId);
        assertEquals("SENT", ackToAlice.status);
    }

    @Test
    public void testXacThucSenderKhongKhopSession() throws Exception {
        conversationDao.addMember("conv-300", "Alice");
        conversationDao.addMember("conv-300", "Bob");

        // Alice co tinh truyen sender la Charlie
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-fake-004";
        msg.sender = "Charlie";
        msg.target = "Bob";
        msg.convId = "conv-300";
        msg.content = "Gia mao Charlie";

        router.handleChatMessage(aliceConnection.session, msg);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("INVALID_SENDER", err.errorCode);

        assertNull(messageDao.findById("msg-fake-004"));
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
