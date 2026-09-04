package vn.edu.ut.udm08.client.network.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-052: Original Message Lookup from Cache Unit Tests")
class OriginalMessageLookupServiceTest {

    private ConversationCache cache;
    private OriginalMessageLookupService lookupService;

    @BeforeEach
    void setUp() {
        cache = new ConversationCache(50);
        lookupService = new OriginalMessageLookupService(cache);
    }

    private ProtocolMessage createMessage(String msgId, String sender, String content) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = msgId;
        msg.sender = sender;
        msg.content = content;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    @Test
    @DisplayName("TC_01: Tra cứu tin nhắn gốc tồn tại trong cache trả về đúng đối tượng")
    void testLookupExistingMessageSuccess() {
        ProtocolMessage original = createMessage("msg-orig-100", "Alice", "Tai lieu bao cao da gui qua mail nhe");
        cache.addMessage("conv-room-1", original);

        ProtocolMessage found = lookupService.lookup("msg-orig-100");
        assertNotNull(found);
        assertEquals("msg-orig-100", found.messageId);
        assertEquals("Alice", found.sender);
        assertEquals("Tai lieu bao cao da gui qua mail nhe", found.content);
        assertTrue(lookupService.isAvailableLocally("msg-orig-100"));
    }

    @Test
    @DisplayName("TC_02: Tra cứu tin nhắn không tồn tại hoặc đã bị xóa trả về null")
    void testLookupNonExistentReturnsNull() {
        assertNull(lookupService.lookup("msg-not-exist"));
        assertFalse(lookupService.isAvailableLocally("msg-not-exist"));
    }

    @Test
    @DisplayName("TC_03: Tra cứu với ID null hoặc khoảng trắng trả về null an toàn")
    void testLookupNullOrBlankIdReturnsNull() {
        assertNull(lookupService.lookup(null));
        assertNull(lookupService.lookup("   "));
        assertNull(lookupService.lookup(""));
    }

    @Test
    @DisplayName("TC_04: Định dạng đoạn trích dẫn Reply (Quote Preview) đầy đủ người gửi và nội dung")
    void testFormatQuotePreviewSuccess() {
        ProtocolMessage msg = createMessage("msg-reply-1", "Bob", "Hom nay di an trua o dau moi nguoi?");
        cache.addMessage("conv-general", msg);

        String quote = lookupService.formatQuotePreview("msg-reply-1", 100);
        assertEquals("Bob: Hom nay di an trua o dau moi nguoi?", quote);
    }

    @Test
    @DisplayName("TC_05: Cắt ngắn nội dung trích dẫn khi vượt quá maxChars và gắn dấu ba chấm")
    void testFormatQuotePreviewTruncation() {
        ProtocolMessage msg = createMessage("msg-long", "Charlie", "Day la noi dung tin nhan rat dai can duoc cat ngan khi hien thi quote");
        cache.addMessage("conv-general", msg);

        String quote = lookupService.formatQuotePreview("msg-long", 20);
        assertEquals("Charlie: Day la noi dung tin ...", quote);
    }

    @Test
    @DisplayName("TC_06: Hiển thị placeholder thông báo khi tin nhắn gốc reply không có trong cache")
    void testFormatQuotePreviewWhenNotFound() {
        String quoteNonExistent = lookupService.formatQuotePreview("msg-deleted", 50);
        assertEquals(OriginalMessageLookupService.MSG_NOT_FOUND_PLACEHOLDER, quoteNonExistent);

        String quoteNull = lookupService.formatQuotePreview(null, 50);
        assertEquals(OriginalMessageLookupService.MSG_NOT_FOUND_PLACEHOLDER, quoteNull);
    }

    @Test
    @DisplayName("TC_07: Định dạng nguồn tin chuyển tiếp (Forward Provenance) chính xác")
    void testFormatForwardProvenance() {
        ProtocolMessage msg = createMessage("msg-fwd-source", "David", "Lich hop chuyen sang 3h chieu");
        cache.addMessage("conv-dev", msg);

        String provenance = lookupService.formatForwardProvenance("msg-fwd-source");
        assertEquals("Chuyển tiếp từ David", provenance);

        // Trường hợp không tìm thấy tin gốc
        assertEquals("Chuyển tiếp", lookupService.formatForwardProvenance("msg-unknown"));
        assertEquals("Chuyển tiếp", lookupService.formatForwardProvenance(null));
    }

    @Test
    @DisplayName("TC_08: Tra cứu ngược convId từ messageId thành công")
    void testGetConversationIdByMessageId() {
        ProtocolMessage msg1 = createMessage("msg-c1", "Alice", "Hello C1");
        ProtocolMessage msg2 = createMessage("msg-c2", "Bob", "Hello C2");

        cache.addMessage("conv-alpha", msg1);
        cache.addMessage("conv-beta", msg2);

        assertEquals("conv-alpha", lookupService.getConversationId("msg-c1"));
        assertEquals("conv-beta", lookupService.getConversationId("msg-c2"));
        assertNull(lookupService.getConversationId("msg-ghost"));
        assertNull(lookupService.getConversationId(null));
    }

    @Test
    @DisplayName("TC_09: Khởi tạo service với cache null ném IllegalArgumentException")
    void testServiceConstructorThrowsOnNullCache() {
        assertThrows(IllegalArgumentException.class, () -> new OriginalMessageLookupService(null));
    }

    @Test
    @DisplayName("TC_10: Đa luồng tra cứu đồng thời (Concurrency Lookup) an toàn 100%")
    void testConcurrentLookupsThreadSafety() throws InterruptedException {
        int totalMessages = 200;
        for (int i = 0; i < totalMessages; i++) {
            cache.addMessage("conv-" + (i % 5), createMessage("msg-test-" + i, "User" + i, "Content " + i));
        }

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successfulLookups = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < totalMessages; i++) {
                        ProtocolMessage msg = lookupService.lookup("msg-test-" + i);
                        if (msg != null && msg.messageId.equals("msg-test-" + i)) {
                            successfulLookups.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threads * totalMessages, successfulLookups.get());
    }
}
