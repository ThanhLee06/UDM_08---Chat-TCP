package vn.edu.ut.udm08.client.network.cache;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lớp quản lý bộ nhớ đệm (In-Memory Cache) tin nhắn phía Client theo từng hội thoại (convId).
 * <p>
 * Tính năng chính:
 * <ul>
 *   <li>Lưu trữ tin nhắn theo từng convId riêng biệt.</li>
 *   <li>Tra cứu tin nhắn cực nhanh O(1) theo messageId phục vụ tính năng reply và forward.</li>
 *   <li>Giới hạn dung lượng cache tối đa N tin nhắn gần nhất mỗi hội thoại để tránh tràn bộ nhớ (Memory Leak).</li>
 *   <li>Đảm bảo an toàn đa luồng (Thread-safe) giữa Socket Reader Thread và JavaFX Application Thread thông qua ReadWriteLock.</li>
 * </ul>
 *
 * @author UDM_08 Team
 */
public class ConversationCache {

    /** Số lượng tin nhắn tối đa mặc định được lưu trữ cho mỗi cuộc hội thoại */
    public static final int DEFAULT_MAX_MESSAGES_PER_CONVERSATION = 100;

    /** Giới hạn số lượng tin nhắn tối đa cho mỗi cuộc hội thoại */
    private final int maxMessagesPerConversation;

    /** Bảng băm lưu trữ danh sách tin nhắn: Key = convId, Value = Danh sách tin nhắn theo thứ tự thời gian */
    private final Map<String, LinkedList<ProtocolMessage>> conversationMap;

    /** Bảng băm chỉ mục tra cứu nhanh: Key = messageId, Value = ProtocolMessage */
    private final Map<String, ProtocolMessage> messageIndex;

    /** Khóa Read-Write Lock để đồng bộ hóa đa luồng an toàn và tối ưu hiệu năng đọc */
    private final ReadWriteLock rwLock;
    private final Lock readLock;
    private final Lock writeLock;

    /**
     * Khởi tạo bộ nhớ đệm với dung lượng mặc định (100 tin nhắn mỗi hội thoại).
     */
    public ConversationCache() {
        this(DEFAULT_MAX_MESSAGES_PER_CONVERSATION);
    }

    /**
     * Khởi tạo bộ nhớ đệm với dung lượng tùy chỉnh.
     *
     * @param maxMessagesPerConversation Giới hạn số tin nhắn tối đa cho mỗi cuộc hội thoại (phải > 0).
     * @throws IllegalArgumentException Nếu maxMessagesPerConversation <= 0.
     */
    public ConversationCache(int maxMessagesPerConversation) {
        if (maxMessagesPerConversation <= 0) {
            throw new IllegalArgumentException("Giới hạn tin nhắn tối đa cho mỗi hội thoại phải lớn hơn 0");
        }
        this.maxMessagesPerConversation = maxMessagesPerConversation;
        this.conversationMap = new HashMap<>();
        this.messageIndex = new HashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
    }

    /**
     * Thêm một tin nhắn mới vào bộ nhớ đệm của một hội thoại cụ thể.
     * <p>
     * Nếu số lượng tin nhắn trong hội thoại vượt quá giới hạn {@link #maxMessagesPerConversation},
     * tin nhắn cũ nhất sẽ tự động bị loại bỏ (FIFO Eviction) để giải phóng bộ nhớ.
     *
     * @param convId Mã định danh hội thoại (không được null hoặc rỗng).
     * @param message Đối tượng tin nhắn cần lưu vào cache (không được null).
     * @return true nếu thêm thành công, false nếu đầu vào không hợp lệ.
     */
    public boolean addMessage(String convId, ProtocolMessage message) {
        if (convId == null || convId.isBlank() || message == null) {
            return false;
        }

        writeLock.lock();
        try {
            LinkedList<ProtocolMessage> list = conversationMap.computeIfAbsent(convId, k -> new LinkedList<>());

            // Thêm tin nhắn mới vào cuối danh sách
            list.addLast(message);

            // Cập nhật chỉ mục messageId nếu tin nhắn có messageId
            if (message.messageId != null && !message.messageId.isBlank()) {
                messageIndex.put(message.messageId, message);
            }

            // Kiểm tra và loại bỏ tin nhắn cũ nhất nếu vượt quá dung lượng tối đa
            while (list.size() > maxMessagesPerConversation) {
                ProtocolMessage oldest = list.removeFirst();
                if (oldest != null && oldest.messageId != null) {
                    // Xóa khỏi bảng chỉ mục để tránh rò rỉ bộ nhớ
                    messageIndex.remove(oldest.messageId);
                }
            }
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Thêm hàng loạt tin nhắn vào bộ nhớ đệm của hội thoại (dùng khi nạp lịch sử từ Server).
     *
     * @param convId Mã định danh cuộc hội thoại.
     * @param messages Danh sách tin nhắn cần nạp.
     * @return Số lượng tin nhắn thực tế đã được nạp vào cache.
     */
    public int addMessages(String convId, List<ProtocolMessage> messages) {
        if (convId == null || convId.isBlank() || messages == null || messages.isEmpty()) {
            return 0;
        }

        writeLock.lock();
        try {
            int addedCount = 0;
            for (ProtocolMessage msg : messages) {
                if (msg != null) {
                    addMessageInternal(convId, msg);
                    addedCount++;
                }
            }
            return addedCount;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Phương thức nội bộ thêm tin nhắn (yêu cầu luồng đang nắm giữ writeLock).
     */
    private void addMessageInternal(String convId, ProtocolMessage message) {
        LinkedList<ProtocolMessage> list = conversationMap.computeIfAbsent(convId, k -> new LinkedList<>());
        list.addLast(message);

        if (message.messageId != null && !message.messageId.isBlank()) {
            messageIndex.put(message.messageId, message);
        }

        while (list.size() > maxMessagesPerConversation) {
            ProtocolMessage oldest = list.removeFirst();
            if (oldest != null && oldest.messageId != null) {
                messageIndex.remove(oldest.messageId);
            }
        }
    }

    /**
     * Lấy toàn bộ danh sách tin nhắn hiện có trong bộ nhớ đệm của một cuộc hội thoại.
     * <p>
     * Trả về bản sao danh sách không thể sửa đổi (Unmodifiable List) để đảm bảo luồng UI
     * không làm ảnh hưởng đến dữ liệu gốc bên trong cache.
     *
     * @param convId Mã định danh cuộc hội thoại.
     * @return Danh sách tin nhắn theo thứ tự thời gian (hoặc danh sách rỗng nếu hội thoại chưa có tin nhắn).
     */
    public List<ProtocolMessage> getMessages(String convId) {
        if (convId == null || convId.isBlank()) {
            return Collections.emptyList();
        }

        readLock.lock();
        try {
            LinkedList<ProtocolMessage> list = conversationMap.get(convId);
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            // Trả về bản sao độc lập để an toàn luồng
            return Collections.unmodifiableList(new ArrayList<>(list));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Tra cứu nhanh tin nhắn theo mã messageId từ tất cả các cuộc hội thoại đang được cache.
     * <p>
     * Thao tác tra cứu đạt độ phức tạp O(1) nhờ bảng băm chỉ mục {@link #messageIndex}.
     *
     * @param messageId Mã định danh tin nhắn cần tìm.
     * @return Đối tượng {@link ProtocolMessage} nếu tìm thấy, ngược lại trả về null.
     */
    public ProtocolMessage getMessageById(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }

        readLock.lock();
        try {
            return messageIndex.get(messageId);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Lấy tin nhắn mới nhất vừa nhận được trong một cuộc hội thoại.
     *
     * @param convId Mã định danh cuộc hội thoại.
     * @return Đối tượng {@link ProtocolMessage} mới nhất hoặc null nếu hội thoại rỗng.
     */
    public ProtocolMessage getLatestMessage(String convId) {
        if (convId == null || convId.isBlank()) {
            return null;
        }

        readLock.lock();
        try {
            LinkedList<ProtocolMessage> list = conversationMap.get(convId);
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list.getLast();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Đếm số lượng tin nhắn đang được lưu trong cache của một cuộc hội thoại.
     *
     * @param convId Mã định danh cuộc hội thoại.
     * @return Số lượng tin nhắn (0 nếu hội thoại chưa tồn tại).
     */
    public int getMessageCount(String convId) {
        if (convId == null || convId.isBlank()) {
            return 0;
        }

        readLock.lock();
        try {
            LinkedList<ProtocolMessage> list = conversationMap.get(convId);
            return list != null ? list.size() : 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Kiểm tra xem một cuộc hội thoại có đang tồn tại trong cache hay không.
     *
     * @param convId Mã định danh cuộc hội thoại.
     * @return true nếu hội thoại đã có tin nhắn trong cache, ngược lại false.
     */
    public boolean hasConversation(String convId) {
        if (convId == null || convId.isBlank()) {
            return false;
        }

        readLock.lock();
        try {
            LinkedList<ProtocolMessage> list = conversationMap.get(convId);
            return list != null && !list.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Xóa toàn bộ tin nhắn của một cuộc hội thoại cụ thể khỏi cache.
     *
     * @param convId Mã định danh cuộc hội thoại cần xóa.
     */
    public void clearConversation(String convId) {
        if (convId == null || convId.isBlank()) {
            return;
        }

        writeLock.lock();
        try {
            LinkedList<ProtocolMessage> list = conversationMap.remove(convId);
            if (list != null) {
                for (ProtocolMessage msg : list) {
                    if (msg != null && msg.messageId != null) {
                        messageIndex.remove(msg.messageId);
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Xóa toàn bộ bộ nhớ đệm của tất cả các cuộc hội thoại (dùng khi đăng xuất hoặc ngắt kết nối).
     */
    public void clearAll() {
        writeLock.lock();
        try {
            conversationMap.clear();
            messageIndex.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Lấy tổng số lượng tin nhắn đang được lưu trữ trên tất cả các hội thoại.
     *
     * @return Tổng số tin nhắn.
     */
    public int getTotalMessageCount() {
        readLock.lock();
        try {
            return messageIndex.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Lấy tổng số lượng cuộc hội thoại đang có trong bộ nhớ đệm.
     *
     * @return Số lượng cuộc hội thoại.
     */
    public int getConversationCount() {
        readLock.lock();
        try {
            return conversationMap.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Lấy cấu hình giới hạn số lượng tin nhắn tối đa cho mỗi cuộc hội thoại.
     *
     * @return Giới hạn tin nhắn.
     */
    public int getMaxMessagesPerConversation() {
        return maxMessagesPerConversation;
    }
}