package vn.edu.ut.udm08.client.network.event;

import javafx.application.Platform;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventBus nâng cao phân phối sự kiện mạng Client theo từng hội thoại (convId) (ST-053).
 * <p>
 * Tính năng chính:
 * <ul>
 *   <li>Đăng ký và hủy đăng ký listener theo từng convId riêng biệt (hỗ trợ mở nhiều tab chat cùng lúc).</li>
 *   <li>Hỗ trợ Global Listener cho các sự kiện toàn cục (User List, Mất kết nối, Lỗi hệ thống).</li>
 *   <li>Tự động chuyển tiếp sự kiện lên JavaFX Application Thread an toàn (Platform.runLater).</li>
 *   <li>Không làm nghẽn luồng đọc Socket khi phát sự kiện (Non-blocking Reader Thread).</li>
 *   <li>Cơ chế dọn dẹp listener theo hội thoại để chống rò rỉ bộ nhớ (Memory Leak Prevention).</li>
 * </ul>
 *
 * @author UDM_08 Team
 */
public class ConversationEventBus {

    /** Map lưu trữ danh sách listener theo từng cuộc hội thoại (convId -> List<Listener>) */
    private final Map<String, List<ConversationEventListener>> conversationListeners;

    /** Danh sách các listener lắng nghe toàn cục tất cả sự kiện */
    private final List<ConversationEventListener> globalListeners;

    /** Cờ cấu hình có bắt buộc đẩy qua JavaFX Thread hay không (mặc định = true) */
    private final boolean dispatchOnJavaFXThread;

    /**
     * Khởi tạo EventBus mặc định (tự động chuyển tiếp lên JavaFX Application Thread).
     */
    public ConversationEventBus() {
        this(true);
    }

    /**
     * Khởi tạo EventBus với tùy chọn dispatch JavaFX.
     *
     * @param dispatchOnJavaFXThread true nếu muốn đẩy event lên JavaFX Thread, false để chạy trực tiếp (dùng cho Unit Test).
     */
    public ConversationEventBus(boolean dispatchOnJavaFXThread) {
        this.conversationListeners = new ConcurrentHashMap<>();
        this.globalListeners = new CopyOnWriteArrayList<>();
        this.dispatchOnJavaFXThread = dispatchOnJavaFXThread;
    }

    /**
     * Đăng ký một listener để lắng nghe sự kiện của một cuộc hội thoại cụ thể.
     *
     * @param convId Mã định danh cuộc hội thoại (không được null hoặc rỗng).
     * @param listener Callback nhận sự kiện (không được null).
     */
    public void register(String convId, ConversationEventListener listener) {
        if (convId == null || convId.isBlank() || listener == null) {
            return;
        }
        conversationListeners.computeIfAbsent(convId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Hủy đăng ký listener của một cuộc hội thoại cụ thể.
     *
     * @param convId Mã định danh cuộc hội thoại.
     * @param listener Callback cần hủy.
     */
    public void unregister(String convId, ConversationEventListener listener) {
        if (convId == null || convId.isBlank() || listener == null) {
            return;
        }
        List<ConversationEventListener> list = conversationListeners.get(convId);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                conversationListeners.remove(convId);
            }
        }
    }

    /**
     * Đăng ký một listener toàn cục (nhận tất cả sự kiện của mọi hội thoại và sự kiện hệ thống).
     *
     * @param listener Callback nhận sự kiện toàn cục.
     */
    public void registerGlobal(ConversationEventListener listener) {
        if (listener == null || globalListeners.contains(listener)) {
            return;
        }
        globalListeners.add(listener);
    }

    /**
     * Hủy đăng ký một listener toàn cục.
     *
     * @param listener Callback cần hủy.
     */
    public void unregisterGlobal(ConversationEventListener listener) {
        if (listener == null) {
            return;
        }
        globalListeners.remove(listener);
    }

    /**
     * Phát một sự kiện tới tất cả các listener đã đăng ký cho convId tương ứng và các Global Listener.
     *
     * @param convId Mã cuộc hội thoại phát sinh sự kiện.
     * @param event Đối tượng sự kiện.
     */
    public void post(String convId, ChatEvent event) {
        if (event == null) {
            return;
        }

        Runnable dispatchAction = () -> {
            // 1. Gửi tới các listener riêng của hội thoại
            if (convId != null && !convId.isBlank()) {
                List<ConversationEventListener> listeners = conversationListeners.get(convId);
                if (listeners != null) {
                    for (ConversationEventListener listener : listeners) {
                        try {
                            listener.onEvent(event);
                        } catch (Exception ignored) {
                            // Phòng vệ tránh lỗi từ 1 listener làm ảnh hưởng các listener khác
                        }
                    }
                }
            }

            // 2. Gửi tới các Global Listener
            for (ConversationEventListener globalListener : globalListeners) {
                try {
                    globalListener.onEvent(event);
                } catch (Exception ignored) {}
            }
        };

        executeDispatch(dispatchAction);
    }

    /**
     * Phát một sự kiện toàn cục tới tất cả các Global Listener.
     *
     * @param event Sự kiện toàn cục (UserList, Error, ConnectionLost...).
     */
    public void postGlobal(ChatEvent event) {
        post(null, event);
    }

    /**
     * Thực thi hành động dispatch lên JavaFX Application Thread hoặc chạy trực tiếp.
     */
    private void executeDispatch(Runnable action) {
        if (!dispatchOnJavaFXThread) {
            action.run();
            return;
        }

        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // JavaFX Toolkit chưa khởi động (ví dụ môi trường Test không có JavaFX), fallback chạy trực tiếp
            action.run();
        }
    }

    /**
     * Xóa toàn bộ listener của một cuộc hội thoại (dùng khi người dùng đóng tab hội thoại).
     *
     * @param convId Mã cuộc hội thoại cần dọn dẹp.
     */
    public void clearConversation(String convId) {
        if (convId == null || convId.isBlank()) {
            return;
        }
        conversationListeners.remove(convId);
    }

    /**
     * Xóa sạch toàn bộ listener của tất cả các hội thoại và global (dùng khi đăng xuất).
     */
    public void clearAll() {
        conversationListeners.clear();
        globalListeners.clear();
    }

    /**
     * Lấy số lượng listener đang đăng ký cho một cuộc hội thoại cụ thể.
     *
     * @param convId Mã cuộc hội thoại.
     * @return Số lượng listener.
     */
    public int getListenerCount(String convId) {
        if (convId == null || convId.isBlank()) {
            return 0;
        }
        List<ConversationEventListener> list = conversationListeners.get(convId);
        return list != null ? list.size() : 0;
    }

    /**
     * Lấy số lượng Global Listener hiện đang đăng ký.
     *
     * @return Số lượng global listener.
     */
    public int getGlobalListenerCount() {
        return globalListeners.size();
    }

    /**
     * Lấy tổng số cuộc hội thoại hiện đang có listener đăng ký.
     *
     * @return Số cuộc hội thoại đang được theo dõi.
     */
    public int getActiveConversationCount() {
        return conversationListeners.size();
    }
}
