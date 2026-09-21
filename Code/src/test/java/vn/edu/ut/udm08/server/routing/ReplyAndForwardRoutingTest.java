package vn.edu.ut.udm08.server.routing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryMessageDao;
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

/**
 * Test suite for secure Reply and Forward logic querying original messages from server DB (ST-116).
 */
public class ReplyAndForwardRoutingTest {

    private OnlineUserRegistry registry;
    private InMemoryConversationDao conversationDao;
    private InMemoryMessageDao messageDao;
    private Transaction transaction;
    private MessageRouter router;

    private TestConnection aliceConnection;
    private TestConnection bobConnection;
    private TestConnection eveConnection;

    @BeforeEach
    void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        conversationDao = new InMemoryConversationDao();
        messageDao = new InMemoryMessageDao();
        transaction = new Transaction(messageDao);
        router = new MessageRouter(registry, messageDao, conversationDao, transaction);

        aliceConnection = new TestConnection("alice", "avatar_alice");
        bobConnection = new TestConnection("bob", "avatar_bob");
        eveConnection = new TestConnection("eve", "avatar_eve");

        registry.register(aliceConnection.session);
        registry.register(bobConnection.session);
        registry.register(eveConnection.session);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (aliceConnection != null) aliceConnection.close();
        if (bobConnection != null) bobConnection.close();
        if (eveConnection != null) eveConnection.close();
    }

    @Test
    @DisplayName("Reply to old message from DB: Server extracts official metadata and ignores tampered client text")
    void testReplyToOldMessage_OverridesTamperedClientMetadata() throws Exception {
        String convId = "room-work";
        conversationDao.createConversation(convId, "ROOM", "Work Room");
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "bob");

        // 1. Lưu sẵn tin nhắn cũ trong DB từ Bob
        ProtocolMessage oldMessage = new ProtocolMessage(MessageType.CHAT);
        oldMessage.messageId = "msg-old-100";
        oldMessage.convId = convId;
        oldMessage.sender = "bob";
        oldMessage.content = "Noi dung tin nhan goc tu Bob";
        oldMessage.timestamp = System.currentTimeMillis() - 86400000L; // 1 ngày trước
        oldMessage.sequence = 1L;
        messageDao.save(oldMessage);

        // 2. Alice gửi tin nhắn Reply, nhưng Client cố tình giả mạo replyToSender và replyToContent
        ProtocolMessage replyMsg = new ProtocolMessage(MessageType.REPLY);
        replyMsg.messageId = "msg-reply-200";
        replyMsg.convId = convId;
        replyMsg.sender = "alice";
        replyMsg.content = "Dong y voi Bob nhe!";
        replyMsg.replyToMessageId = "msg-old-100";
        replyMsg.replyToSender = "FakeUserHacker"; // Client giả mạo tên người gửi
        replyMsg.replyToContent = "Fake content hacker"; // Client giả mạo nội dung

        router.handleChatMessage(aliceConnection.session, replyMsg);

        // 3. Bob nhận được tin Reply với metadata CHUẨN XÁC từ DB
        ProtocolMessage bobReceived = bobConnection.readMessage();
        assertNotNull(bobReceived);
        assertEquals("msg-reply-200", bobReceived.messageId);
        assertEquals("reply", bobReceived.kind);
        assertEquals("msg-old-100", bobReceived.replyToMessageId);
        assertEquals("bob", bobReceived.replyToSender, "Server phải lấy sender gốc từ DB, không tin Client");
        assertEquals("Noi dung tin nhan goc tu Bob", bobReceived.replyToContent, "Server phải lấy content gốc từ DB");

        // 4. Alice nhận được MESSAGE_ACK xác nhận gửi thành công
        ProtocolMessage ack = aliceConnection.readMessage();
        assertNotNull(ack);
        assertEquals(MessageType.MESSAGE_ACK, ack.type);
        assertEquals("msg-reply-200", ack.messageId);
        assertEquals("SENT", ack.status);

        // 5. Kiểm tra tin nhắn mới trong DB
        ProtocolMessage savedReply = messageDao.findById("msg-reply-200");
        assertNotNull(savedReply);
        assertEquals("bob", savedReply.replyToSender);
        assertEquals("Noi dung tin nhan goc tu Bob", savedReply.replyToContent);
    }

    @Test
    @DisplayName("Reply to non-existent message: Server rejects with MESSAGE_NOT_FOUND")
    void testReplyToNonExistentMessage_RejectsWithMessageNotFound() throws Exception {
        String convId = "room-work";
        conversationDao.createConversation(convId, "ROOM", "Work Room");
        conversationDao.addMember(convId, "alice");

        ProtocolMessage replyMsg = new ProtocolMessage(MessageType.REPLY);
        replyMsg.messageId = "msg-reply-300";
        replyMsg.convId = convId;
        replyMsg.sender = "alice";
        replyMsg.content = "Tra loi tin khong ton tai";
        replyMsg.replyToMessageId = "msg-ghost-9999";

        router.handleChatMessage(aliceConnection.session, replyMsg);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("MESSAGE_NOT_FOUND", err.errorCode);
        assertNull(messageDao.findById("msg-reply-300"), "Tin nhắn lỗi không được lưu vào DB");
    }

    @Test
    @DisplayName("Reply to message of another room: Server rejects with INVALID_REPLY_TARGET")
    void testReplyToMessageFromDifferentRoom_RejectsWithInvalidReplyTarget() throws Exception {
        conversationDao.createConversation("room-A", "ROOM", "Room A");
        conversationDao.createConversation("room-B", "ROOM", "Room B");
        conversationDao.addMember("room-A", "alice");
        conversationDao.addMember("room-B", "alice");

        // Tin nhắn gốc thuộc room-A
        ProtocolMessage oldMsgRoomA = new ProtocolMessage(MessageType.CHAT);
        oldMsgRoomA.messageId = "msg-roomA-01";
        oldMsgRoomA.convId = "room-A";
        oldMsgRoomA.sender = "bob";
        oldMsgRoomA.content = "Tin cua phong A";
        messageDao.save(oldMsgRoomA);

        // Alice cố tình Reply tin của room-A trong room-B
        ProtocolMessage replyInRoomB = new ProtocolMessage(MessageType.REPLY);
        replyInRoomB.messageId = "msg-reply-wrong-room";
        replyInRoomB.convId = "room-B";
        replyInRoomB.sender = "alice";
        replyInRoomB.content = "Tra loi tin phong A nhung gui o phong B";
        replyInRoomB.replyToMessageId = "msg-roomA-01";

        router.handleChatMessage(aliceConnection.session, replyInRoomB);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("INVALID_REPLY_TARGET", err.errorCode);
    }

    @Test
    @DisplayName("Forward old message: Server validates source and target permissions and extracts official metadata")
    void testForwardOldMessage_SuccessWithMetadataFromDb() throws Exception {
        String sourceConv = "room-source";
        String targetConv = "room-target";

        conversationDao.createConversation(sourceConv, "ROOM", "Source Room");
        conversationDao.createConversation(targetConv, "ROOM", "Target Room");

        conversationDao.addMember(sourceConv, "bob");
        conversationDao.addMember(sourceConv, "alice"); // Alice có quyền ở nguồn
        conversationDao.addMember(targetConv, "alice"); // Alice có quyền ở đích
        conversationDao.addMember(targetConv, "bob");

        // Tin nhắn gốc ở room-source
        ProtocolMessage origMessage = new ProtocolMessage(MessageType.CHAT);
        origMessage.messageId = "msg-fwd-source-01";
        origMessage.convId = sourceConv;
        origMessage.sender = "bob";
        origMessage.content = "Tai lieu huong dan du an";
        messageDao.save(origMessage);

        // Alice chuyển tiếp tin nhắn này sang room-target
        ProtocolMessage fwdMsg = new ProtocolMessage(MessageType.FORWARD);
        fwdMsg.messageId = "msg-fwd-dest-02";
        fwdMsg.convId = targetConv;
        fwdMsg.sender = "alice";
        fwdMsg.content = "Gui moi nguoi tai lieu nay";
        fwdMsg.forwardFromMessageId = "msg-fwd-source-01";

        router.handleChatMessage(aliceConnection.session, fwdMsg);

        // Bob ở room-target nhận được tin nhắn forward với metadata chuẩn
        ProtocolMessage received = bobConnection.readMessage();
        assertNotNull(received);
        assertEquals("msg-fwd-dest-02", received.messageId);
        assertEquals("forward", received.kind);
        assertTrue(received.isForwarded);
        assertEquals("msg-fwd-source-01", received.forwardFromMessageId);
        assertEquals(sourceConv, received.forwardFromConvId, "Metadata source convId phải lấy từ DB");
        assertEquals("bob", received.forwardedFromSender, "Metadata source sender phải lấy từ DB");

        // Alice nhận được ACK
        ProtocolMessage ack = aliceConnection.readMessage();
        assertNotNull(ack);
        assertEquals(MessageType.MESSAGE_ACK, ack.type);
        assertEquals("SENT", ack.status);
    }

    @Test
    @DisplayName("Forward non-existent message: Server rejects with MESSAGE_NOT_FOUND")
    void testForwardNonExistentMessage_RejectsWithMessageNotFound() throws Exception {
        String targetConv = "room-target";
        conversationDao.createConversation(targetConv, "ROOM", "Target Room");
        conversationDao.addMember(targetConv, "alice");

        ProtocolMessage fwdMsg = new ProtocolMessage(MessageType.FORWARD);
        fwdMsg.messageId = "msg-fwd-ghost";
        fwdMsg.convId = targetConv;
        fwdMsg.sender = "alice";
        fwdMsg.content = "Chuyen tiep tin ao";
        fwdMsg.forwardFromMessageId = "ghost-source-999";

        router.handleChatMessage(aliceConnection.session, fwdMsg);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("MESSAGE_NOT_FOUND", err.errorCode);
    }

    @Test
    @DisplayName("Forward when sender is NOT a member of source conversation: Rejects with FORBIDDEN")
    void testForward_NotMemberOfSourceRoom_RejectsWithForbidden() throws Exception {
        String privateConv = "room-secret-source";
        String publicConv = "room-public-dest";

        conversationDao.createConversation(privateConv, "ROOM", "Secret Source Room");
        conversationDao.createConversation(publicConv, "ROOM", "Public Room");

        conversationDao.addMember(privateConv, "bob");
        // Eve KHÔNG PHẢI là thành viên của room-secret-source!
        conversationDao.addMember(publicConv, "eve");

        ProtocolMessage secretMsg = new ProtocolMessage(MessageType.CHAT);
        secretMsg.messageId = "secret-001";
        secretMsg.convId = privateConv;
        secretMsg.sender = "bob";
        secretMsg.content = "Bi mat noi bo";
        messageDao.save(secretMsg);

        // Eve cố tình forward tin bí mật này ra room-public
        ProtocolMessage eveFwd = new ProtocolMessage(MessageType.FORWARD);
        eveFwd.messageId = "eve-fwd-01";
        eveFwd.convId = publicConv;
        eveFwd.sender = "eve";
        eveFwd.content = "Toi lay duoc tin bi mat";
        eveFwd.forwardFromMessageId = "secret-001";

        router.handleChatMessage(eveConnection.session, eveFwd);

        ProtocolMessage err = eveConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN", err.errorCode);
    }

    @Test
    @DisplayName("Forward when sender is NOT a member of target conversation: Rejects with FORBIDDEN")
    void testForward_NotMemberOfTargetRoom_RejectsWithForbidden() throws Exception {
        String sourceConv = "room-source";
        String secretTarget = "room-secret-target";

        conversationDao.createConversation(sourceConv, "ROOM", "Source Room");
        conversationDao.createConversation(secretTarget, "ROOM", "Secret Target Room");

        conversationDao.addMember(sourceConv, "alice");
        conversationDao.addMember(secretTarget, "bob");
        // Alice KHÔNG PHẢI là thành viên của room-secret-target!

        ProtocolMessage origMsg = new ProtocolMessage(MessageType.CHAT);
        origMsg.messageId = "orig-msg-01";
        origMsg.convId = sourceConv;
        origMsg.sender = "alice";
        origMsg.content = "Tin nguon hop le";
        messageDao.save(origMsg);

        ProtocolMessage fwdToSecret = new ProtocolMessage(MessageType.FORWARD);
        fwdToSecret.messageId = "fwd-unauth-dest";
        fwdToSecret.convId = secretTarget;
        fwdToSecret.sender = "alice";
        fwdToSecret.content = "Forward vao nhom khong co quyen";
        fwdToSecret.forwardFromMessageId = "orig-msg-01";

        router.handleChatMessage(aliceConnection.session, fwdToSecret);

        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("FORBIDDEN", err.errorCode);
    }

    @Test
    @DisplayName("Server restart simulation: Messages persisted before restart are queried accurately for reply/forward")
    void testServerRestartSimulation_PersistedMessagesRepliedAndForwardedSuccessfully() throws Exception {
        // Giai đoạn 1: Trước khi restart, có 1 tin nhắn cũ được lưu vào DB
        String convId = "conv-room-restart";
        conversationDao.createConversation(convId, "ROOM", "Restart Room");
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "bob");

        ProtocolMessage persistedMsg = new ProtocolMessage(MessageType.CHAT);
        persistedMsg.messageId = "msg-before-restart-01";
        persistedMsg.convId = convId;
        persistedMsg.sender = "bob";
        persistedMsg.content = "Tin nhan truoc khi restart server 3 ngay truoc";
        persistedMsg.timestamp = 1500000000000L;
        messageDao.save(persistedMsg);

        // Giai đoạn 2: Mô phỏng Server Restart - tạo router mới dùng chung MessageDao/ConversationDao
        OnlineUserRegistry freshRegistry = new OnlineUserRegistry();
        Transaction freshTransaction = new Transaction(messageDao);
        MessageRouter restartedRouter = new MessageRouter(freshRegistry, messageDao, conversationDao, freshTransaction);

        freshRegistry.register(aliceConnection.session);
        freshRegistry.register(bobConnection.session);

        // Giai đoạn 3: Alice thực hiện Reply tin nhắn cũ sau khi restart
        ProtocolMessage replyMsg = new ProtocolMessage(MessageType.REPLY);
        replyMsg.messageId = "msg-after-restart-reply";
        replyMsg.convId = convId;
        replyMsg.sender = "alice";
        replyMsg.content = "Server restart roi van reply duoc!";
        replyMsg.replyToMessageId = "msg-before-restart-01";

        restartedRouter.handleChatMessage(aliceConnection.session, replyMsg);

        ProtocolMessage bobRecv = bobConnection.readMessage();
        assertNotNull(bobRecv);
        assertEquals("msg-after-restart-reply", bobRecv.messageId);
        assertEquals("bob", bobRecv.replyToSender);
        assertEquals("Tin nhan truoc khi restart server 3 ngay truoc", bobRecv.replyToContent);
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
