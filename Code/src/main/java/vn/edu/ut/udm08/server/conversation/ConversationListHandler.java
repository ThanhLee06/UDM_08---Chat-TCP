package vn.edu.ut.udm08.server.conversation;

import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.Collections;
import java.util.List;

/**
 * Xử lý API tải danh sách tất cả các cuộc trò chuyện của User (ST-114 / Issue #162).
 */
public class ConversationListHandler {

    private final ConversationDao conversationDao;

    public ConversationListHandler(ConversationDao conversationDao) {
        this.conversationDao = conversationDao;
    }

    /**
     * Xử lý yêu cầu CONVERSATION_LIST_REQUEST từ Client.
     */
    public void handleConversationListRequest(ClientSession session, ProtocolMessage message) {
        try {
            // 1. Kiểm tra xác thực phiên làm việc
            if (session == null || !session.isAuthenticated() || session.getUsername() == null) {
                sendError(session, message, "UNAUTHORIZED", "Chưa đăng nhập");
                return;
            }

            if (message == null) {
                return;
            }

            String userId = session.getUsername();

            // Nếu phiên có avatar, đồng bộ avatar người dùng vào DAO
            if (conversationDao != null && session.getAvatarId() != null) {
                conversationDao.setUserAvatar(userId, session.getAvatarId());
            }

            // 2. Lấy danh sách Inbox từ ConversationDao
            List<ConversationSummary> inbox;
            if (conversationDao != null) {
                inbox = conversationDao.getInboxForUser(userId);
            } else {
                inbox = Collections.emptyList();
            }

            // 3. Trả về gói tin CONVERSATION_LIST_RESPONSE
            ProtocolMessage response = new ProtocolMessage(MessageType.CONVERSATION_LIST_RESPONSE);
            response.requestId = message.requestId;
            response.messageId = message.messageId;
            response.conversations = inbox;
            response.sender = "SERVER";
            response.timestamp = System.currentTimeMillis();

            session.sendMessage(response);

        } catch (Exception e) {
            System.err.println("Lỗi xử lý tải danh sách hội thoại: " + e.getMessage());
            sendError(session, message, "SERVER_ERROR", "Lỗi máy chủ khi tải danh sách hội thoại");
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
