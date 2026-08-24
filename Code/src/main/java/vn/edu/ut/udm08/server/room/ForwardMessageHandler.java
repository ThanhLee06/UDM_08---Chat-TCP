package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.List;
import java.util.UUID;

public class ForwardMessageHandler {

    private final OnlineUserRegistry registry;
    private final MessageDao messageDao;

    public ForwardMessageHandler(OnlineUserRegistry registry, MessageDao messageDao) {
        if (registry == null || messageDao == null) {
            throw new IllegalArgumentException("Registry và MessageDao không được null");
        }
        this.registry = registry;
        this.messageDao = messageDao;
    }

    public void handleForwardMessage(ClientSession senderSession, ProtocolMessage msg) {
        try {
            // 0. Kiem tra goi tin null
            if (msg == null) {
                sendErrorMessage(senderSession, null, "INVALID_MESSAGE", "Bản tin không hợp lệ");
                return;
            }

            // 1. Kiem tra phien lam viec nguoi gui
            if (senderSession == null || senderSession.getUsername() == null) {
                sendErrorMessage(senderSession, msg.messageId, "UNAUTHORIZED", "Chưa đăng nhập");
                return;
            }

            // 2. Kiem tra nguoi gui khop voi session
            if (msg.sender == null || !msg.sender.equals(senderSession.getUsername())) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_SENDER", "Người gửi không khớp với phiên làm việc");
                return;
            }

            // 3. Lay thông tin hoi thoai dich va tin nguon
            String targetConvId = (msg.convId != null && !msg.convId.trim().isEmpty()) ? msg.convId : msg.target;
            if (targetConvId == null || targetConvId.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_TARGET", "Hội thoại đích không được để trống");
                return;
            }

            if (msg.fwdFrom == null || msg.fwdFrom.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_FORWARD_SOURCE", "Tin nhắn nguồn không được để trống");
                return;
            }

            if (msg.content == null || msg.content.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_CONTENT", "Nội dung tin nhắn không được để trống");
                return;
            }

            // 4. Kiem tra tin nguon ton tai trong DB
            ProtocolMessage sourceMsg = messageDao.findById(msg.fwdFrom);
            if (sourceMsg == null) {
                sendErrorMessage(senderSession, msg.messageId, "SOURCE_MSG_NOT_FOUND", "Tin nguồn không tồn tại");
                return;
            }

            // 5. Kiem tra user co quyen doc tin nguon (la thanh vien hoi thoai nguon)
            String sourceConvId = (sourceMsg.convId != null && !sourceMsg.convId.trim().isEmpty()) ? sourceMsg.convId : sourceMsg.target;
            if (!messageDao.isUserInConversation(msg.sender, sourceConvId)) {
                sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN_READ_SOURCE", "Không có quyền đọc tin nguồn");
                return;
            }

            // 6. Kiem tra user co quyen gui vao hoi thoai dich
            if (!messageDao.isUserInConversation(msg.sender, targetConvId)) {
                sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN_SEND_TARGET", "Không có quyền gửi vào hội thoại này");
                return;
            }

            // 7. Tao tin nhan forward moi
            ProtocolMessage newMsg = new ProtocolMessage(MessageType.CHAT);
            newMsg.messageId = (msg.messageId != null && !msg.messageId.trim().isEmpty())
                    ? msg.messageId
                    : UUID.randomUUID().toString();
            newMsg.sender = msg.sender;
            newMsg.target = targetConvId;
            newMsg.convId = targetConvId;
            newMsg.content = msg.content;
            newMsg.fwdFrom = msg.fwdFrom;
            newMsg.forwardOf = msg.fwdFrom;
            newMsg.kind = "forward";
            newMsg.timestamp = System.currentTimeMillis();

            // 8. Luu tin moi vao DB
            messageDao.save(newMsg);

            // 9. Broadcast tin moi toi cac thanh vien trong hoi thoai dich (tru sender nhan CHAT_OK)
            List<String> members = messageDao.getConversationMembers(targetConvId);
            for (String member : members) {
                if (member.equalsIgnoreCase(msg.sender)) {
                    continue;
                }
                ClientSession memberSession = registry.find(member);
                if (memberSession != null && memberSession.isConnected()) {
                    try {
                        memberSession.sendMessage(newMsg);
                    } catch (Exception e) {
                        System.err.println("Không thể gửi tin nhắn forward tới member " + member + ": " + e.getMessage());
                    }
                }
            }

            // 10. Tra response thanh cong (CHAT_OK) cho sender
            ProtocolMessage okMsg = new ProtocolMessage(MessageType.CHAT_OK);
            okMsg.messageId = newMsg.messageId;
            okMsg.sender = "SERVER";
            okMsg.target = msg.sender;
            okMsg.timestamp = System.currentTimeMillis();

            try {
                senderSession.sendMessage(okMsg);
            } catch (Exception e) {
                System.err.println("Không thể gửi CHAT_OK về cho sender: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Lỗi xử lý forward message: " + e.getMessage());
            sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "ERROR", "Lỗi hệ thống khi chuyển tiếp tin nhắn");
        }
    }

    private void sendErrorMessage(ClientSession session, String messageId, String errorCode, String errorMessage) {
        if (session == null) {
            return;
        }
        try {
            ProtocolMessage err = new ProtocolMessage(MessageType.ERROR);
            err.messageId = messageId;
            err.sender = "SERVER";
            err.errorCode = errorCode;
            err.errorMessage = errorMessage;
            err.timestamp = System.currentTimeMillis();
            session.sendMessage(err);
        } catch (Exception ignored) {
        }
    }
}
