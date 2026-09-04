package vn.edu.ut.udm08.client.network.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-053: ConversationEventBus Unit Tests")
class ConversationEventBusTest {

    private ConversationEventBus eventBus;

    @BeforeEach
    void setUp() {
        // Khởi tạo EventBus với chế độ chạy trực tiếp (synchronous) cho Unit Test
        eventBus = new ConversationEventBus(false);
    }

    private ProtocolMessage createTestMessage(String id, String content) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = id;
        msg.content = content;
        return msg;
    }

    @Test
    @DisplayName("TC_01: Đăng ký và nhận sự kiện đúng convId của cuộc hội thoại")
    void testRegisterAndPostToSpecificConversation() {
        List<ChatEvent> receivedEvents = new ArrayList<>();
        eventBus.register("conv-101", receivedEvents::add);

        MessageReceivedEvent event = new MessageReceivedEvent("conv-101", createTestMessage("m1", "Hello"));
        eventBus.post("conv-101", event);

        assertEquals(1, receivedEvents.size());
        assertEquals("conv-101", receivedEvents.get(0).getConvId());
        assertTrue(receivedEvents.get(0) instanceof MessageReceivedEvent);
        assertEquals("m1", ((MessageReceivedEvent) receivedEvents.get(0)).getMessage().messageId);
    }

    @Test
    @DisplayName("TC_02: Cô lập sự kiện giữa các cuộc hội thoại (Event Isolation)")
    void testEventIsolationBetweenConversations() {
        List<ChatEvent> convAEvents = new ArrayList<>();
        List<ChatEvent> convBEvents = new ArrayList<>();

        eventBus.register("conv-A", convAEvents::add);
        eventBus.register("conv-B", convBEvents::add);

        // Phát event cho conv-A
        eventBus.post("conv-A", new MessageReceivedEvent("conv-A", createTestMessage("mA", "To A")));

        assertEquals(1, convAEvents.size());
        assertEquals(0, convBEvents.size(), "conv-B không được nhận sự kiện của conv-A");

        // Phát event cho conv-B
        eventBus.post("conv-B", new UserTypingEvent("conv-B", "Bob", true));

        assertEquals(1, convAEvents.size());
        assertEquals(1, convBEvents.size());
    }

    @Test
    @DisplayName("TC_03: Global Listener nhận được tất cả các sự kiện từ mọi hội thoại và sự kiện hệ thống")
    void testGlobalListenerReceivesAllEvents() {
        List<ChatEvent> globalEvents = new ArrayList<>();
        eventBus.registerGlobal(globalEvents::add);

        // 1. Event của conv-1
        eventBus.post("conv-1", new MessageReceivedEvent("conv-1", createTestMessage("m1", "Msg 1")));
        // 2. Event của conv-2
        eventBus.post("conv-2", new MessageSentSuccessEvent("conv-2", "m2"));
        // 3. Event toàn cục UserList
        eventBus.postGlobal(new UserListUpdatedEvent(List.of(new UserProfile("Alice", "cat"))));

        assertEquals(3, globalEvents.size(), "Global Listener phải nhận đủ cả 3 sự kiện");
    }

    @Test
    @DisplayName("TC_04: Hủy đăng ký listener (Unregister) của hội thoại thành công")
    void testUnregisterListener() {
        List<ChatEvent> events = new ArrayList<>();
        ConversationEventListener listener = events::add;

        eventBus.register("conv-1", listener);
        assertEquals(1, eventBus.getListenerCount("conv-1"));

        eventBus.post("conv-1", new MessageSentSuccessEvent("conv-1", "m1"));
        assertEquals(1, events.size());

        // Hủy đăng ký
        eventBus.unregister("conv-1", listener);
        assertEquals(0, eventBus.getListenerCount("conv-1"));

        eventBus.post("conv-1", new MessageSentSuccessEvent("conv-1", "m2"));
        assertEquals(1, events.size(), "Sau khi unregister không được nhận thêm event");
    }

    @Test
    @DisplayName("TC_05: Hủy đăng ký Global Listener thành công")
    void testUnregisterGlobalListener() {
        List<ChatEvent> events = new ArrayList<>();
        ConversationEventListener listener = events::add;

        eventBus.registerGlobal(listener);
        assertEquals(1, eventBus.getGlobalListenerCount());

        eventBus.postGlobal(new ErrorEvent("ERR_01", "Lỗi test"));
        assertEquals(1, events.size());

        eventBus.unregisterGlobal(listener);
        assertEquals(0, eventBus.getGlobalListenerCount());

        eventBus.postGlobal(new ErrorEvent("ERR_02", "Lỗi test 2"));
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("TC_06: Dọn dẹp listener của cuộc hội thoại khi đóng tab (clearConversation)")
    void testClearConversationRemovesListeners() {
        eventBus.register("conv-close", e -> {});
        eventBus.register("conv-close", e -> {});
        eventBus.register("conv-keep", e -> {});

        assertEquals(2, eventBus.getListenerCount("conv-close"));
        assertEquals(1, eventBus.getListenerCount("conv-keep"));

        eventBus.clearConversation("conv-close");

        assertEquals(0, eventBus.getListenerCount("conv-close"));
        assertEquals(1, eventBus.getListenerCount("conv-keep"));
    }

    @Test
    @DisplayName("TC_07: Xóa sạch toàn bộ listeners khi đăng xuất (clearAll)")
    void testClearAllRemovesAllListeners() {
        eventBus.register("conv-1", e -> {});
        eventBus.register("conv-2", e -> {});
        eventBus.registerGlobal(e -> {});

        eventBus.clearAll();

        assertEquals(0, eventBus.getActiveConversationCount());
        assertEquals(0, eventBus.getGlobalListenerCount());
    }

    @Test
    @DisplayName("TC_08: Kiểm tra phòng vệ khi đầu vào null hoặc rỗng")
    void testNullInputsSafety() {
        assertDoesNotThrow(() -> {
            eventBus.register(null, e -> {});
            eventBus.register("   ", e -> {});
            eventBus.register("conv-1", null);
            eventBus.registerGlobal(null);

            eventBus.unregister(null, null);
            eventBus.unregisterGlobal(null);

            eventBus.post(null, null);
            eventBus.postGlobal(null);

            eventBus.clearConversation(null);
        });

        assertEquals(0, eventBus.getActiveConversationCount());
        assertEquals(0, eventBus.getGlobalListenerCount());
    }

    @Test
    @DisplayName("TC_09: Đảm bảo một listener bị lỗi ngoại lệ không làm gián đoạn các listener khác")
    void testListenerExceptionIsolation() {
        AtomicInteger successfulListenerCalls = new AtomicInteger(0);

        // Listener 1 ném ngoại lệ
        eventBus.register("conv-test", e -> {
            throw new RuntimeException("Simulated listener exception");
        });

        // Listener 2 thực thi bình thường
        eventBus.register("conv-test", e -> successfulListenerCalls.incrementAndGet());

        assertDoesNotThrow(() -> {
            eventBus.post("conv-test", new MessageSentSuccessEvent("conv-test", "m1"));
        });

        assertEquals(1, successfulListenerCalls.get(), "Listener 2 vẫn phải được gọi dù Listener 1 ném lỗi");
    }

    @Test
    @DisplayName("TC_10: Kiểm thử đa luồng (Concurrency) phát 1000 sự kiện đồng thời")
    void testConcurrentEventPostingThreadSafety() throws InterruptedException {
        int threads = 10;
        int eventsPerThread = 100;
        AtomicInteger totalReceived = new AtomicInteger(0);

        eventBus.register("conv-concurrent", e -> totalReceived.incrementAndGet());

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < eventsPerThread; i++) {
                        eventBus.post("conv-concurrent", new MessageSentSuccessEvent("conv-concurrent", "m-" + i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threads * eventsPerThread, totalReceived.get(), "Phải nhận đủ 1000 sự kiện");
    }
}
