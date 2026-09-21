package vn.edu.ut.udm08.server.routing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.conversation.ConversationListHandler;
import vn.edu.ut.udm08.server.history.HistoryHandler;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryMessageDao;
import vn.edu.ut.udm08.server.room.Transaction;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite kiem thu gui tin Offline va khoi phuc hoi thoai qua lich su va inbox (ST-117 / Issue #165).
 */
public class OfflineMessagingAndRecoveryTest {

    private OnlineUserRegistry registry;
    private InMemoryConversationDao conversationDao;
    private InMemoryMessageDao messageDao;
    private Transaction transaction;
    private MessageRouter router;
    private HistoryHandler historyHandler;
    private ConversationListHandler conversationListHandler;

    private TestConnection aliceConnection;
    private TestConnection bobConnection;
    private TestConnection charlieConnection;

    @BeforeEach
    void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        conversationDao = new InMemoryConversationDao();
        messageDao = new InMemoryMessageDao();
        transaction = new Transaction(messageDao);
        router = new MessageRouter(registry, messageDao, conversationDao, transaction);
        historyHandler = new HistoryHandler(conversationDao);
        conversationListHandler = new ConversationListHandler(conversationDao);

        aliceConnection = new TestConnection("alice", "avatar_alice");
        registry.register(aliceConnection.session);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (aliceConnection != null) aliceConnection.close();
        if (bobConnection != null) bobConnection.close();
        if (charlieConnection != null) charlieConnection.close();
    }

    @Test
    @DisplayName("Test luồng Offline: User B Offline -> User A gửi 5 tin nhắn -> DB lưu đủ 5 tin -> Restart Server -> User B Online thấy đủ lịch sử và sidebar cập nhật")
    void testOfflineMessagingAndServerRestartRecovery() throws Exception {
        String convId = "conv-dm-alice-bob";
        conversationDao.createConversation(convId, "DM", "DM Alice Bob");
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "bob");

        // User B hoàn toàn Offline (chưa kết nối socket nào)

        // 1. User A gửi 5 tin nhắn liên tiếp
        for (int i = 1; i <= 5; i++) {
            ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
            msg.messageId = "msg-offline-" + i;
            msg.convId = convId;
            msg.sender = "alice";
            msg.target = "bob";
            msg.content = "Tin nhan offline so " + i;

            router.handleChatMessage(aliceConnection.session, msg);

            // User A nhận được MESSAGE_ACK xác nhận tin đã lưu DB thành công
            ProtocolMessage ack = aliceConnection.readMessage();
            assertNotNull(ack, "Alice phai nhan duoc MESSAGE_ACK cho tin " + i);
            assertEquals(MessageType.MESSAGE_ACK, ack.type);
            assertEquals("msg-offline-" + i, ack.messageId);
            assertEquals("SENT", ack.status);
            assertNotNull(ack.sequence);
            assertNotNull(ack.timestamp);
        }

        // 2. Kiểm tra trong DB: Cả 5 tin đều được lưu trữ đầy đủ
        for (int i = 1; i <= 5; i++) {
            ProtocolMessage inDb = messageDao.findById("msg-offline-" + i);
            assertNotNull(inDb, "Tin nhan thu " + i + " phai ton tai trong MessageDao DB");
            assertEquals("Tin nhan offline so " + i, inDb.content);
            assertEquals("alice", inDb.sender);
        }

        // 3. Mô phỏng Server Restart: Tạo lại các handler với DB dữ liệu cũ
        OnlineUserRegistry freshRegistry = new OnlineUserRegistry();
        Transaction freshTransaction = new Transaction(messageDao);
        MessageRouter freshRouter = new MessageRouter(freshRegistry, messageDao, conversationDao, freshTransaction);
        HistoryHandler freshHistoryHandler = new HistoryHandler(conversationDao);
        ConversationListHandler freshConversationListHandler = new ConversationListHandler(conversationDao);

        // 4. User B Đăng nhập vào Server sau khi restart
        bobConnection = new TestConnection("bob", "avatar_bob");
        freshRegistry.register(bobConnection.session);

        // 5. User B lấy danh sách hội thoại Inbox cho Sidebar:
        // Hội thoại conv-dm-alice-bob phải nằm trên cùng với lastMessage là tin số 5
        ProtocolMessage inboxReq = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        inboxReq.requestId = "req-inbox-bob-1";
        freshConversationListHandler.handleConversationListRequest(bobConnection.session, inboxReq);

        ProtocolMessage inboxResp = bobConnection.readMessage();
        assertNotNull(inboxResp);
        assertEquals(MessageType.CONVERSATION_LIST_RESPONSE, inboxResp.type);
        assertNotNull(inboxResp.conversations);
        assertFalse(inboxResp.conversations.isEmpty());

        ConversationSummary topConv = inboxResp.conversations.get(0);
        assertEquals(convId, topConv.convId);
        assertEquals("Tin nhan offline so 5", topConv.lastMessage, "Sidebar phai hien thi noi dung tin nhan moi nhat");

        // 6. User B mở chat và tải lịch sử tin nhắn qua HISTORY_REQUEST
        ProtocolMessage histReq = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        histReq.requestId = "req-history-bob-1";
        histReq.convId = convId;
        histReq.limit = 10;

        freshHistoryHandler.handleHistoryRequest(bobConnection.session, histReq);

        ProtocolMessage histResp = bobConnection.readMessage();
        assertNotNull(histResp);
        assertEquals(MessageType.HISTORY_RESPONSE, histResp.type);
        List<ProtocolMessage> historyMessages = histResp.messages;
        assertNotNull(historyMessages);
        assertEquals(5, historyMessages.size(), "User B phai nhan du 5 tin nhan da gui trong luc offline");

        // Kiểm tra đúng thứ tự thời gian từ cũ đến mới (tin 1 -> tin 5)
        for (int i = 0; i < 5; i++) {
            assertEquals("msg-offline-" + (i + 1), historyMessages.get(i).messageId);
            assertEquals("Tin nhan offline so " + (i + 1), historyMessages.get(i).content);
        }
    }

    @Test
    @DisplayName("Test nhiều người: User A gửi tin cho User B (Offline) và User C (Online) trong cùng 1 Room")
    void testGroupRoomWithOnlineAndOfflineUsers() throws Exception {
        String roomId = "room-group-alpha";
        conversationDao.createConversation(roomId, "ROOM", "Nhom Alpha");
        conversationDao.addMember(roomId, "alice");
        conversationDao.addMember(roomId, "bob");
        conversationDao.addMember(roomId, "charlie");

        // Charlie Online
        charlieConnection = new TestConnection("charlie", "avatar_charlie");
        registry.register(charlieConnection.session);

        // Bob hoàn toàn Offline

        // 1. Alice gửi tin vào Room
        ProtocolMessage groupMsg = new ProtocolMessage(MessageType.CHAT);
        groupMsg.messageId = "msg-room-alpha-1";
        groupMsg.convId = roomId;
        groupMsg.sender = "alice";
        groupMsg.content = "Thong bao hop team khan cap luc 9h";

        router.handleChatMessage(aliceConnection.session, groupMsg);

        // 2. Alice nhận được MESSAGE_ACK xác nhận
        ProtocolMessage aliceAck = aliceConnection.readMessage();
        assertNotNull(aliceAck);
        assertEquals(MessageType.MESSAGE_ACK, aliceAck.type);
        assertEquals("msg-room-alpha-1", aliceAck.messageId);
        assertEquals("SENT", aliceAck.status);

        // 3. Charlie (Online) nhận được tin nhắn Realtime ngay lập tức
        ProtocolMessage charlieRecv = charlieConnection.readMessage();
        assertNotNull(charlieRecv, "Charlie dang online phai nhan duoc tin realtime");
        assertEquals("msg-room-alpha-1", charlieRecv.messageId);
        assertEquals("alice", charlieRecv.sender);
        assertEquals("Thong bao hop team khan cap luc 9h", charlieRecv.content);

        // 4. Tin nhắn được lưu DB an toàn
        ProtocolMessage inDb = messageDao.findById("msg-room-alpha-1");
        assertNotNull(inDb);
        assertEquals("Thong bao hop team khan cap luc 9h", inDb.content);

        // 5. Sau đó Bob đăng nhập vào Server
        bobConnection = new TestConnection("bob", "avatar_bob");
        registry.register(bobConnection.session);

        // 6. Bob yêu cầu lịch sử chat của room-group-alpha
        ProtocolMessage bobHistReq = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        bobHistReq.requestId = "req-bob-room-hist";
        bobHistReq.convId = roomId;
        bobHistReq.limit = 10;

        historyHandler.handleHistoryRequest(bobConnection.session, bobHistReq);

        ProtocolMessage bobHistResp = bobConnection.readMessage();
        assertNotNull(bobHistResp);
        assertEquals(MessageType.HISTORY_RESPONSE, bobHistResp.type);
        assertNotNull(bobHistResp.messages);
        assertEquals(1, bobHistResp.messages.size());
        assertEquals("msg-room-alpha-1", bobHistResp.messages.get(0).messageId);
        assertEquals("Thong bao hop team khan cap luc 9h", bobHistResp.messages.get(0).content);
    }

    @Test
    @DisplayName("Test Reply và Forward khi người nhận đang Offline")
    void testReplyAndForwardWhenRecipientIsOffline() throws Exception {
        String convId = "conv-dm-work";
        conversationDao.createConversation(convId, "DM", "Work DM");
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "bob");

        // Tin nhắn gốc đã có sẵn từ hôm qua
        ProtocolMessage oldMsg = new ProtocolMessage(MessageType.CHAT);
        oldMsg.messageId = "old-work-msg";
        oldMsg.convId = convId;
        oldMsg.sender = "bob";
        oldMsg.content = "Ke hoach tuan nay la gi?";
        oldMsg.timestamp = System.currentTimeMillis() - 86400000L;
        oldMsg.sequence = 1L;
        messageDao.save(oldMsg);
        conversationDao.addMessage(convId, oldMsg);

        // Bob đang Offline, Alice Reply tin nhắn này
        ProtocolMessage replyMsg = new ProtocolMessage(MessageType.REPLY);
        replyMsg.messageId = "reply-work-1";
        replyMsg.convId = convId;
        replyMsg.sender = "alice";
        replyMsg.content = "Ke hoach da duoc cap nhat tren Jira";
        replyMsg.replyToMessageId = "old-work-msg";

        router.handleChatMessage(aliceConnection.session, replyMsg);

        // Alice nhận ACK
        ProtocolMessage ack = aliceConnection.readMessage();
        assertNotNull(ack);
        assertEquals(MessageType.MESSAGE_ACK, ack.type);
        assertEquals("reply-work-1", ack.messageId);

        // Bob đăng nhập lại và đọc lịch sử
        bobConnection = new TestConnection("bob", "avatar_bob");
        registry.register(bobConnection.session);

        ProtocolMessage histReq = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        histReq.requestId = "bob-req-reply-hist";
        histReq.convId = convId;
        histReq.limit = 10;

        historyHandler.handleHistoryRequest(bobConnection.session, histReq);

        ProtocolMessage histResp = bobConnection.readMessage();
        assertNotNull(histResp);
        assertEquals(MessageType.HISTORY_RESPONSE, histResp.type);
        assertEquals(2, histResp.messages.size());

        ProtocolMessage replyInHist = histResp.messages.get(1);
        assertEquals("reply-work-1", replyInHist.messageId);
        assertEquals("reply", replyInHist.kind);
        assertEquals("old-work-msg", replyInHist.replyToMessageId);
        assertEquals("bob", replyInHist.replyToSender);
        assertEquals("Ke hoach tuan nay la gi?", replyInHist.replyToContent);
    }

    private static class TestConnection implements AutoCloseable {
        private final Socket clientSocket;
        private final Socket serverSocket;
        private final ClientSession session;
        private final BufferedReader reader;
        private final PrintWriter writer;

        private TestConnection(String username, String avatarId) throws IOException {
            InetAddress address = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, address)) {
                clientSocket = new Socket(address, listener.getLocalPort());
                serverSocket = listener.accept();
            }
            clientSocket.setSoTimeout(1000);
            session = ClientSession.createAnonymous(serverSocket);
            session.authenticate(username, avatarId);

            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        private ProtocolMessage readMessage() throws IOException {
            String line = reader.readLine();
            if (line == null) return null;
            return JsonUtil.fromJson(line);
        }

        @Override
        public void close() throws IOException {
            session.close();
            clientSocket.close();
        }
    }
}
