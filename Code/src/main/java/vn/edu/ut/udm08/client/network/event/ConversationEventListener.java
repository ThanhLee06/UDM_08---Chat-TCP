package vn.edu.ut.udm08.client.network.event;

/**
 * Listener nhận sự kiện từ {@link ConversationEventBus} (ST-053).
 */
@FunctionalInterface
public interface ConversationEventListener {

    /**
     * Hàm callback được gọi khi có sự kiện phát sinh.
     *
     * @param event Sự kiện mạng.
     */
    void onEvent(ChatEvent event);
}
