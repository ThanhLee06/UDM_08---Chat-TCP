package vn.edu.ut.udm08.shared.model;

/**
 * Các hằng số message type dùng chung cho giao thức TCP.
 *
 * <p>Các giá trị String được giữ nguyên để tương thích với
 * các module Client, Server và các ST đã triển khai trước đó.</p>
 */
public final class MessageType {

    private MessageType() {
    }

    // AUTH
    public static final String HELLO = "HELLO";
    public static final String HELLO_OK = "HELLO_OK";
    public static final String LOGOUT = "LOGOUT";
    public static final String LOGOUT_OK = "LOGOUT_OK";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";

    // CHAT
    public static final String CHAT = "CHAT";
    public static final String CHAT_OK = "CHAT_OK";
    public static final String HISTORY_REQUEST = "HISTORY_REQUEST";
    public static final String HISTORY_RESPONSE = "HISTORY_RESPONSE";

    // ROOM
    public static final String CONVERSATION_LIST_REQUEST =
            "CONVERSATION_LIST_REQUEST";
    public static final String CONVERSATION_LIST_RESPONSE =
            "CONVERSATION_LIST_RESPONSE";
    public static final String OPEN_DM_REQUEST = "OPEN_DM_REQUEST";
    public static final String OPEN_DM_RESPONSE = "OPEN_DM_RESPONSE";

    // USER
    public static final String USER_LIST = "USER_LIST";
    public static final String USER_SEARCH_REQUEST = "USER_SEARCH_REQUEST";
    public static final String USER_SEARCH_RESPONSE = "USER_SEARCH_RESPONSE";

    // SYSTEM
    public static final String DISCONNECT = "DISCONNECT";
    public static final String ERROR = "ERROR";
}
