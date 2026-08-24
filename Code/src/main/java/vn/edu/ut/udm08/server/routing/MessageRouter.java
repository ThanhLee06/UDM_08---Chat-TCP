package vn.edu.ut.udm08.server.routing;

import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

// Lop dinh tuyen tin nhan giua Client va Server
public class MessageRouter {
    private final OnlineUserRegistry registry;

    public MessageRouter(OnlineUserRegistry registry) {
        this.registry = registry;
    }

    // Xu ly dinh tuyen tin nhan CHAT rieng tu nguoi gui den nguoi nhan
    public void handleChatMessage(ClientSession senderSession, ProtocolMessage msg) {
        try {
            // 0. Kiem tra goi tin null
            if (msg == null) {
                sendErrorMessage(senderSession, null, "INVALID_MESSAGE", "Ban tin khong hop le");
                return;
            }

            // 1. Kiem tra phien lam viec nguoi gui
            if (senderSession == null || senderSession.getUsername() == null) {
                sendErrorMessage(senderSession, msg.messageId, "UNAUTHORIZED", "Chua dang nhap");
                return;
            }

            // 2. Kiem tra nguoi gui (sender) co khop voi session hien tai hay khong
            if (msg.sender == null || !msg.sender.equals(senderSession.getUsername())) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_SENDER", "Nguoi gui khong khop voi phien lam viec");
                return;
            }

            // 3. Kiem tra nguoi nhan (target) co bi rong khong
            if (msg.target == null || msg.target.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_TARGET", "Nguoi nhan khong duoc de trong");
                return;
            }

            // 4. Kiem tra noi dung tin nhan (content) khong duoc rong hoac qua dai (> 5000 ky tu)
            if (msg.content == null || msg.content.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_CONTENT", "Noi dung tin nhan khong duoc de trong");
                return;
            }
            if (msg.content.length() > 5000) {
                sendErrorMessage(senderSession, msg.messageId, "CONTENT_TOO_LONG", "Noi dung tin nhan qua dai (toi da 5000 ky tu)");
                return;
            }

            // 5. Tim ClientSession cua nguoi nhan trong OnlineUserRegistry
            ClientSession targetSession = registry.find(msg.target);
            if (targetSession == null || !targetSession.isConnected()) {
                // Nguoi nhan khong ton tai hoac da offline
                sendErrorMessage(senderSession, msg.messageId, "USER_OFFLINE", "Nguoi nhan khong ton tai hoac da offline");
                return;
            }

            // 6. Chuyen tiep nguyen ven goi tin CHAT sang nguoi nhan
            if (msg.timestamp == null || msg.timestamp <= 0) {
                msg.timestamp = System.currentTimeMillis();
            }
            try {
                targetSession.sendMessage(msg);
            } catch (Exception e) {
                // Dong va gỡ bo targetSession khoi registry neu socket bi hhong
                registry.remove(targetSession);
                targetSession.close();
                sendErrorMessage(senderSession, msg.messageId, "DELIVERY_FAILED", "Khong the gui tin nhan toi nguoi nhan");
                return;
            }

            // 7. Phan hoi goi tin CHAT_OK ve cho nguoi gui xac nhan da toi dich thành công
            ProtocolMessage okMsg = new ProtocolMessage(MessageType.CHAT_OK);
            okMsg.messageId = msg.messageId;
            okMsg.sender = "SERVER";
            okMsg.target = msg.sender;
            okMsg.timestamp = System.currentTimeMillis();

            try {
                senderSession.sendMessage(okMsg);
            } catch (Exception e) {
                // Sender ngắt kết nối ngay sau khi gửi - Bỏ qua an toàn để không ảnh hưởng Server
                System.err.println("Khong the gui CHAT_OK ve cho sender (da ngat ket noi): " + e.getMessage());
            }

        } catch (Exception e) {
            // Try-catch an toan: bat moi loi de khong lam sap server
            System.err.println("Loi dinh tuyen tin nhan: " + e.getMessage());
            sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "ERROR", "Loi he thong dinh tuyen");
        }
    }

    // Ham phu ho tro gui goi tin ERROR ve cho client
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
            // Bo qua neu khong gui duoc loi
        }
    }
}
