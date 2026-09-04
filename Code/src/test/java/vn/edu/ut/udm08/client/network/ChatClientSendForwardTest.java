package vn.edu.ut.udm08.client.network;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-051: ChatClient Send Forward Message Unit Tests")
class ChatClientSendForwardTest {

    private ServerSocket mockServer;
    private Thread serverThread;
    private int serverPort;
    private String loopbackHost;
    private ChatClient client;
    private volatile Socket serverSideClientSocket;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @BeforeEach
    void setUp() throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        loopbackHost = loopback.getHostAddress();
        mockServer = new ServerSocket(0, 50, loopback);
        serverPort = mockServer.getLocalPort();
        client = new ChatClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.disconnect();
        }
        if (serverSideClientSocket != null && !serverSideClientSocket.isClosed()) {
            serverSideClientSocket.close();
        }
        if (mockServer != null && !mockServer.isClosed()) {
            mockServer.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    private void establishConnection(String username) throws Exception {
        CountDownLatch helloLatch = new CountDownLatch(1);
        serverThread = new Thread(() -> {
            try {
                serverSideClientSocket = mockServer.accept();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));
                reader.readLine(); // Đọc gói HELLO
                helloLatch.countDown();
            } catch (IOException ignored) {}
        });
        serverThread.start();

        client.connect(loopbackHost, serverPort, username, "avatar_cat", new StubChatListener());
        assertTrue(helloLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("TC_01: Gửi tin nhắn chuyển tiếp (Forward) kèm lời nhắn thành công")
    void testSendForwardWithContentValid() throws Exception {
        establishConnection("Alice");

        CountDownLatch forwardLatch = new CountDownLatch(1);
        AtomicReference<String> receivedJson = new AtomicReference<>();

        Thread readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));
                String line = reader.readLine();
                receivedJson.set(line);
                forwardLatch.countDown();
            } catch (IOException ignored) {}
        });
        readerThread.start();

        ProtocolMessage sentMsg = client.sendForward("conv-target-group", "Chuyen tiep thong bao quan trong nay!", "msg-source-999");

        assertNotNull(sentMsg);
        assertEquals("conv-target-group", sentMsg.convId);
        assertEquals("conv-target-group", sentMsg.target);
        assertEquals("msg-source-999", sentMsg.fwdFrom);
        assertEquals("Alice", sentMsg.sender);
        assertEquals("Chuyen tiep thong bao quan trong nay!", sentMsg.content);

        assertTrue(forwardLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(receivedJson.get());

        ProtocolMessage serverReceived = JsonUtil.fromJson(receivedJson.get());
        assertEquals(MessageType.CHAT, serverReceived.type);
        assertEquals("conv-target-group", serverReceived.convId);
        assertEquals("msg-source-999", serverReceived.fwdFrom);
        assertEquals("Alice", serverReceived.sender);
        assertEquals("Chuyen tiep thong bao quan trong nay!", serverReceived.content);
    }

    @Test
    @DisplayName("TC_02: Gửi tin nhắn chuyển tiếp không kèm lời nhắn (content null/blank) thành công")
    void testSendForwardWithoutContentValid() throws Exception {
        establishConnection("Alice");

        CountDownLatch forwardLatch = new CountDownLatch(1);
        AtomicReference<String> receivedJson = new AtomicReference<>();

        Thread readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));
                String line = reader.readLine();
                receivedJson.set(line);
                forwardLatch.countDown();
            } catch (IOException ignored) {}
        });
        readerThread.start();

        ProtocolMessage sentMsg = client.sendForward("conv-room-2", null, "msg-source-777");

        assertNotNull(sentMsg);
        assertEquals("", sentMsg.content);
        assertEquals("msg-source-777", sentMsg.fwdFrom);

        assertTrue(forwardLatch.await(5, TimeUnit.SECONDS));
        ProtocolMessage serverReceived = JsonUtil.fromJson(receivedJson.get());
        assertEquals("msg-source-777", serverReceived.fwdFrom);
    }

    @Test
    @DisplayName("TC_03: Chặn gửi tin nhắn chuyển tiếp khi Client chưa kết nối đến Server")
    void testSendForwardWhenNotConnected() {
        assertThrows(IOException.class, () -> {
            client.sendForward("conv-target", "Content", "msg-source");
        });
    }

    @Test
    @DisplayName("TC_04: Chặn gửi khi targetConvId là null hoặc khoảng trắng")
    void testSendForwardNullOrBlankTargetConvId() throws Exception {
        establishConnection("Alice");
        assertThrows(IllegalArgumentException.class, () -> client.sendForward(null, "Text", "msg-src"));
        assertThrows(IllegalArgumentException.class, () -> client.sendForward("   ", "Text", "msg-src"));
        assertThrows(IllegalArgumentException.class, () -> client.sendForward("", "Text", "msg-src"));
    }

    @Test
    @DisplayName("TC_05: Chặn gửi khi fwdFromMessageId là null hoặc khoảng trắng")
    void testSendForwardNullOrBlankFwdFrom() throws Exception {
        establishConnection("Alice");
        assertThrows(IllegalArgumentException.class, () -> client.sendForward("conv-dest", "Text", null));
        assertThrows(IllegalArgumentException.class, () -> client.sendForward("conv-dest", "Text", "   "));
        assertThrows(IllegalArgumentException.class, () -> client.sendForward("conv-dest", "Text", ""));
    }

    @Test
    @DisplayName("TC_06: Chặn gửi tin nhắn chuyển tiếp có độ dài lời nhắn vượt quá 5000 ký tự")
    void testSendForwardContentTooLong() throws Exception {
        establishConnection("Alice");
        String longContent = "A".repeat(5001);
        assertThrows(IllegalArgumentException.class, () -> client.sendForward("conv-dest", longContent, "msg-src"));
    }

    @Test
    @DisplayName("TC_07: Cho phép gửi lời nhắn chuyển tiếp đạt đúng giới hạn biên 5000 ký tự")
    void testSendForwardContentBoundary5000() throws Exception {
        establishConnection("Alice");
        String maxContent = "F".repeat(5000);
        assertDoesNotThrow(() -> client.sendForward("conv-dest", maxContent, "msg-src"));
    }

    @Test
    @DisplayName("TC_08: Tự động cắt bỏ khoảng trắng thừa (Trim) ở targetConvId, content và fwdFrom")
    void testSendForwardAutoTrim() throws Exception {
        establishConnection("Alice");
        ProtocolMessage msg = client.sendForward("  conv-destination  ", "  Loi nhan fwd  ", "  msg-src-123  ");
        assertEquals("conv-destination", msg.convId);
        assertEquals("Loi nhan fwd", msg.content);
        assertEquals("msg-src-123", msg.fwdFrom);
    }

    @Test
    @DisplayName("TC_09: Đối tượng ProtocolMessage trả về có timestamp hợp lệ và messageId UUID không rỗng")
    void testSendForwardReturnsValidProtocolMessage() throws Exception {
        establishConnection("Alice");
        ProtocolMessage msg = client.sendForward("conv-dest", "Forward text", "msg-orig");
        assertNotNull(msg.messageId);
        assertFalse(msg.messageId.isBlank());
        assertTrue(msg.timestamp > 0);
        assertEquals(MessageType.CHAT, msg.type);
    }

    @Test
    @DisplayName("TC_10: Gửi tuần tự 5 tin nhắn chuyển tiếp liên tiếp thành công")
    void testSendMultipleForwardsInSequence() throws Exception {
        establishConnection("Alice");
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            assertDoesNotThrow(() -> client.sendForward("conv-fwd-" + idx, "Fwd text " + idx, "src-" + idx));
        }
    }

    private static class StubChatListener implements ChatListener {
        @Override public void onLoginSuccess(ProtocolMessage message) {}
        @Override public void onUserListUpdated(List<UserProfile> users) {}
        @Override public void onMessageReceived(ProtocolMessage message) {}
        @Override public void onMessageSentSuccess(String messageId) {}
        @Override public void onErrorReceived(String errorCode, String errorMessage) {}
        @Override public void onConnectionLost(Throwable cause) {}
    }
}
