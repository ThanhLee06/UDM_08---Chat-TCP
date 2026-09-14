package vn.edu.ut.udm08.server.routing;

import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.ForwardMessageHandler;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryMessageDao;
import vn.edu.ut.udm08.server.room.MessageDao;
import vn.edu.ut.udm08.server.room.Transaction;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.Collections;
import java.util.List;

// Lớp định tuyến tin nhắn giữa Client và Server
public class MessageRouter {
    private final OnlineUserRegistry registry;
    private final ForwardMessageHandler forwardHandler;
    private final MessageDao messageDao;
    private final ConversationDao conversationDao;
    private final Transaction transaction;

    public MessageRouter(OnlineUserRegistry registry) {
        this(registry, new InMemoryMessageDao(), new InMemoryConversationDao());
    }

    public MessageRouter(OnlineUserRegistry registry, MessageDao messageDao) {
        this(registry, messageDao, new InMemoryConversationDao());
    }

    public MessageRouter(OnlineUserRegistry registry, MessageDao messageDao, ConversationDao conversationDao) {
        this(registry, messageDao, conversationDao, new Transaction(messageDao, conversationDao));
    }

    public MessageRouter(OnlineUserRegistry registry, MessageDao messageDao, ConversationDao conversationDao, Transaction transaction) {
        this.registry = registry;
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
        this.transaction = transaction != null ? transaction : new Transaction(messageDao, conversationDao);
        this.forwardHandler = new ForwardMessageHandler(registry, messageDao);
    }

    public MessageRouter(OnlineUserRegistry registry, ForwardMessageHandler forwardHandler) {
        this.registry = registry;
        this.messageDao = new InMemoryMessageDao();
        this.conversationDao = new InMemoryConversationDao();
        this.transaction = new Transaction(this.messageDao, this.conversationDao);
        this.forwardHandler = forwardHandler;
    }

    public ConversationDao getConversationDao() {
        return conversationDao;
    }

    public MessageDao getMessageDao() {
        return messageDao;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void handleForwardMessage(ClientSession senderSession, ProtocolMessage msg) {
        if (forwardHandler != null) {
            forwardHandler.handleForwardMessage(senderSession, msg);
        }
    }

    // Xử lý định tuyến tin nhắn CHAT từ người gửi đến người nhận
    public void handleChatMessage(ClientSession senderSession, ProtocolMessage msg) {
        try {
            // 0. Kiểm tra gói tin null
            if (msg == null) {
                sendErrorMessage(senderSession, null, "INVALID_MESSAGE", "Bản tin không hợp lệ");
                return;
            }

            // 1. Kiểm tra phiên làm việc người gửi
            if (senderSession == null || senderSession.getUsername() == null) {
                sendErrorMessage(senderSession, msg.messageId, "UNAUTHORIZED", "Chưa đăng nhập");
                return;
            }

            // 2. Xác thực Sender: Lấy senderId từ Session (không tin Client truyền lên)
            String senderId = senderSession.getUsername();
            if (msg.sender == null || !msg.sender.equals(senderId)) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_SENDER", "Người gửi không khớp với phiên làm việc");
                return;
            }

            // 3. Kiểm tra người nhận (target)
            if (msg.target == null || msg.target.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_TARGET", "Người nhận không được để trống");
                return;
            }

            // 4. Kiểm tra nội dung tin nhắn
            if (msg.content == null || msg.content.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_CONTENT", "Nội dung tin nhắn không được để trống");
                return;
            }
            if (msg.content.length() > 5000) {
                sendErrorMessage(senderSession, msg.messageId, "CONTENT_TOO_LONG", "Nội dung tin nhắn quá dài (tối đa 5000 ký tự)");
                return;
            }

            // 5. Phân quyền: Kiểm tra User có nằm trong cuộc trò chuyện không bằng ConversationDao.isMember(convId, userId)
            String convId = (msg.convId != null && !msg.convId.trim().isEmpty()) ? msg.convId.trim() : msg.target.trim();
            msg.convId = convId;

            if (!conversationDao.getMembers(convId).isEmpty() && !conversationDao.isMember(convId, senderId)) {
                sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN", "Người dùng không thuộc cuộc trò chuyện này");
                return;
            } else if (conversationDao.getMembers(convId).isEmpty()) {
                conversationDao.addMember(convId, senderId);
                conversationDao.addMember(convId, msg.target.trim());
            }

            // 6. Lưu DB trước: Gọi Transaction (ST-103) lưu tin nhắn vào DB, lấy timestamp và sequence chính thức
            ProtocolMessage savedMsg = transaction.saveMessage(msg);
            if (savedMsg == null) {
                sendErrorMessage(senderSession, msg.messageId, "DB_SAVE_FAILED", "Không thể lưu tin nhắn vào cơ sở dữ liệu");
                return;
            }

            // 7. Bắn tin Realtime: Gửi tin nhắn đến các thành viên đang Online trong cuộc trò chuyện
            String targetUsername = msg.target != null ? msg.target.trim() : null;
            ClientSession targetSession = registry.find(targetUsername);

            if (targetSession == null || !targetSession.isConnected()) {
                if (targetSession != null && !targetSession.isConnected()) {
                    registry.remove(targetSession);
                    targetSession.close();
                }
                sendErrorMessage(senderSession, msg.messageId, "USER_OFFLINE", "Người nhận không tồn tại hoặc đã offline");
                return;
            }

            try {
                targetSession.sendMessage(savedMsg);
            } catch (Exception e) {
                // Xử lý rớt mạng người nhận: Nếu gửi Realtime cho người nhận bị lỗi do rớt mạng, không báo lỗi cho người gửi.
                System.err.println("Gửi tin nhắn realtime cho người nhận thất bại (rớt mạng): " + e.getMessage());
                if (!targetSession.isConnected()) {
                    registry.remove(targetSession);
                    targetSession.close();
                }
            }

            // 8. Phản hồi cho người gửi: Trả về gói tin xác nhận (type = "MESSAGE_ACK") kèm messageId, timestamp chính thức và trạng thái SENT
            ProtocolMessage ackMsg = new ProtocolMessage(MessageType.MESSAGE_ACK);
            ackMsg.messageId = savedMsg.messageId;
            ackMsg.convId = savedMsg.convId;
            ackMsg.sender = "SERVER";
            ackMsg.target = senderId;
            ackMsg.timestamp = savedMsg.timestamp;
            ackMsg.sequence = savedMsg.sequence;
            ackMsg.status = "SENT";

            try {
                senderSession.sendMessage(ackMsg);
            } catch (Exception e) {
                System.err.println("Khong the gui MESSAGE_ACK ve cho sender (da ngat ket noi): " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Lỗi hệ thống định tuyến tin nhắn: " + e.getMessage());
            sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "ERROR", "Lỗi hệ thống định tuyến");
        }
    }

    // Xử lý API lấy lịch sử tin nhắn theo cursor (ST-094 & ST-102)
    public void handleGetHistoryMessage(ClientSession session, ProtocolMessage request) {
        try {
            if (request == null) {
                sendErrorMessage(session, null, "INVALID_MESSAGE", "Bản tin không hợp lệ");
                return;
            }

            if (session == null || session.getUsername() == null) {
                sendErrorMessage(session, request.requestId, "UNAUTHORIZED", "Chưa đăng nhập");
                return;
            }

            String userId = session.getUsername();
            String convId = request.convId;
            if (convId == null || convId.trim().isEmpty()) {
                sendErrorMessage(session, request.requestId, "INVALID_TARGET", "Mã hội thoại không được để trống");
                return;
            }

            // Kiểm tra quyền: User có thuộc cuộc trò chuyện này không
            if (!conversationDao.isMember(convId, userId)) {
                sendErrorMessage(session, request.requestId, "FORBIDDEN", "Người dùng không có quyền truy cập lịch sử cuộc trò chuyện này");
                return;
            }

            // Áp dụng limit (mặc định 30, tối đa 100)
            int effectiveLimit = (request.limit != null && request.limit > 0) ? request.limit : 30;
            if (effectiveLimit > 100) {
                effectiveLimit = 100;
            }

            // Truy vấn danh sách tin nhắn từ ConversationDao
            List<ProtocolMessage> historyMessages;
            try {
                historyMessages = conversationDao.getMessages(convId, request.cursor, effectiveLimit);
            } catch (IllegalArgumentException e) {
                sendErrorMessage(session, request.requestId, "BAD_REQUEST", "Cursor không hợp lệ");
                return;
            }

            // Tạo bản tin phản hồi GET_HISTORY_OK
            ProtocolMessage response = new ProtocolMessage(MessageType.GET_HISTORY_OK);
            response.requestId = request.requestId;
            response.convId = convId;
            response.history = historyMessages != null ? historyMessages : Collections.emptyList();

            if (response.history.isEmpty()) {
                response.nextCursor = null;
                response.hasMore = false;
            } else {
                // Tin nhắn cũ nhất trong trang ở vị trí index 0 (vì danh sách từ cũ đến mới)
                ProtocolMessage oldestMsgInPage = response.history.get(0);
                response.nextCursor = oldestMsgInPage.sequence != null ? String.valueOf(oldestMsgInPage.sequence) : oldestMsgInPage.messageId;
                response.hasMore = (response.history.size() == effectiveLimit);
            }

            session.sendMessage(response);

        } catch (Exception e) {
            System.err.println("Lỗi xử lý lấy lịch sử tin nhắn: " + e.getMessage());
            sendErrorMessage(session, request != null ? request.requestId : null, "ERROR", "Lỗi hệ thống khi truy vấn lịch sử tin nhắn");
        }
    }

    // Hàm phụ hỗ trợ gửi gói tin ERROR về cho client
    private void sendErrorMessage(ClientSession session, String messageId, String errorCode, String errorMessage) {
        if (session == null) {
            return;
        }
        try {
            ProtocolMessage err = new ProtocolMessage(MessageType.ERROR);
            err.messageId = messageId;
            err.sender = "SERVER";
            err.target = session.getUsername();
            err.errorCode = errorCode;
            err.errorMessage = errorMessage;
            err.timestamp = System.currentTimeMillis();
            session.sendMessage(err);
        } catch (Exception ignored) {
        }
    }
}
