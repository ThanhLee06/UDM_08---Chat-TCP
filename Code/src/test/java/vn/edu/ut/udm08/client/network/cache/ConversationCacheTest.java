package vn.edu.ut.udm08.client.network.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-049: ConversationCache Unit Tests")
class ConversationCacheTest {

    private ConversationCache cache;

    @BeforeEach
    void setUp() {
        cache = new ConversationCache(10); // Cấu hình giới hạn 10 tin nhắn/hội thoại cho các bài test cơ bản
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
    @DisplayName("TC_01: Thêm tin nhắn và lấy danh sách theo convId thành công")
    void testAddAndRetrieveMessagesSuccess() {
        ProtocolMessage msg1 = createMessage("msg-1", "Alice", "Hello Bob");
        ProtocolMessage msg2 = createMessage("msg-2", "Bob", "Hi Alice");

        assertTrue(cache.addMessage("conv-101", msg1));
        assertTrue(cache.addMessage("conv-101", msg2));

        List<ProtocolMessage> messages = cache.getMessages("conv-101");
        assertEquals(2, messages.size());
        assertEquals("msg-1", messages.get(0).messageId);
        assertEquals("msg-2", messages.get(1).messageId);
        assertEquals(2, cache.getMessageCount("conv-101"));
        assertTrue(cache.hasConversation("conv-101"));
    }

    @Test
    @DisplayName("TC_02: Thêm hàng loạt tin nhắn lịch sử (Batch Add) thành công")
    void testAddMessagesBatch() {
        List<ProtocolMessage> batch = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            batch.add(createMessage("msg-" + i, "User" + i, "Content " + i));
        }

        int added = cache.addMessages("conv-batch", batch);
        assertEquals(5, added);
        assertEquals(5, cache.getMessageCount("conv-batch"));
        assertEquals(5, cache.getMessages("conv-batch").size());
    }

    @Test
    @DisplayName("TC_03: Cơ chế FIFO Eviction khi vượt quá dung lượng tối đa (maxMessagesPerConversation)")
    void testFifoEvictionWhenLimitExceeded() {
        ConversationCache smallCache = new ConversationCache(3);

        smallCache.addMessage("conv-limit", createMessage("msg-1", "Alice", "Msg 1"));
        smallCache.addMessage("conv-limit", createMessage("msg-2", "Alice", "Msg 2"));
        smallCache.addMessage("conv-limit", createMessage("msg-3", "Alice", "Msg 3"));
        assertEquals(3, smallCache.getMessageCount("conv-limit"));

        // Thêm tin thứ 4 -> tin msg-1 cũ nhất phải bị loại bỏ
        smallCache.addMessage("conv-limit", createMessage("msg-4", "Alice", "Msg 4"));
        assertEquals(3, smallCache.getMessageCount("conv-limit"));

        List<ProtocolMessage> list = smallCache.getMessages("conv-limit");
        assertEquals("msg-2", list.get(0).messageId);
        assertEquals("msg-3", list.get(1).messageId);
        assertEquals("msg-4", list.get(2).messageId);

        // Kiểm tra msg-1 đã bị xóa sạch khỏi bảng chỉ mục tra cứu
        assertNull(smallCache.getMessageById("msg-1"), "Tin nhắn cũ nhất phải bị xóa khỏi index khi bị evict");
        assertNotNull(smallCache.getMessageById("msg-4"));
    }

    @Test
    @DisplayName("TC_04: Tra cứu tin nhắn O(1) theo messageId trên nhiều hội thoại")
    void testMessageIndexLookupById() {
        ProtocolMessage msgA = createMessage("msg-alice-1", "Alice", "Alo");
        ProtocolMessage msgB = createMessage("msg-bob-1", "Bob", "Da nghe");

        cache.addMessage("conv-A", msgA);
        cache.addMessage("conv-B", msgB);

        assertEquals(msgA, cache.getMessageById("msg-alice-1"));
        assertEquals(msgB, cache.getMessageById("msg-bob-1"));
        assertNull(cache.getMessageById("msg-nonexistent"));
        assertNull(cache.getMessageById(null));
        assertNull(cache.getMessageById("   "));
    }

    @Test
    @DisplayName("TC_05: Lấy tin nhắn mới nhất trong cuộc hội thoại (getLatestMessage)")
    void testLatestMessage() {
        assertNull(cache.getLatestMessage("conv-empty"));

        cache.addMessage("conv-1", createMessage("msg-1", "A", "First"));
        cache.addMessage("conv-1", createMessage("msg-2", "A", "Second"));

        ProtocolMessage latest = cache.getLatestMessage("conv-1");
        assertNotNull(latest);
        assertEquals("msg-2", latest.messageId);
        assertEquals("Second", latest.content);
    }

    @Test
    @DisplayName("TC_06: Xóa cache của một hội thoại cụ thể (clearConversation)")
    void testClearConversation() {
        cache.addMessage("conv-1", createMessage("msg-1", "A", "Text 1"));
        cache.addMessage("conv-2", createMessage("msg-2", "B", "Text 2"));

        cache.clearConversation("conv-1");

        assertFalse(cache.hasConversation("conv-1"));
        assertEquals(0, cache.getMessageCount("conv-1"));
        assertTrue(cache.getMessages("conv-1").isEmpty());
        assertNull(cache.getMessageById("msg-1"), "Index của conv-1 phải bị xóa");

        // conv-2 vẫn giữ nguyên
        assertTrue(cache.hasConversation("conv-2"));
        assertEquals(1, cache.getMessageCount("conv-2"));
        assertNotNull(cache.getMessageById("msg-2"));
    }

    @Test
    @DisplayName("TC_07: Xóa toàn bộ cache (clearAll)")
    void testClearAll() {
        cache.addMessage("conv-1", createMessage("msg-1", "A", "Text 1"));
        cache.addMessage("conv-2", createMessage("msg-2", "B", "Text 2"));

        cache.clearAll();

        assertEquals(0, cache.getConversationCount());
        assertEquals(0, cache.getTotalMessageCount());
        assertNull(cache.getMessageById("msg-1"));
        assertNull(cache.getMessageById("msg-2"));
    }

    @Test
    @DisplayName("TC_08: Kiểm tra an toàn phòng vệ khi đầu vào null hoặc rỗng")
    void testNullAndEmptyInputsSafety() {
        assertFalse(cache.addMessage(null, createMessage("1", "A", "Text")));
        assertFalse(cache.addMessage("   ", createMessage("1", "A", "Text")));
        assertFalse(cache.addMessage("conv-1", null));

        assertTrue(cache.getMessages(null).isEmpty());
        assertTrue(cache.getMessages("   ").isEmpty());
        assertEquals(0, cache.getMessageCount(null));
        assertFalse(cache.hasConversation(null));

        assertDoesNotThrow(() -> cache.clearConversation(null));
        assertDoesNotThrow(() -> cache.addMessages(null, null));
    }

    @Test
    @DisplayName("TC_09: Khởi tạo với giới hạn dung lượng không hợp lệ ném IllegalArgumentException")
    void testInvalidMaxLimitConstructorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationCache(0));
        assertThrows(IllegalArgumentException.class, () -> new ConversationCache(-10));
    }

    @Test
    @DisplayName("TC_10: Kiểm thử đa luồng (Concurrency Thread-Safety) không bị lỗi và mất dữ liệu")
    void testConcurrentAccessThreadSafety() throws InterruptedException {
        int threads = 10;
        int messagesPerThread = 100;
        ConversationCache concurrentCache = new ConversationCache(2000);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int m = 0; m < messagesPerThread; m++) {
                        String msgId = "msg-t" + threadId + "-" + m;
                        String convId = "conv-" + (threadId % 3); // Ghi đồng thời vào 3 hội thoại chung
                        ProtocolMessage msg = createMessage(msgId, "User" + threadId, "Content " + m);
                        if (concurrentCache.addMessage(convId, msg)) {
                            writeSuccessCount.incrementAndGet();
                        }
                        // Luồng vừa ghi vừa đọc đồng thời
                        concurrentCache.getMessages(convId);
                        concurrentCache.getMessageById(msgId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Đa luồng phải hoàn thành trong 10 giây");
        executor.shutdown();

        assertEquals(threads * messagesPerThread, writeSuccessCount.get());
        assertEquals(threads * messagesPerThread, concurrentCache.getTotalMessageCount());
    }
}