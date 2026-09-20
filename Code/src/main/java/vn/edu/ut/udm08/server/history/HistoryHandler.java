package vn.edu.ut.udm08.server.history;

import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.MessagePagedResult;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

/**
 * Xử lý API lịch sử tin nhắn có phân trang và kiểm tra quyền (ST-113 / Issue #161).
 */
public class HistoryHandler {

    private final ConversationDao conversationDao;

    public HistoryHandler(ConversationDao conversationDao) {
        this.conversationDao = conversationDao;
    }

    /**
     * Xử lý yêu cầu HISTORY_REQUEST từ Client.
     */
    public void handleHistoryRequest(ClientSession session, ProtocolMessage message) {
        try {
            // 1. Kiểm tra phiên làm việc
            if (session == null || !session.isAuthenticated() || session.getUsername() == null) {
                sendError(session, message, "UNAUTHORIZED", "Chưa đăng nhập");
                return;
            }

            if (message == null) {
                return;
            }

            String userId = session.getUsername();
            String convId = message.convId;

            // 2. Kiểm tra đầu vào convId
            if (convId == null || convId.isBlank()) {
                sendError(session, message, "BAD_REQUEST", "Hội thoại (convId) không được để trống");
                return;
            }
            convId = convId.trim();

            // 3. Kiểm tra quyền: User có phải thành viên của convId không?
            if (conversationDao != null) {
                if (!conversationDao.isMember(convId, userId)) {
                    sendError(session, message, "FORBIDDEN", "Bạn không có quyền truy cập lịch sử hội thoại này");
                    return;
                }
            }

            // 4. Xác định limit (mặc định 30, max 100)
            int limit = (message.limit != null && message.limit > 0) ? message.limit : 30;
            if (limit > 100) {
                limit = 100;
            }

            // 5. Truy vấn DB qua ConversationDao
            MessagePagedResult pagedResult;
            try {
                if (conversationDao != null) {
                    pagedResult = conversationDao.getMessages(convId, message.cursor, limit);
                } else {
                    pagedResult = new MessagePagedResult(null, null, false);
                }
            } catch (IllegalArgumentException e) {
                // Cursor không hợp lệ
                sendError(session, message, "BAD_REQUEST", "Cursor không hợp lệ");
                return;
            }

            // 6. Trả về HISTORY_RESPONSE
            ProtocolMessage response = new ProtocolMessage(MessageType.HISTORY_RESPONSE);
            response.requestId = message.requestId;
            response.messageId = message.messageId;
            response.convId = convId;
            response.messages = pagedResult.getMessages();
            response.nextCursor = pagedResult.getNextCursor();
            response.hasMore = pagedResult.isHasMore();
            response.sender = "SERVER";
            response.timestamp = System.currentTimeMillis();

            session.sendMessage(response);

        } catch (Exception e) {
            System.err.println("Lỗi xử lý yêu cầu lịch sử: " + e.getMessage());
            sendError(session, message, "SERVER_ERROR", "Lỗi máy chủ khi tải lịch sử");
        }
    }

    private void sendError(ClientSession session, ProtocolMessage req, String errorCode, String errorMessage) {
        if (session == null) {
            return;
        }
        try {
            ProtocolMessage err = new ProtocolMessage(MessageType.ERROR);
            err.requestId = (req != null) ? req.requestId : null;
            err.messageId = (req != null) ? req.messageId : null;
            err.sender = "SERVER";
            err.errorCode = errorCode;
            err.errorMessage = errorMessage;
            err.timestamp = System.currentTimeMillis();
            session.sendMessage(err);
        } catch (Exception ignored) {
        }
    }
}
