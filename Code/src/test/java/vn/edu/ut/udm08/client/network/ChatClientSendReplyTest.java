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

@DisplayName("ST-050: ChatClient Send Reply Message Unit Tests")
class ChatClientSendReplyTest {

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
    @DisplayName("TC_01: Gửi tin nhắn trả lời (Reply) thành công với đầy đủ replyTo và convId")
    void testSendReplyValid() throws Exception {
        establishConnection("Alice");

        CountDownLatch replyLatch = new CountDownLatch(1);
        AtomicReference<String> receivedJson = new AtomicReference<>();

        Thread readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverSideClientSocket.getInputStream(), StandardCharsets.UTF_8));
                String line = reader.readLine();
                receivedJson.set(line);
                replyLatch.countDown();
            } catch (IOException ignored) {}
        });
        readerThread.start();

        ProtocolMessage sentMsg = client.sendReply("conv-101", "Toi dong y voi y kien nay!", "msg-orig-888");

        assertNotNull(sentMsg);
        assertEquals("conv-101", sentMsg.convId);
        assertEquals("conv-101", sentMsg.target);
        assertEquals("msg-orig-888", sentMsg.replyTo);
        assertEquals("Alice", sentMsg.sender);
        assertEquals("Toi dong y voi y kien nay!", sentMsg.content);

        assertTrue(replyLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(receivedJson.get());

        ProtocolMessage serverReceived = JsonUtil.fromJson(receivedJson.get());
        assertEquals(MessageType.CHAT, serverReceived.type);
        assertEquals("conv-101", serverReceived.convId);
        assertEquals("msg-orig-888", serverReceived.replyTo);
        assertEquals("Alice", serverReceived.sender);
        assertEquals("Toi dong y voi y kien nay!", serverReceived.content);
    }

    @Test
    @DisplayName("TC_02: Chặn gửi tin nhắn trả lời khi Client chưa kết nối đến Server")
    void testSendReplyWhenNotConnected() {
        assertThrows(IOException.class, () -> {
            client.sendReply("conv-101", "Nội dung", "msg-123");
        });
    }

    @Test
    @DisplayName("TC_03: Chặn gửi khi convId là null hoặc khoảng trắng")
    void testSendReplyNullOrBlankConvId() throws Exception {
        establishConnection("Alice");
        assertThrows(IllegalArgumentException.class, () -> client.sendReply(null, "Content", "msg-1"));
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("   ", "Content", "msg-1"));
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("", "Content", "msg-1"));
    }

    @Test
    @DisplayName("TC_04: Chặn gửi khi nội dung tin nhắn là null hoặc khoảng trắng")
    void testSendReplyNullOrBlankContent() throws Exception {
        establishConnection("Alice");
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", null, "msg-1"));
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", "   ", "msg-1"));
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", "", "msg-1"));
    }

    @Test
    @DisplayName("TC_05: Chặn gửi khi replyToMessageId là null hoặc khoảng trắng")
    void testSendReplyNullOrBlankReplyTo() throws Exception {
        establishConnection("Alice");
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", "Content", null));
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", "Content", "   "));
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", "Content", ""));
    }

    @Test
    @DisplayName("TC_06: Chặn gửi tin nhắn trả lời có độ dài vượt quá 5000 ký tự")
    void testSendReplyContentTooLong() throws Exception {
        establishConnection("Alice");
        String longContent = "A".repeat(5001);
        assertThrows(IllegalArgumentException.class, () -> client.sendReply("conv-1", longContent, "msg-1"));
    }

    @Test
    @DisplayName("TC_07: Cho phép gửi tin nhắn trả lời đạt đúng giới hạn biên 5000 ký tự")
    void testSendReplyContentBoundary5000() throws Exception {
        establishConnection("Alice");
        String maxContent = "B".repeat(5000);
        assertDoesNotThrow(() -> client.sendReply("conv-1", maxContent, "msg-1"));
    }

    @Test
    @DisplayName("TC_08: Tự động cắt bỏ khoảng trắng thừa (Trim) ở convId, content và replyTo")
    void testSendReplyAutoTrim() throws Exception {
        establishConnection("Alice");
        ProtocolMessage msg = client.sendReply("  conv-room  ", "  Xin chao ban  ", "  msg-orig-123  ");
        assertEquals("conv-room", msg.convId);
        assertEquals("Xin chao ban", msg.content);
        assertEquals("msg-orig-123", msg.replyTo);
    }

    @Test
    @DisplayName("TC_09: Đối tượng ProtocolMessage trả về có timestamp hợp lệ và messageId UUID không rỗng")
    void testSendReplyReturnsValidProtocolMessage() throws Exception {
        establishConnection("Alice");
        ProtocolMessage msg = client.sendReply("conv-1", "Reply text", "orig-1");
        assertNotNull(msg.messageId);
        assertFalse(msg.messageId.isBlank());
        assertTrue(msg.timestamp > 0);
        assertEquals(MessageType.CHAT, msg.type);
    }

    @Test
    @DisplayName("TC_10: Gửi tuần tự nhiều tin nhắn trả lời liên tiếp thành công")
    void testSendMultipleRepliesInSequence() throws Exception {
        establishConnection("Alice");
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            assertDoesNotThrow(() -> client.sendReply("conv-seq", "Reply " + idx, "orig-" + idx));
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
