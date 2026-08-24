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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MessageRouterTest {

    private OnlineUserRegistry registry;
    private MessageRouter router;

    private TestConnection aliceConnection;
    private TestConnection bobConnection;
    private TestConnection charlieConnection;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OnlineUserRegistry();
        router = new MessageRouter(registry);

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
    public void testClientAGuiClientBThanhCong() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-101";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.content = "Chao Bob!";
        msg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(aliceConnection.session, msg);

        // Bob nhan duoc tin nhan CHAT tu Alice
        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("Alice", receivedByBob.sender);
        assertEquals("Bob", receivedByBob.target);
        assertEquals("Chao Bob!", receivedByBob.content);

        // Alice nhan duoc phan hoi CHAT_OK
        ProtocolMessage receivedByAlice = aliceConnection.readMessage();
        assertNotNull(receivedByAlice);
        assertEquals(MessageType.CHAT_OK, receivedByAlice.type);
        assertEquals("msg-101", receivedByAlice.messageId);
    }

    @Test
    public void testClientBGuiLaiClientAThanhCong() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-102";
        msg.sender = "Bob";
        msg.target = "Alice";
        msg.content = "Chao Alice, minh nhan duoc roi!";
        msg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(bobConnection.session, msg);

        // Alice nhan duoc tin nhan CHAT tu Bob
        ProtocolMessage receivedByAlice = aliceConnection.readMessage();
        assertNotNull(receivedByAlice);
        assertEquals(MessageType.CHAT, receivedByAlice.type);
        assertEquals("Bob", receivedByAlice.sender);
        assertEquals("Alice", receivedByAlice.target);

        // Bob nhan duoc CHAT_OK
        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals(MessageType.CHAT_OK, receivedByBob.type);
        assertEquals("msg-102", receivedByBob.messageId);
    }

    @Test
    public void testClientCKhongNhanTinNhanCuaAAndB() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-103";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.content = "Tin nhan rieng tu Alice den Bob";
        msg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(aliceConnection.session, msg);

        // Bob nhan tin nhan CHAT
        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals("Tin nhan rieng tu Alice den Bob", receivedByBob.content);

        // Charlie KHONG nhan duoc tin nhan nao (Socket timeout)
        assertThrows(SocketTimeoutException.class, () -> charlieConnection.readMessage());
    }

    @Test
    public void testGiaMaoNguoiGui() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-104";
        msg.sender = "Eve"; // Gia mao
        msg.target = "Bob";
        msg.content = "Tin nhan gia mao";
        msg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(aliceConnection.session, msg);

        // Alice nhan duoc tin nhan ERROR voi ma INVALID_SENDER
        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("INVALID_SENDER", err.errorCode);

        // Bob KHONG nhan duoc tin nhan gia mao
        assertThrows(SocketTimeoutException.class, () -> bobConnection.readMessage());
    }

    @Test
    public void testNguoiNhanOfflineHoacKhongTonTai() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-105";
        msg.sender = "Alice";
        msg.target = "UnknownUser"; // User khong ton tai
        msg.content = "Alo?";
        msg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(aliceConnection.session, msg);

        // Alice nhan duoc loi USER_OFFLINE
        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("USER_OFFLINE", err.errorCode);
    }

    @Test
    public void testNoiDungRongHoacQuaDai() throws Exception {
        // 1. Noi dung rong
        ProtocolMessage emptyMsg = new ProtocolMessage(MessageType.CHAT);
        emptyMsg.messageId = "msg-106";
        emptyMsg.sender = "Alice";
        emptyMsg.target = "Bob";
        emptyMsg.content = "   ";

        router.handleChatMessage(aliceConnection.session, emptyMsg);
        ProtocolMessage err1 = aliceConnection.readMessage();
        assertEquals(MessageType.ERROR, err1.type);
        assertEquals("INVALID_CONTENT", err1.errorCode);

        // 2. Noi dung > 5000 ky tu
        ProtocolMessage longMsg = new ProtocolMessage(MessageType.CHAT);
        longMsg.messageId = "msg-107";
        longMsg.sender = "Alice";
        longMsg.target = "Bob";
        longMsg.content = "a".repeat(5001);

        router.handleChatMessage(aliceConnection.session, longMsg);
        ProtocolMessage err2 = aliceConnection.readMessage();
        assertEquals(MessageType.ERROR, err2.type);
        assertEquals("CONTENT_TOO_LONG", err2.errorCode);
    }

    @Test
    public void testServerTiepTucHoatDongSauLoi() throws Exception {
        // Dot 1: Gui tin nhan loi (nguoi nhan rỗng)
        ProtocolMessage badMsg = new ProtocolMessage(MessageType.CHAT);
        badMsg.messageId = "msg-108";
        badMsg.sender = "Alice";
        badMsg.target = "";
        badMsg.content = "Loi target";

        router.handleChatMessage(aliceConnection.session, badMsg);
        ProtocolMessage err = aliceConnection.readMessage();
        assertEquals("INVALID_TARGET", err.errorCode);

        // Dot 2: Gui tin nhan hop le ngay sau do -> Van hoat dong binh thuong
        ProtocolMessage validMsg = new ProtocolMessage(MessageType.CHAT);
        validMsg.messageId = "msg-109";
        validMsg.sender = "Alice";
        validMsg.target = "Bob";
        validMsg.content = "Lai thanh cong roi!";

        router.handleChatMessage(aliceConnection.session, validMsg);
        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertEquals("Lai thanh cong roi!", receivedByBob.content);
    }

    @Test
    public void testGuiTinNhanReplyToMetadata() throws Exception {
        ProtocolMessage replyMsg = new ProtocolMessage(MessageType.CHAT);
        replyMsg.messageId = "msg-201";
        replyMsg.sender = "Alice";
        replyMsg.target = "Bob";
        replyMsg.content = "Tra loi tin nhan msg-100 cua Bob";
        replyMsg.replyTo = "msg-100";
        replyMsg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(aliceConnection.session, replyMsg);

        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("Alice", receivedByBob.sender);
        assertEquals("Bob", receivedByBob.target);
        assertEquals("msg-100", receivedByBob.replyTo);
        assertNull(receivedByBob.forwardOf);

        ProtocolMessage receivedByAlice = aliceConnection.readMessage();
        assertEquals(MessageType.CHAT_OK, receivedByAlice.type);
        assertEquals("msg-201", receivedByAlice.messageId);
    }

    @Test
    public void testGuiTinNhanForwardOfMetadata() throws Exception {
        ProtocolMessage forwardMsg = new ProtocolMessage(MessageType.CHAT);
        forwardMsg.messageId = "msg-202";
        forwardMsg.sender = "Alice";
        forwardMsg.target = "Bob";
        forwardMsg.content = "Chuyen tiep tin nhan goc msg-050";
        forwardMsg.forwardOf = "msg-050";
        forwardMsg.timestamp = System.currentTimeMillis();

        router.handleChatMessage(aliceConnection.session, forwardMsg);

        ProtocolMessage receivedByBob = bobConnection.readMessage();
        assertNotNull(receivedByBob);
        assertEquals(MessageType.CHAT, receivedByBob.type);
        assertEquals("Alice", receivedByBob.sender);
        assertEquals("Bob", receivedByBob.target);
        assertEquals("msg-050", receivedByBob.forwardOf);
        assertNull(receivedByBob.replyTo);

        ProtocolMessage receivedByAlice = aliceConnection.readMessage();
        assertEquals(MessageType.CHAT_OK, receivedByAlice.type);
        assertEquals("msg-202", receivedByAlice.messageId);
    }

    @Test
    public void testTinNhanNullHandling() throws Exception {
        router.handleChatMessage(aliceConnection.session, null);
        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("INVALID_MESSAGE", err.errorCode);
    }

    @Test
    public void testTargetSocketClosedReturnsUserOffline() throws Exception {
        // Dong session cua Bob (nguoi nhan offline)
        bobConnection.close();

        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-301";
        msg.sender = "Alice";
        msg.target = "Bob";
        msg.content = "Gui khi Bob da offline";

        router.handleChatMessage(aliceConnection.session, msg);

        // Alice nhan duoc USER_OFFLINE
        ProtocolMessage err = aliceConnection.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("USER_OFFLINE", err.errorCode);
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
