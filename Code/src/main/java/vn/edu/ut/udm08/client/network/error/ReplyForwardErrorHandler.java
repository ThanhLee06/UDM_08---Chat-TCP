package vn.edu.ut.udm08.client.network.error;

import vn.edu.ut.udm08.client.network.event.ConversationEventBus;
import vn.edu.ut.udm08.client.network.event.ErrorEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lớp xử lý và chuẩn hóa các mã lỗi liên quan đến tính năng Reply và Forward từ Server (ST-054).
 * <p>
 * Tính năng chính:
 * <ul>
 *   <li>Map mã lỗi kỹ thuật từ Server sang thông báo tiếng Việt thân thiện, dễ hiểu cho người dùng.</li>
 *   <li>Nhận diện lỗi Reply (để UI tự động đóng thanh trích dẫn quote khi tin gốc không tồn tại).</li>
 *   <li>Nhận diện lỗi Forward (để UI thông báo người dùng chọn lại hội thoại đích).</li>
 *   <li>Tự động đóng gói và phát {@link ErrorEvent} qua {@link ConversationEventBus} tới UI.</li>
 * </ul>
 *
 * @author UDM_08 Team
 */
public class ReplyForwardErrorHandler {

    // ==================== CÁC MÃ LỖI CHUẨN DÀNH CHO REPLY & FORWARD ====================

    /** Tin gốc trả lời không tồn tại trên Server hoặc đã bị xóa */
    public static final String ERR_REPLY_ORIGINAL_NOT_FOUND = "REPLY_ORIGINAL_NOT_FOUND";

    /** Tin gốc trả lời không thuộc về cuộc hội thoại hiện tại */
    public static final String ERR_REPLY_CONVERSATION_MISMATCH = "REPLY_CONVERSATION_MISMATCH";

    /** Tin nhắn nguồn chuyển tiếp không tồn tại */
    public static final String ERR_FORWARD_SOURCE_NOT_FOUND = "FORWARD_SOURCE_NOT_FOUND";

    /** Không có quyền đọc tin nhắn nguồn để chuyển tiếp */
    public static final String ERR_FORWARD_SOURCE_NO_PERMISSION = "FORWARD_SOURCE_NO_PERMISSION";

    /** Không có quyền gửi tin nhắn vào cuộc hội thoại đích */
    public static final String ERR_FORWARD_TARGET_NO_PERMISSION = "FORWARD_TARGET_NO_PERMISSION";

    /** Nội dung tin nhắn vượt quá giới hạn 5000 ký tự */
    public static final String ERR_MESSAGE_TOO_LONG = "MESSAGE_TOO_LONG";

    /** Cuộc hội thoại đích không tồn tại */
    public static final String ERR_TARGET_NOT_FOUND = "TARGET_NOT_FOUND";

    /** Mất kết nối hoặc lỗi đường truyền mạng */
    public static final String ERR_NETWORK_ERROR = "NETWORK_ERROR";

    /** Lỗi không xác định */
    public static final String ERR_UNKNOWN_ERROR = "UNKNOWN_ERROR";

    // ==================== BẢNG MAP THÔNG BÁO TIẾNG VIỆT THÂN THIỆN ====================

    private static final Map<String, String> ERROR_MESSAGE_MAP = new HashMap<>();

    static {
        ERROR_MESSAGE_MAP.put(ERR_REPLY_ORIGINAL_NOT_FOUND, "Tin nhắn gốc bạn đang trả lời không tồn tại hoặc đã bị xóa.");
        ERROR_MESSAGE_MAP.put(ERR_REPLY_CONVERSATION_MISMATCH, "Tin nhắn gốc không thuộc về cuộc hội thoại này.");
        ERROR_MESSAGE_MAP.put(ERR_FORWARD_SOURCE_NOT_FOUND, "Tin nhắn nguồn cần chuyển tiếp không tồn tại.");
        ERROR_MESSAGE_MAP.put(ERR_FORWARD_SOURCE_NO_PERMISSION, "Bạn không có quyền xem hoặc chuyển tiếp tin nhắn nguồn này.");
        ERROR_MESSAGE_MAP.put(ERR_FORWARD_TARGET_NO_PERMISSION, "Bạn không có quyền gửi tin nhắn vào cuộc hội thoại đích.");
        ERROR_MESSAGE_MAP.put(ERR_MESSAGE_TOO_LONG, "Nội dung tin nhắn quá dài (tối đa 5000 ký tự).");
        ERROR_MESSAGE_MAP.put(ERR_TARGET_NOT_FOUND, "Không tìm thấy cuộc hội thoại hoặc người nhận chỉ định.");
        ERROR_MESSAGE_MAP.put(ERR_NETWORK_ERROR, "Mất kết nối đến máy chủ. Vui lòng kiểm tra lại đường truyền mạng.");
        ERROR_MESSAGE_MAP.put(ERR_UNKNOWN_ERROR, "Đã xảy ra lỗi không xác định từ máy chủ.");
    }

    private static final Set<String> REPLY_ERRORS = Set.of(
            ERR_REPLY_ORIGINAL_NOT_FOUND,
            ERR_REPLY_CONVERSATION_MISMATCH
    );

    private static final Set<String> FORWARD_ERRORS = Set.of(
            ERR_FORWARD_SOURCE_NOT_FOUND,
            ERR_FORWARD_SOURCE_NO_PERMISSION,
            ERR_FORWARD_TARGET_NO_PERMISSION
    );

    /**
     * Chuyển đổi mã lỗi từ Server sang thông báo tiếng Việt thân thiện với người dùng.
     *
     * @param errorCode Mã lỗi nhận từ gói tin ProtocolMessage.
     * @param serverRawMessage Thông báo lỗi thô từ Server (dùng làm fallback nếu mã lỗi chưa định nghĩa).
     * @return Chuỗi thông báo lỗi tiếng Việt dễ hiểu.
     */
    public String getFriendlyErrorMessage(String errorCode, String serverRawMessage) {
        if (errorCode == null || errorCode.isBlank()) {
            if (serverRawMessage != null && !serverRawMessage.isBlank()) {
                return serverRawMessage;
            }
            return ERROR_MESSAGE_MAP.get(ERR_UNKNOWN_ERROR);
        }

        String mappedMessage = ERROR_MESSAGE_MAP.get(errorCode.trim().toUpperCase());
        if (mappedMessage != null) {
            return mappedMessage;
        }

        // Nếu mã lỗi không có trong từ điển, ưu tiên thông báo từ server
        if (serverRawMessage != null && !serverRawMessage.isBlank()) {
            return serverRawMessage;
        }

        return "Lỗi máy chủ: " + errorCode;
    }

    /**
     * Xử lý lỗi từ Server: Chuyển đổi thông báo và tự động phát {@link ErrorEvent} qua {@link ConversationEventBus}.
     *
     * @param convId Mã cuộc hội thoại phát sinh lỗi (có thể null).
     * @param errorCode Mã lỗi từ Server.
     * @param serverRawMessage Nội dung lỗi thô từ Server.
     * @param eventBus Đối tượng EventBus để phát sự kiện lên UI (có thể null).
     * @return Đối tượng {@link ErrorEvent} vừa được tạo ra.
     */
    public ErrorEvent handleError(String convId, String errorCode, String serverRawMessage, ConversationEventBus eventBus) {
        String friendlyMessage = getFriendlyErrorMessage(errorCode, serverRawMessage);
        ErrorEvent errorEvent = new ErrorEvent(convId, errorCode != null ? errorCode.trim() : ERR_UNKNOWN_ERROR, friendlyMessage);

        if (eventBus != null) {
            if (convId != null && !convId.isBlank()) {
                eventBus.post(convId, errorEvent);
            } else {
                eventBus.postGlobal(errorEvent);
            }
        }

        return errorEvent;
    }

    /**
     * Kiểm tra xem mã lỗi có phải là lỗi liên quan trực tiếp đến thao tác Reply hay không.
     *
     * @param errorCode Mã lỗi.
     * @return true nếu là lỗi Reply, ngược lại false.
     */
    public boolean isReplyError(String errorCode) {
        if (errorCode == null) return false;
        return REPLY_ERRORS.contains(errorCode.trim().toUpperCase());
    }

    /**
     * Kiểm tra xem mã lỗi có phải là lỗi liên quan trực tiếp đến thao tác Forward hay không.
     *
     * @param errorCode Mã lỗi.
     * @return true nếu là lỗi Forward, ngược lại false.
     */
    public boolean isForwardError(String errorCode) {
        if (errorCode == null) return false;
        return FORWARD_ERRORS.contains(errorCode.trim().toUpperCase());
    }

    /**
     * Kiểm tra xem UI có nên tự động đóng thanh trích dẫn Reply (Quote Bar) hay không khi gặp lỗi này.
     *
     * @param errorCode Mã lỗi.
     * @return true nếu cần xóa quote (ví dụ tin gốc đã bị xóa/sai hội thoại).
     */
    public boolean shouldDismissQuote(String errorCode) {
        return isReplyError(errorCode);
    }
}
