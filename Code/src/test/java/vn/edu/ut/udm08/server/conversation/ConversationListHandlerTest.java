package vn.edu.ut.udm08.server.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConversationListHandlerTest {

    private ConversationDao conversationDao;
    private ConversationListHandler handler;

    private TestConnection aliceConn;
    private TestConnection strangerConn;

    @BeforeEach
    void setUp() throws Exception {
        conversationDao = new InMemoryConversationDao();
        handler = new ConversationListHandler(conversationDao);

        aliceConn = new TestConnection("alice", "avatar_alice.png");
        strangerConn = new TestConnection("stranger", "avatar_stranger.png");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (aliceConn != null) aliceConn.close();
        if (strangerConn != null) strangerConn.close();
    }

    @Test
    void testInboxRetrievalSuccessWithDmAndRoom() throws Exception {
        // 1. Đăng ký avatar của các user
        conversationDao.setUserAvatar("bob", "avatar_bob.png");
        conversationDao.setUserAvatar("charlie", "avatar_charlie.png");

        // 2. Tạo hội thoại nhóm Room: Nhom Du An (lastActivity = 3000L)
        String roomConvId = "room:project";
        conversationDao.createConversation(roomConvId, "ROOM", "Nhóm Dự Án", "room_avatar.png");
        conversationDao.addMember(roomConvId, "alice");
        conversationDao.addMember(roomConvId, "bob");
        conversationDao.addMember(roomConvId, "charlie");

        ProtocolMessage roomMsg = new ProtocolMessage(MessageType.CHAT);
        roomMsg.convId = roomConvId;
        roomMsg.sender = "bob";
        roomMsg.content = "Họp lúc 9h sáng nhé";
        roomMsg.timestamp = 3000L;
        conversationDao.addMessage(roomConvId, roomMsg);

        // 3. Tạo hội thoại DM Alice - Bob (lastActivity = 2000L)
        String dmBobConvId = "dm:alice:bob";
        conversationDao.createConversation(dmBobConvId, "DM", null);
        conversationDao.addMember(dmBobConvId, "alice");
        conversationDao.addMember(dmBobConvId, "bob");

        ProtocolMessage dmBobMsg = new ProtocolMessage(MessageType.CHAT);
        dmBobMsg.convId = dmBobConvId;
        dmBobMsg.sender = "bob";
        dmBobMsg.target = "alice";
        dmBobMsg.content = "Chào Alice, bạn khỏe không?";
        dmBobMsg.timestamp = 2000L;
        conversationDao.addMessage(dmBobConvId, dmBobMsg);

        // 4. Tạo hội thoại DM Alice - Charlie (lastActivity = 1000L)
        String dmCharlieConvId = "dm:alice:charlie";
        conversationDao.createConversation(dmCharlieConvId, "DM", null);
        conversationDao.addMember(dmCharlieConvId, "alice");
        conversationDao.addMember(dmCharlieConvId, "charlie");

        ProtocolMessage dmCharlieMsg = new ProtocolMessage(MessageType.CHAT);
        dmCharlieMsg.convId = dmCharlieConvId;
        dmCharlieMsg.sender = "alice";
        dmCharlieMsg.target = "charlie";
        dmCharlieMsg.content = "Gửi tài liệu giúp mình";
        dmCharlieMsg.timestamp = 1000L;
        conversationDao.addMessage(dmCharlieMsg.convId, dmCharlieMsg);

        // 5. Alice gửi CONVERSATION_LIST_REQUEST
        ProtocolMessage request = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        request.requestId = "req-inbox-01";

        handler.handleConversationListRequest(aliceConn.session, request);

        ProtocolMessage response = aliceConn.readMessage();
        assertNotNull(response);
        assertEquals(MessageType.CONVERSATION_LIST_RESPONSE, response.type);
        assertEquals("req-inbox-01", response.requestId);

        List<ConversationSummary> inbox = response.conversations;
        assertNotNull(inbox);
        assertEquals(3, inbox.size(), "Alice phải có đúng 3 cuộc trò chuyện");

        // Kiểm tra thứ tự sắp xếp giảm dần theo lastActivity (mới nhất lên đầu)
        assertEquals(roomConvId, inbox.get(0).convId);
        assertEquals("ROOM", inbox.get(0).chatType);
        assertEquals("Nhóm Dự Án", inbox.get(0).displayName);
        assertEquals("room_avatar.png", inbox.get(0).avatar);
        assertEquals("Họp lúc 9h sáng nhé", inbox.get(0).lastMessage);
        assertEquals(3000L, inbox.get(0).lastActivity);

        // DM với Bob: displayName phải là Bob (người đối diện, KHÔNG phải Alice)
        assertEquals(dmBobConvId, inbox.get(1).convId);
        assertEquals("DM", inbox.get(1).chatType);
        assertEquals("bob", inbox.get(1).displayName);
        assertEquals("avatar_bob.png", inbox.get(1).avatar);
        assertEquals("Chào Alice, bạn khỏe không?", inbox.get(1).lastMessage);
        assertEquals(2000L, inbox.get(1).lastActivity);

        // DM với Charlie: displayName phải là Charlie (KHÔNG phải Alice)
        assertEquals(dmCharlieConvId, inbox.get(2).convId);
        assertEquals("DM", inbox.get(2).chatType);
        assertEquals("charlie", inbox.get(2).displayName);
        assertEquals("avatar_charlie.png", inbox.get(2).avatar);
        assertEquals("Gửi tài liệu giúp mình", inbox.get(2).lastMessage);
        assertEquals(1000L, inbox.get(2).lastActivity);
    }

    @Test
    void testIncludesOfflineUsers() throws Exception {
        // Bob và Charlie đều offline (không hề kết nối socket tới server)
        conversationDao.setUserAvatar("offline_user", "avatar_offline.png");
        String convId = "dm:alice:offline_user";
        conversationDao.createConversation(convId, "DM", null);
        conversationDao.addMember(convId, "alice");
        conversationDao.addMember(convId, "offline_user");

        ProtocolMessage req = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        req.requestId = "req-offline-test";

        handler.handleConversationListRequest(aliceConn.session, req);

        ProtocolMessage res = aliceConn.readMessage();
        assertNotNull(res);
        assertEquals(MessageType.CONVERSATION_LIST_RESPONSE, res.type);
        assertNotNull(res.conversations);
        assertEquals(1, res.conversations.size());
        assertEquals("offline_user", res.conversations.get(0).displayName);
        assertEquals("avatar_offline.png", res.conversations.get(0).avatar);
    }

    @Test
    void testEmptyInboxWhenUserHasNoConversations() throws Exception {
        ProtocolMessage req = new ProtocolMessage(MessageType.CONVERSATION_LIST_REQUEST);
        req.requestId = "req-empty-inbox";

        handler.handleConversationListRequest(strangerConn.session, req);

        ProtocolMessage res = strangerConn.readMessage();
        assertNotNull(res);
        assertEquals(MessageType.CONVERSATION_LIST_RESPONSE, res.type);
        assertNotNull(res.conversations);
        assertTrue(res.conversations.isEmpty());
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
