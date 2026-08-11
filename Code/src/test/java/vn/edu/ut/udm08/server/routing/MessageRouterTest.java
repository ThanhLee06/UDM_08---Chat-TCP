package vn.edu.ut.udm08.server.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.SessionRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageRouterTest {

    private SessionRegistry registry;
    private MessageRouter router;
    private DummyClientSession alice;
    private DummyClientSession bob;

    @BeforeEach
    public void setUp() {
        registry = new SessionRegistry();
        router = new MessageRouter(registry);

        alice = new DummyClientSession("alice", "avatar1");
        bob = new DummyClientSession("bob", "avatar2");

        registry.register(alice);
        registry.register(bob);
    }

    @Test
    public void testGuiTinNhanThanhCong() {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "101";
        msg.sender = "alice";
        msg.target = "bob";
        msg.content = "Chao Bob";

        router.handleChatMessage(alice, msg);

        // Bob nhan duoc tin nhan CHAT
        assertEquals(1, bob.receivedMessages.size());
        assertEquals("Chao Bob", bob.receivedMessages.get(0).content);

        // Alice nhan duoc phan hoi CHAT_OK
        assertEquals(1, alice.receivedMessages.size());
        assertEquals(MessageType.CHAT_OK, alice.receivedMessages.get(0).type);
    }

    @Test
    public void testGiaMaoNguoiGui() {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "102";
        msg.sender = "eve"; // Gia mao
        msg.target = "bob";
        msg.content = "Tin nhan gia";

        router.handleChatMessage(alice, msg);

        // Alice nhan duoc loi INVALID_SENDER
        assertEquals(1, alice.receivedMessages.size());
        assertEquals("INVALID_SENDER", alice.receivedMessages.get(0).errorCode);
    }

    @Test
    public void testNguoiNhanOffline() {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "103";
        msg.sender = "alice";
        msg.target = "charlie"; // Charlie khong online
        msg.content = "Alo";

        router.handleChatMessage(alice, msg);

        // Alice nhan duoc loi USER_OFFLINE
        assertEquals(1, alice.receivedMessages.size());
        assertEquals("USER_OFFLINE", alice.receivedMessages.get(0).errorCode);
    }

    @Test
    public void testNoiDungRong() {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "104";
        msg.sender = "alice";
        msg.target = "bob";
        msg.content = ""; // Noi dung rong

        router.handleChatMessage(alice, msg);

        assertEquals(1, alice.receivedMessages.size());
        assertEquals("INVALID_CONTENT", alice.receivedMessages.get(0).errorCode);
    }

    // Class Dummy ho tro test
    private static class DummyClientSession implements ClientSession {
        private String username;
        private String avatarId;
        public List<ProtocolMessage> receivedMessages = new ArrayList<>();

        public DummyClientSession(String username, String avatarId) {
            this.username = username;
            this.avatarId = avatarId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public void setUsername(String username) {
            this.username = username;
        }

        @Override
        public String getAvatarId() {
            return avatarId;
        }

        @Override
        public void setAvatarId(String avatarId) {
            this.avatarId = avatarId;
        }

        @Override
        public void sendMessage(ProtocolMessage message) throws Exception {
            receivedMessages.add(message);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
