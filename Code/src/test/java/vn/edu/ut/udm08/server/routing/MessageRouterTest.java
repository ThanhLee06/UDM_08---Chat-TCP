package vn.edu.ut.udm08.server.routing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.conversation.ConversationRegistry;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MessageRouterTest {

    private IConversationRegistry convRegistry;
    private MessageRouter router;

    private ClientSession alice;
    private ClientSession bob;
    private ClientSession charlie;

    private Socket aliceClientSocket;
    private Socket bobClientSocket;
    private Socket charlieClientSocket;

    private BufferedReader aliceReader;
    private BufferedReader bobReader;
    private BufferedReader charlieReader;

    @BeforeEach
    void setUp() throws IOException {
        convRegistry = new ConversationRegistry();
        router = new MessageRouter(convRegistry);

        SocketPair alicePair = createClientSocket();
        SocketPair bobPair = createClientSocket();
        SocketPair charliePair = createClientSocket();

        aliceClientSocket = alicePair.clientSocket();
        bobClientSocket = bobPair.clientSocket();
        charlieClientSocket = charliePair.clientSocket();

        alice = ClientSession.createAnonymous(alicePair.serverSideSocket());
        bob = ClientSession.createAnonymous(bobPair.serverSideSocket());
        charlie = ClientSession.createAnonymous(charliePair.serverSideSocket());

        assertTrue(alice.authenticate("alice", "avatar1"));
        assertTrue(bob.authenticate("bob", "avatar2"));
        assertTrue(charlie.authenticate("charlie", "avatar3"));

        aliceReader = new BufferedReader(new InputStreamReader(aliceClientSocket.getInputStream(), StandardCharsets.UTF_8));
        bobReader = new BufferedReader(new InputStreamReader(bobClientSocket.getInputStream(), StandardCharsets.UTF_8));
        charlieReader = new BufferedReader(new InputStreamReader(charlieClientSocket.getInputStream(), StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (alice != null) {
            alice.close();
        }
        if (bob != null) {
            bob.close();
        }
        if (charlie != null) {
            charlie.close();
        }
        if (aliceClientSocket != null) {
            aliceClientSocket.close();
        }
        if (bobClientSocket != null) {
            bobClientSocket.close();
        }
        if (charlieClientSocket != null) {
            charlieClientSocket.close();
        }
    }

    @Test
    void shouldRouteChatMessageToDmConvIdSuccessfully() throws Exception {
        convRegistry.join("conv-dm-alice-bob", alice);
        convRegistry.join("conv-dm-alice-bob", bob);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "101";
        msg.sender = "alice";
        msg.convId = "conv-dm-alice-bob";
        msg.content = "Chao Bob";

        router.handleChatMessage(alice, msg);

        ProtocolMessage receivedByBob = readMessage(bobReader);
        ProtocolMessage receivedByAlice = readMessage(aliceReader);

        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("alice", receivedByBob.sender);
        assertEquals("conv-dm-alice-bob", receivedByBob.convId);
        assertEquals("Chao Bob", receivedByBob.content);

        assertEquals(MessageType.CHAT_OK, receivedByAlice.type);
        assertEquals("101", receivedByAlice.messageId);
        assertEquals("SERVER", receivedByAlice.sender);
    }

    @Test
    void shouldRouteChatMessageToPublicRoomWithMultipleMembers() throws Exception {
        convRegistry.join("GENERAL", alice);
        convRegistry.join("GENERAL", bob);
        convRegistry.join("GENERAL", charlie);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "202";
        msg.sender = "alice";
        msg.convId = "GENERAL";
        msg.content = "Thong bao phong chung!";

        router.handleChatMessage(alice, msg);

        ProtocolMessage receivedByBob = readMessage(bobReader);
        ProtocolMessage receivedByCharlie = readMessage(charlieReader);
        ProtocolMessage receivedByAlice = readMessage(aliceReader);

        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("Thong bao phong chung!", receivedByBob.content);

        assertEquals(MessageType.CHAT, receivedByCharlie.type);
        assertEquals("Thong bao phong chung!", receivedByCharlie.content);

        assertEquals(MessageType.CHAT_OK, receivedByAlice.type);
        assertEquals("202", receivedByAlice.messageId);
    }

    @Test
    void shouldRejectOfflineConvIdWhenNoOtherMembersOnline() throws Exception {
        convRegistry.join("conv-solo", alice);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "103";
        msg.sender = "alice";
        msg.convId = "conv-solo";
        msg.content = "Alo phien 1 nguoi";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("USER_OFFLINE", error.errorCode);
        assertEquals("103", error.messageId);
    }

    @Test
    void shouldRejectForgedSender() throws Exception {
        convRegistry.join("GENERAL", alice);
        convRegistry.join("GENERAL", bob);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "102";
        msg.sender = "eve";
        msg.convId = "GENERAL";
        msg.content = "Tin nhan gia mao";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("INVALID_SENDER", error.errorCode);
        assertEquals("102", error.messageId);
    }

    @Test
    void shouldRejectEmptyContent() throws Exception {
        convRegistry.join("GENERAL", alice);
        convRegistry.join("GENERAL", bob);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "104";
        msg.sender = "alice";
        msg.convId = "GENERAL";
        msg.content = "";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("INVALID_CONTENT", error.errorCode);
        assertEquals("104", error.messageId);
    }

    @Test
    void shouldRejectContentLongerThan5000Characters() throws Exception {
        convRegistry.join("GENERAL", alice);
        convRegistry.join("GENERAL", bob);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "105";
        msg.sender = "alice";
        msg.convId = "GENERAL";
        msg.content = "a".repeat(5001);

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("CONTENT_TOO_LONG", error.errorCode);
        assertEquals("105", error.messageId);
    }

    @Test
    void shouldRejectMessageWhenSenderIsNotMemberOfConvId() throws Exception {
        convRegistry.join("GENERAL", bob);

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "106";
        msg.sender = "alice";
        msg.convId = "GENERAL";
        msg.content = "Xin chao";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("NOT_A_MEMBER", error.errorCode);
        assertEquals("106", error.messageId);
    }

    private ProtocolMessage readMessage(BufferedReader reader) throws Exception {
        String json = reader.readLine();
        assertNotNull(json, "Expected a message from the server");
        return JsonUtil.fromJson(json);
    }

    private SocketPair createClientSocket() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept();
        serverSocket.close();
        return new SocketPair(clientSocket, serverSideSocket);
    }

    private record SocketPair(Socket clientSocket, Socket serverSideSocket) {}
}