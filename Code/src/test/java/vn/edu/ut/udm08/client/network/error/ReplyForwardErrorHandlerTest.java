package vn.edu.ut.udm08.client.network.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.client.network.event.ChatEvent;
import vn.edu.ut.udm08.client.network.event.ConversationEventBus;
import vn.edu.ut.udm08.client.network.event.ErrorEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-054: Reply and Forward Server Error Handler Unit Tests")
class ReplyForwardErrorHandlerTest {

    private ReplyForwardErrorHandler errorHandler;
    private ConversationEventBus eventBus;

    @BeforeEach
    void setUp() {
        errorHandler = new ReplyForwardErrorHandler();
        eventBus = new ConversationEventBus(false); // Synchronous cho test
    }

    @Test
    @DisplayName("TC_01: Map mã lỗi REPLY_ORIGINAL_NOT_FOUND sang tiếng Việt thân thiện")
    void testMapReplyOriginalNotFound() {
        String msg = errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_REPLY_ORIGINAL_NOT_FOUND, null);
        assertEquals("Tin nhắn gốc bạn đang trả lời không tồn tại hoặc đã bị xóa.", msg);
    }

    @Test
    @DisplayName("TC_02: Map mã lỗi REPLY_CONVERSATION_MISMATCH sang tiếng Việt")
    void testMapReplyConversationMismatch() {
        String msg = errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_REPLY_CONVERSATION_MISMATCH, null);
        assertEquals("Tin nhắn gốc không thuộc về cuộc hội thoại này.", msg);
    }

    @Test
    @DisplayName("TC_03: Map đầy đủ các mã lỗi chuyển tiếp FORWARD sang thông báo tiếng Việt")
    void testMapForwardErrors() {
        assertEquals("Tin nhắn nguồn cần chuyển tiếp không tồn tại.",
                errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_FORWARD_SOURCE_NOT_FOUND, null));

        assertEquals("Bạn không có quyền xem hoặc chuyển tiếp tin nhắn nguồn này.",
                errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_FORWARD_SOURCE_NO_PERMISSION, null));

        assertEquals("Bạn không có quyền gửi tin nhắn vào cuộc hội thoại đích.",
                errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_FORWARD_TARGET_NO_PERMISSION, null));
    }

    @Test
    @DisplayName("TC_04: Map các mã lỗi mạng và giới hạn độ dài tin nhắn")
    void testMapNetworkAndMessageTooLongErrors() {
        assertEquals("Nội dung tin nhắn quá dài (tối đa 5000 ký tự).",
                errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_MESSAGE_TOO_LONG, null));

        assertEquals("Mất kết nối đến máy chủ. Vui lòng kiểm tra lại đường truyền mạng.",
                errorHandler.getFriendlyErrorMessage(ReplyForwardErrorHandler.ERR_NETWORK_ERROR, null));
    }

    @Test
    @DisplayName("TC_05: Fallback thông báo khi mã lỗi không xác định hoặc null")
    void testFallbackWhenUnknownErrorCode() {
        // Có raw message từ server
        assertEquals("Custom server text",
                errorHandler.getFriendlyErrorMessage("CUSTOM_ERR_999", "Custom server text"));

        // Không có raw message
        assertEquals("Lỗi máy chủ: CUSTOM_ERR_999",
                errorHandler.getFriendlyErrorMessage("CUSTOM_ERR_999", null));

        // Mã lỗi null
        assertEquals("Đã xảy ra lỗi không xác định từ máy chủ.",
                errorHandler.getFriendlyErrorMessage(null, null));
    }

    @Test
    @DisplayName("TC_06: Kiểm tra nhận diện chính xác các mã lỗi Reply (isReplyError)")
    void testIsReplyError() {
        assertTrue(errorHandler.isReplyError(ReplyForwardErrorHandler.ERR_REPLY_ORIGINAL_NOT_FOUND));
        assertTrue(errorHandler.isReplyError(ReplyForwardErrorHandler.ERR_REPLY_CONVERSATION_MISMATCH));
        assertFalse(errorHandler.isReplyError(ReplyForwardErrorHandler.ERR_FORWARD_SOURCE_NOT_FOUND));
        assertFalse(errorHandler.isReplyError(null));
    }

    @Test
    @DisplayName("TC_07: Kiểm tra nhận diện chính xác các mã lỗi Forward (isForwardError)")
    void testIsForwardError() {
        assertTrue(errorHandler.isForwardError(ReplyForwardErrorHandler.ERR_FORWARD_SOURCE_NOT_FOUND));
        assertTrue(errorHandler.isForwardError(ReplyForwardErrorHandler.ERR_FORWARD_SOURCE_NO_PERMISSION));
        assertTrue(errorHandler.isForwardError(ReplyForwardErrorHandler.ERR_FORWARD_TARGET_NO_PERMISSION));
        assertFalse(errorHandler.isForwardError(ReplyForwardErrorHandler.ERR_REPLY_ORIGINAL_NOT_FOUND));
        assertFalse(errorHandler.isForwardError(null));
    }

    @Test
    @DisplayName("TC_08: Kiểm tra cờ shouldDismissQuote yêu cầu UI đóng quote preview khi gặp lỗi reply")
    void testShouldDismissQuote() {
        assertTrue(errorHandler.shouldDismissQuote(ReplyForwardErrorHandler.ERR_REPLY_ORIGINAL_NOT_FOUND));
        assertTrue(errorHandler.shouldDismissQuote(ReplyForwardErrorHandler.ERR_REPLY_CONVERSATION_MISMATCH));
        assertFalse(errorHandler.shouldDismissQuote(ReplyForwardErrorHandler.ERR_FORWARD_SOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("TC_09: handleError tự động map thông báo tiếng Việt và phát ErrorEvent qua ConversationEventBus")
    void testHandleErrorDispatchesErrorEventToConvEventBus() {
        List<ChatEvent> receivedEvents = new ArrayList<>();
        eventBus.register("conv-101", receivedEvents::add);

        ErrorEvent event = errorHandler.handleError("conv-101",
                ReplyForwardErrorHandler.ERR_REPLY_ORIGINAL_NOT_FOUND, null, eventBus);

        assertNotNull(event);
        assertEquals("conv-101", event.getConvId());
        assertEquals(ReplyForwardErrorHandler.ERR_REPLY_ORIGINAL_NOT_FOUND, event.getErrorCode());
        assertEquals("Tin nhắn gốc bạn đang trả lời không tồn tại hoặc đã bị xóa.", event.getErrorMessage());

        assertEquals(1, receivedEvents.size());
        assertTrue(receivedEvents.get(0) instanceof ErrorEvent);
        assertEquals(event, receivedEvents.get(0));
    }

    @Test
    @DisplayName("TC_10: handleError khi convId là null tự động phát qua postGlobal")
    void testHandleErrorWithoutConvIdDispatchesGlobalEvent() {
        List<ChatEvent> globalEvents = new ArrayList<>();
        eventBus.registerGlobal(globalEvents::add);

        ErrorEvent event = errorHandler.handleError(null,
                ReplyForwardErrorHandler.ERR_NETWORK_ERROR, null, eventBus);

        assertNotNull(event);
        assertNull(event.getConvId());
        assertEquals(1, globalEvents.size());
        assertEquals(event, globalEvents.get(0));
    }
}
