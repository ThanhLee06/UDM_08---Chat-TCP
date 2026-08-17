package vn.edu.ut.udm08.server.routing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
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

    private OnlineUserRegistry registry;
    private MessageRouter router;

    private ClientSession alice;
    private ClientSession bob;

    private Socket aliceClientSocket;
    private Socket bobClientSocket;

    private BufferedReader aliceReader;
    private BufferedReader bobReader;

    @BeforeEach
    void setUp() throws IOException {
        registry = new OnlineUserRegistry();
        router = new MessageRouter(registry);

        SocketPair alicePair = createClientSocket();
        SocketPair bobPair = createClientSocket();

        aliceClientSocket = alicePair.clientSocket();
        bobClientSocket = bobPair.clientSocket();

        alice = ClientSession.createAnonymous(alicePair.serverSideSocket());
        bob = ClientSession.createAnonymous(bobPair.serverSideSocket());

        assertTrue(alice.authenticate("alice", "avatar1"));
        assertTrue(bob.authenticate("bob", "avatar2"));

        assertTrue(registry.register(alice));
        assertTrue(registry.register(bob));

        aliceReader = new BufferedReader(
                new InputStreamReader(
                        aliceClientSocket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        bobReader = new BufferedReader(
                new InputStreamReader(
                        bobClientSocket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        if (alice != null) {
            alice.close();
        }

        if (bob != null) {
            bob.close();
        }

        if (aliceClientSocket != null) {
            aliceClientSocket.close();
        }

        if (bobClientSocket != null) {
            bobClientSocket.close();
        }
    }

    @Test
    void shouldRouteChatMessageSuccessfully() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "101";
        msg.sender = "alice";
        msg.target = "bob";
        msg.content = "Chao Bob";

        router.handleChatMessage(alice, msg);

        ProtocolMessage receivedByBob = readMessage(bobReader);
        ProtocolMessage receivedByAlice = readMessage(aliceReader);

        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("alice", receivedByBob.sender);
        assertEquals("bob", receivedByBob.target);
        assertEquals("Chao Bob", receivedByBob.content);

        assertEquals(MessageType.CHAT_OK, receivedByAlice.type);
        assertEquals("101", receivedByAlice.messageId);
        assertEquals("SERVER", receivedByAlice.sender);
    }

    @Test
    void shouldRejectForgedSender() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "102";
        msg.sender = "eve";
        msg.target = "bob";
        msg.content = "Tin nhan gia mao";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("INVALID_SENDER", error.errorCode);
        assertEquals("102", error.messageId);

        assertNull(bobReader.ready() ? bobReader.readLine() : null);
    }

    @Test
    void shouldRejectOfflineRecipient() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "103";
        msg.sender = "alice";
        msg.target = "charlie";
        msg.content = "Alo";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("USER_OFFLINE", error.errorCode);
        assertEquals("103", error.messageId);
    }

    @Test
    void shouldRejectEmptyContent() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "104";
        msg.sender = "alice";
        msg.target = "bob";
        msg.content = "";

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("INVALID_CONTENT", error.errorCode);
        assertEquals("104", error.messageId);
    }

    @Test
    void shouldRejectContentLongerThan5000Characters() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "105";
        msg.sender = "alice";
        msg.target = "bob";
        msg.content = "a".repeat(5001);

        router.handleChatMessage(alice, msg);

        ProtocolMessage error = readMessage(aliceReader);

        assertEquals(MessageType.ERROR, error.type);
        assertEquals("CONTENT_TOO_LONG", error.errorCode);
        assertEquals("105", error.messageId);
    }

    private ProtocolMessage readMessage(BufferedReader reader) throws Exception {
        String json = reader.readLine();

        assertNotNull(json, "Expected a message from the server");

        return JsonUtil.fromJson(json);
    }

    private SocketPair createClientSocket() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);

        Socket clientSocket = new Socket(
                "localhost",
                serverSocket.getLocalPort()
        );

        Socket serverSideSocket = serverSocket.accept();

        serverSocket.close();

        return new SocketPair(clientSocket, serverSideSocket);
    }

    private record SocketPair(
            Socket clientSocket,
            Socket serverSideSocket
    ) {
    }
}