package vn.edu.ut.udm08.client.network.cache;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

/**
 * Service hỗ trợ nghiệp vụ tra cứu tin nhắn gốc từ ConversationCache (ST-052).
 * <p>
 * Phục vụ trực tiếp cho:
 * <ul>
 *   <li>Tính năng Reply: Lấy thông tin tin nhắn gốc và tạo đoạn trích dẫn (Quote Preview) hiển thị trên thanh soạn thảo/bong bóng chat.</li>
 *   <li>Tính năng Forward: Xác định nguồn gốc tin nhắn được chuyển tiếp (Forward Origin Provenance).</li>
 *   <li>Tính năng Điều hướng: Xác định hội thoại chứa tin gốc để hỗ trợ cuộn đến tin nhắn tương ứng (Scroll-to-message).</li>
 * </ul>
 *
 * @author UDM_08 Team
 */
public class OriginalMessageLookupService {

    /** Thông báo mặc định khi tin nhắn gốc không còn trong bộ nhớ đệm hoặc đã bị xóa */
    public static final String MSG_NOT_FOUND_PLACEHOLDER = "[Tin nhắn gốc không tồn tại hoặc đã bị xóa]";

    private final ConversationCache cache;

    /**
     * Khởi tạo service với đối tượng ConversationCache.
     *
     * @param cache Bộ nhớ đệm tin nhắn (không được null).
     * @throws IllegalArgumentException Nếu cache là null.
     */
    public OriginalMessageLookupService(ConversationCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("ConversationCache không được để null");
        }
        this.cache = cache;
    }

    /**
     * Tra cứu đối tượng tin nhắn gốc theo messageId.
     *
     * @param messageId Mã định danh tin nhắn gốc cần tìm.
     * @return Đối tượng {@link ProtocolMessage} nếu tồn tại trong cache, ngược lại trả về null.
     */
    public ProtocolMessage lookup(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        return cache.getMessageById(messageId);
    }

    /**
     * Kiểm tra xem tin nhắn gốc có sẵn trong bộ nhớ đệm cục bộ hay không.
     *
     * @param messageId Mã định danh tin nhắn.
     * @return true nếu tin nhắn có sẵn trong cache, false nếu cần gọi Server tải về.
     */
    public boolean isAvailableLocally(String messageId) {
        return cache.containsMessage(messageId);
    }

    /**
     * Tạo chuỗi trích dẫn (Quote Preview) ngắn gọn từ tin nhắn gốc để hiển thị trên UI Reply.
     * <p>
     * Định dạng: "[Người gửi]: [Nội dung rút gọn]..."
     * Ví dụ: "Alice: Chào bạn, hôm nay có họp không..."
     *
     * @param replyToMessageId Mã tin nhắn đang được trả lời.
     * @param maxChars Độ dài ký tự tối đa cho phần nội dung trích dẫn.
     * @return Chuỗi trích dẫn định dạng sẵn hoặc placeholder nếu tin không có trong cache.
     */
    public String formatQuotePreview(String replyToMessageId, int maxChars) {
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            return MSG_NOT_FOUND_PLACEHOLDER;
        }

        ProtocolMessage original = lookup(replyToMessageId);
        if (original == null) {
            return MSG_NOT_FOUND_PLACEHOLDER;
        }

        String sender = (original.sender != null && !original.sender.isBlank()) ? original.sender : "Người dùng";
        String content = (original.content != null) ? original.content.trim() : "";

        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars) + "...";
        }

        return sender + ": " + content;
    }

    /**
     * Tạo thông tin nguồn gốc chuyển tiếp (Forward Provenance) để hiển thị trên UI Forward.
     * <p>
     * Định dạng: "Chuyển tiếp từ [Người gửi gốc]"
     *
     * @param fwdFromMessageId Mã tin nhắn gốc được chuyển tiếp.
     * @return Chuỗi thông tin người gửi gốc hoặc nhãn mặc định.
     */
    public String formatForwardProvenance(String fwdFromMessageId) {
        if (fwdFromMessageId == null || fwdFromMessageId.isBlank()) {
            return "Chuyển tiếp";
        }

        ProtocolMessage original = lookup(fwdFromMessageId);
        if (original != null && original.sender != null && !original.sender.isBlank()) {
            return "Chuyển tiếp từ " + original.sender;
        }
        return "Chuyển tiếp";
    }

    /**
     * Lấy mã cuộc hội thoại (convId) chứa tin nhắn gốc.
     *
     * @param messageId Mã tin nhắn.
     * @return convId chứa tin nhắn hoặc null nếu không tìm thấy.
     */
    public String getConversationId(String messageId) {
        return cache.getConversationIdByMessageId(messageId);
    }

    /**
     * Lấy đối tượng ConversationCache bên trong.
     *
     * @return cache.
     */
    public ConversationCache getCache() {
        return cache;
    }
}
