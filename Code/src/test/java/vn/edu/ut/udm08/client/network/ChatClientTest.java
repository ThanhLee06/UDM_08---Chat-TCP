package vn.edu.ut.udm08.client.network;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-09: ChatClient Structure and Socket Management Tests")
class ChatClientTest {

    private ChatClient client;

    @BeforeEach
    void setUp() {
        client = new ChatClient();
    }

    @Test
    @DisplayName("TC_01: Kiểm tra trạng thái khởi tạo ban đầu của ChatClient")
    void testInitialState() {
        assertNotNull(client, "Đối tượng ChatClient phải được khởi tạo thành công");
        assertFalse(client.isConnected(), "Trạng thái ban đầu isConnected() phải là false");
        assertNull(client.getUsername(), "Username ban đầu phải là null");
        assertNull(client.getAvatarId(), "AvatarId ban đầu phải là null");
    }

    @Test
    @DisplayName("TC_02: Ném IOException khi gọi sendMessage lúc chưa kết nối")
    void testSendMessageWhenNotConnectedThrowsException() {
        IOException exception = assertThrows(IOException.class, () -> {
            client.sendMessage("targetUser", "Xin chao");
        });
        assertTrue(exception.getMessage().contains("Chưa kết nối") || exception.getMessage().contains("kết nối"),
                "Thông báo lỗi phải chỉ rõ trạng thái chưa kết nối");
    }

    @Test
    @DisplayName("TC_03: Gọi disconnect khi chưa kết nối phải an toàn, không ném ngoại lệ")
    void testDisconnectWhenNotConnectedIsSafe() {
        assertDoesNotThrow(() -> client.disconnect(),
                "Gọi disconnect() trên client chưa kết nối không được ném Exception");
        assertFalse(client.isConnected(), "Trạng thái sau khi disconnect vẫn là false");
    }

    @Test
    @DisplayName("TC_04: Gọi disconnect nhiều lần liên tiếp phải an toàn (Idempotent)")
    void testMultipleDisconnectCallsAreSafe() {
        assertDoesNotThrow(() -> {
            client.disconnect();
            client.disconnect();
            client.disconnect();
        }, "Gọi disconnect() nhiều lần liên tiếp không được gây lỗi");
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("TC_05: Gọi sendRawMessage khi chưa kết nối không làm crash ứng dụng")
    void testSendRawMessageWhenNotConnectedDoesNotThrow() {
        assertDoesNotThrow(() -> {
            client.sendRawMessage("{\"type\":\"HELLO\"}");
        }, "Gửi raw message khi writer null không được gây NullPointerException");
    }
}
