package vn.edu.ut.udm08.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.conversation.ConversationListHandler;
import vn.edu.ut.udm08.server.history.HistoryHandler;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryMessageDao;
import vn.edu.ut.udm08.server.room.MessageDao;
import vn.edu.ut.udm08.server.room.Transaction;
import vn.edu.ut.udm08.server.routing.MessageRouter;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử toàn diện 3 chức năng đã tích hợp:
 * 1. Lưu tin vào DB trước khi phát realtime và gửi ACK (ST-112 #160)
 * 2. API lịch sử tin nhắn có phân trang cursor và kiểm tra quyền (ST-113 #161)
 * 3. API danh sách hội thoại Inbox cho Sidebar Client (ST-114 #162)
 */
public class ThreeFeaturesCombinedIntegrationTest {

    private OnlineUserRegistry registry;
    private InMemoryConversationDao conversationDao;
    private MessageDao messageDao;
    private Transaction transaction;
    private MessageRouter router;
    private HistoryHandler historyHandler;
    private ConversationListHandler conversationListHandler;

    private TestClientConnection alice;
    private TestClientConnection bob;
    private TestClientConnection charlie;

    @BeforeEach
    void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        conversationDao = new InMemoryConversationDao();
        messageDao = new InMemoryMessageDao();
        transaction = new Transaction(messageDao);
        router = new MessageRouter(registry, messageDao, conversationDao, transaction);
        historyHandler = new HistoryHandler(conversationDao);
        conversationListHandler = new ConversationListHandler(conversationDao);

        alice = new TestClientConnection("Alice", "avatar_alice");
        bob = new TestClientConnection("Bob", "avatar_bob");
        charlie = new TestClientConnection("Charlie", "avatar_charlie");

        registry.register(alice.session);
        registry.register(bob.session);
        registry.register(charlie.session);

        conversationDao.setUserAvatar("Alice", "avatar_alice");
        conversationDao.setUserAvatar("Bob", "avatar_bob");
        conversationDao.setUserAvatar("Charlie", "avatar_charlie");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (alice != null) alice.close();
        if (bob != null) bob.close();
        if (charlie != null) charlie.close();
    }

    @Test
    @DisplayName("Chức năng 1: Lưu DB trước khi bắn tin Realtime, cấp sequence, gửi ACK và chặn người ngoài")
    void testFeature1_MessageSendingAndDbPersistence() throws Exception {
        String convId = "conv-dm-alice-bob";
        conversationDao.createConversation(convId, "DM", "Alice & Bob", "avatar_dm");
        conversationDao.addMember(convId, "Alice");
        conversationDao.addMember(convId, "Bob");

        // 1. Alice gửi tin nhắn
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-001";
        msg.convId = convId;
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.content = "Xin chào Bob, tin này phải lưu DB!";
        msg.timestamp = 9999L; // Giả mạo timestamp từ client

        router.handleChatMessage(alice.session, msg);

        // 2. Bob nhận được tin realtime từ server với sequence và timestamp chính thức từ DB
        ProtocolMessage deliveredToBob = bob.readMessage();
        assertNotNull(deliveredToBob, "Bob phải nhận được tin nhắn realtime");
        assertEquals(MessageType.CHAT, deliveredToBob.type);
        assertEquals("Xin chào Bob, tin này phải lưu DB!", deliveredToBob.content);
        assertEquals("Alice", deliveredToBob.sender);
        assertNotNull(deliveredToBob.sequence, "Sequence phải được cấp từ DB");
        assertTrue(deliveredToBob.sequence > 0);
        assertNotEquals(9999L, deliveredToBob.timestamp, "Timestamp phải là timestamp chính thức từ DB");

        // 3. Alice nhận được MESSAGE_ACK xác nhận đã lưu DB thành công
        ProtocolMessage ackForAlice = alice.readMessage();
        assertNotNull(ackForAlice, "Alice phải nhận được MESSAGE_ACK");
        assertEquals(MessageType.MESSAGE_ACK, ackForAlice.type);
        assertEquals("msg-001", ackForAlice.messageId);
        assertEquals("SENT", ackForAlice.status);
        assertEquals(deliveredToBob.sequence, ackForAlice.sequence);

        // 4. Kiểm tra trong DB: Tin nhắn đã được lưu an toàn
        ProtocolMessage savedInDb = messageDao.findById("msg-001");
        assertNotNull(savedInDb, "Tin nhắn phải tồn tại trong MessageDao");
        assertEquals("Alice", savedInDb.sender);
        assertEquals("conv-dm-alice-bob", savedInDb.convId);

        // 5. Charlie (không phải thành viên) cố tình gửi tin vào conv này -> Bị chặn FORBIDDEN
        ProtocolMessage charlieMsg = new ProtocolMessage(MessageType.CHAT);
        charlieMsg.messageId = "msg-charlie-fake";
        charlieMsg.convId = convId;
        charlieMsg.sender = "Charlie";
        charlieMsg.content = "Tôi muốn hack vào nhóm này";

        router.handleChatMessage(charlie.session, charlieMsg);

        ProtocolMessage charlieErr = charlie.readMessage();
        assertNotNull(charlieErr, "Charlie phải nhận thông báo lỗi");
        assertEquals(MessageType.ERROR, charlieErr.type);
        assertEquals("FORBIDDEN", charlieErr.errorCode);

        // 6. Xử lý rớt mạng: Bob ngắt kết nối (Offline), Alice gửi tin thứ 2
        registry.remove(bob.session);
        bob.session.close();

        ProtocolMessage msg2 = new ProtocolMessage(MessageType.CHAT);
        msg2.messageId = "msg-002";
        msg2.convId = convId;
        msg2.sender = "Alice";
        msg2.target = "Bob";
        msg2.content = "Bob offline rồi nhưng tin vẫn lưu DB";

        router.handleChatMessage(alice.session, msg2);

        // Alice vẫn nhận được ACK thành công
        ProtocolMessage ack2 = alice.readMessage();
        assertNotNull(ack2);
        assertEquals(MessageType.MESSAGE_ACK, ack2.type);
        assertEquals("msg-002", ack2.messageId);

        // Tin nhắn thứ 2 vẫn an toàn trong DB
        assertNotNull(messageDao.findById("msg-002"));
    }

    @Test
    @DisplayName("Chức năng 2: Lấy lịch sử tin nhắn có phân trang cursor và kiểm tra quyền")
    void testFeature2_HistoryApiWithCursorPagination() throws Exception {
        String convId = "conv-room-dev";
        conversationDao.createConversation(convId, "ROOM", "Nhóm Dev", "avatar_room");
        conversationDao.addMember(convId, "Alice");
        conversationDao.addMember(convId, "Bob");

        // Gửi 5 tin nhắn vào hội thoại
        for (int i = 1; i <= 5; i++) {
            ProtocolMessage m = new ProtocolMessage(MessageType.CHAT);
            m.messageId = "history-msg-" + i;
            m.convId = convId;
            boolean isAlice = (i % 2 == 1);
            m.sender = isAlice ? "Alice" : "Bob";
            m.content = "Tin nhắn số " + i;
            ClientSession senderSession = isAlice ? alice.session : bob.session;
            router.handleChatMessage(senderSession, m);

            alice.drainAvailable();
            bob.drainAvailable();
        }

        // Trang 1: Alice xin lịch sử với limit = 2, cursor = null
        ProtocolMessage req1 = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req1.requestId = "req-page-1";
        req1.convId = convId;
        req1.limit = 2;
        req1.cursor = null;

        historyHandler.handleHistoryRequest(alice.session, req1);

        ProtocolMessage resp1 = alice.readMessage();
        assertNotNull(resp1);
        assertEquals(MessageType.HISTORY_RESPONSE, resp1.type);
        assertEquals("req-page-1", resp1.requestId);
        assertNotNull(resp1.messages);
        assertEquals(2, resp1.messages.size());
        assertTrue(resp1.hasMore, "Vẫn còn tin nhắn cũ hơn");
        assertNotNull(resp1.nextCursor);

        String cursorPage2 = resp1.nextCursor;

        // Trang 2: Lấy tiếp trang 2 với cursor từ trang 1
        ProtocolMessage req2 = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req2.requestId = "req-page-2";
        req2.convId = convId;
        req2.limit = 2;
        req2.cursor = cursorPage2;

        historyHandler.handleHistoryRequest(alice.session, req2);

        ProtocolMessage resp2 = alice.readMessage();
        assertNotNull(resp2);
        assertEquals(MessageType.HISTORY_RESPONSE, resp2.type);
        assertEquals(2, resp2.messages.size());
        assertTrue(resp2.hasMore);

        // Trang 3: Lấy tin nhắn còn lại
        ProtocolMessage req3 = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        req3.requestId = "req-page-3";
        req3.convId = convId;
        req3.limit = 2;
        req3.cursor = resp2.nextCursor;

        historyHandler.handleHistoryRequest(alice.session, req3);

        ProtocolMessage resp3 = alice.readMessage();
        assertNotNull(resp3);
        assertEquals(1, resp3.messages.size());
        assertFalse(resp3.hasMore, "Đã hết tin nhắn cũ");

        // Kiểm tra quyền: Charlie (người ngoài) xin lịch sử của conv-room-dev -> Bị từ chối FORBIDDEN
        ProtocolMessage forbiddenReq = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        forbiddenReq.requestId = "req-charlie-forbidden";
        forbiddenReq.convId = convId;

        historyHandler.handleHistoryRequest(charlie.session, forbiddenReq);

        ProtocolMessage forbiddenResp = charlie.readMessage();
        assertNotNull(forbiddenResp);
        assertEquals(MessageType.ERROR, forbiddenResp.type);
        assertEquals("FORBIDDEN", forbiddenResp.errorCode);
    }

    @Test
    @DisplayName("Chức năng 3: Lấy danh sách hội thoại Inbox cho Sidebar Client, hiển thị tên đối phương cho DM và sắp xếp theo hoạt động")
    void testFeature3_ConversationListApi() throws Exception {
        // Tạo 1 cuộc trò chuyện DM giữa Alice và Bob
        String dmConvId = "conv-dm-ab";
        conversationDao.createConversation(dmConvId, "DM", "DM Alice Bob", null);
        conversationDao.addMember(dmConvId, "Alice");
        conversationDao.addMember(dmConvId, "Bob");

        // Tạo 1 cuộc trò chuyện Room giữa Alice, Bob và Charlie
        String roomConvId = "conv-room-all";
        conversationDao.createConversation(roomConvId, "ROOM", "Công Ty ABC", "avatar_company");
        conversationDao.addMember(roomConvId, "Alice");
        conversationDao.addMember(roomConvId, "Bob");
        conversationDao.addMember(roomConvId, "Charlie");

        // Alice gửi 1 tin vào Room
        ProtocolMessage roomMsg = new ProtocolMessage(MessageType.CHAT);
        roomMsg.messageId = "msg-room-1";
        roomMsg.convId = roomConvId;
        roomMsg.sender = "Alice";
        roomMsg.content = "Chào mọi người trong công ty";
        router.handleChatMessage(alice.session, roomMsg);
        alice.drainAvailable();
        bob.drainAvailable();
        charlie.drainAvailable();

        // Đợi 50ms để đảm bảo timestamp của tin nhắn sau lớn hơn hẳn tin nhắn trước
        Thread.sleep(50);

        // Sau đó Bob gửi 1 tin vào DM (mới hơn)
        ProtocolMessage dmMsg = new ProtocolMessage(MessageType.CHAT);
        dmMsg.messageId = "msg-dm-1";
        dmMsg.convId = dmConvId;
        dmMsg.sender = "Bob";
        dmMsg.target = "Alice";
        dmMsg.content = "Alice ơi xem báo cáo chưa?";
        router.handleChatMessage(bob.session, dmMsg);
        alice.drainAvailable();
        bob.drainAvailable();

        // Bob ngắt kết nối (Offline)
        registry.remove(bob.session);
        bob.session.close();

        // Alice gọi CONVERSATION_LIST_REQUEST
        ProtocolMessage inboxReq = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        inboxReq.requestId = "inbox-req-01";

        conversationListHandler.handleConversationListRequest(alice.session, inboxReq);

        ProtocolMessage inboxResp = alice.readMessage();
        assertNotNull(inboxResp);
        assertEquals(MessageType.CONVERSATION_LIST_RESPONSE, inboxResp.type);
        assertEquals("inbox-req-01", inboxResp.requestId);
        assertNotNull(inboxResp.conversations);
        assertEquals(2, inboxResp.conversations.size(), "Phải bao gồm cả DM (dù Bob offline) và Room");

        // Kiểm tra thứ tự sắp xếp: DM có tin nhắn mới hơn nên phải đứng đầu tiên
        ConversationSummary firstConv = inboxResp.conversations.get(0);
        assertEquals(dmConvId, firstConv.convId);
        assertEquals("DM", firstConv.chatType);
        assertEquals("Bob", firstConv.displayName, "Với DM, Alice phải nhìn thấy tên của Bob");
        assertEquals("avatar_bob", firstConv.avatar, "Với DM, Alice phải nhìn thấy avatar của Bob");
        assertEquals("Alice ơi xem báo cáo chưa?", firstConv.lastMessage);

        ConversationSummary secondConv = inboxResp.conversations.get(1);
        assertEquals(roomConvId, secondConv.convId);
        assertEquals("ROOM", secondConv.chatType);
        assertEquals("Công Ty ABC", secondConv.displayName, "Với ROOM, hiển thị tên nhóm");
        assertEquals("avatar_company", secondConv.avatar, "Với ROOM, hiển thị avatar nhóm");
        assertEquals("Chào mọi người trong công ty", secondConv.lastMessage);
    }

    @Test
    @DisplayName("Kịch bản tích hợp xuyên suốt: Gửi tin -> Cập nhật Inbox -> Đọc lại lịch sử phân trang")
    void testEndToEndWorkflow_AllThreeFeaturesCombined() throws Exception {
        String convId = "conv-e2e-project";
        conversationDao.createConversation(convId, "DM", "Dự Án Lớn", null);
        conversationDao.addMember(convId, "Alice");
        conversationDao.addMember(convId, "Bob");

        // Bước 1: Alice gửi tin nhắn đầu tiên (Chức năng 1)
        ProtocolMessage msg1 = new ProtocolMessage(MessageType.CHAT);
        msg1.messageId = "e2e-m1";
        msg1.convId = convId;
        msg1.sender = "Alice";
        msg1.target = "Bob";
        msg1.content = "Bắt đầu dự án nhé!";

        router.handleChatMessage(alice.session, msg1);

        ProtocolMessage bobRecv1 = bob.readMessage();
        assertNotNull(bobRecv1);
        assertEquals("Bắt đầu dự án nhé!", bobRecv1.content);

        ProtocolMessage aliceAck1 = alice.readMessage();
        assertNotNull(aliceAck1);
        assertEquals(MessageType.MESSAGE_ACK, aliceAck1.type);

        // Bước 2: Alice kiểm tra Sidebar Inbox (Chức năng 3)
        ProtocolMessage inboxReq1 = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        inboxReq1.requestId = "e2e-inbox-1";
        conversationListHandler.handleConversationListRequest(alice.session, inboxReq1);

        ProtocolMessage inboxResp1 = alice.readMessage();
        assertNotNull(inboxResp1);
        assertEquals(1, inboxResp1.conversations.size());
        assertEquals("Bob", inboxResp1.conversations.get(0).displayName);
        assertEquals("Bắt đầu dự án nhé!", inboxResp1.conversations.get(0).lastMessage);

        // Bước 3: Bob trả lời lại (Chức năng 1)
        ProtocolMessage msg2 = new ProtocolMessage(MessageType.CHAT);
        msg2.messageId = "e2e-m2";
        msg2.convId = convId;
        msg2.sender = "Bob";
        msg2.target = "Alice";
        msg2.content = "Nhất trí, bắt tay vào làm luôn!";

        router.handleChatMessage(bob.session, msg2);

        ProtocolMessage aliceRecv2 = alice.readMessage();
        assertNotNull(aliceRecv2);
        assertEquals("Nhất trí, bắt tay vào làm luôn!", aliceRecv2.content);

        ProtocolMessage bobAck2 = bob.readMessage();
        assertNotNull(bobAck2);
        assertEquals(MessageType.MESSAGE_ACK, bobAck2.type);

        // Bước 4: Alice mở lại Inbox, lastMessage cập nhật theo tin nhắn mới nhất của Bob (Chức năng 3)
        ProtocolMessage inboxReq2 = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        inboxReq2.requestId = "e2e-inbox-2";
        conversationListHandler.handleConversationListRequest(alice.session, inboxReq2);

        ProtocolMessage inboxResp2 = alice.readMessage();
        assertNotNull(inboxResp2);
        assertEquals("Nhất trí, bắt tay vào làm luôn!", inboxResp2.conversations.get(0).lastMessage);

        // Bước 5: Bob tải lại lịch sử tin nhắn của cuộc trò chuyện (Chức năng 2)
        ProtocolMessage histReq = new ProtocolMessage(MessageType.HISTORY_REQUEST);
        histReq.requestId = "e2e-hist-1";
        histReq.convId = convId;
        histReq.limit = 10;

        historyHandler.handleHistoryRequest(bob.session, histReq);

        ProtocolMessage histResp = bob.readMessage();
        assertNotNull(histResp);
        assertEquals(MessageType.HISTORY_RESPONSE, histResp.type);
        assertEquals(2, histResp.messages.size());
        assertEquals("e2e-m1", histResp.messages.get(0).messageId);
        assertEquals("e2e-m2", histResp.messages.get(1).messageId);
        assertFalse(histResp.hasMore);
    }

    private static class TestClientConnection implements AutoCloseable {
        private final Socket clientSocket;
        private final Socket serverSocket;
        private final ClientSession session;
        private final BufferedReader reader;
        private final PrintWriter writer;

        private TestClientConnection(String username, String avatarId) throws IOException {
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

        private void drainAvailable() {
            try {
                while (reader.ready()) {
                    reader.readLine();
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() throws IOException {
            session.close();
            clientSocket.close();
        }
    }
}
