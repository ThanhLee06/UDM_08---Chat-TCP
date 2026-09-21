package vn.edu.ut.udm08.server.session;

import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class SessionValidator {
    public boolean validate(ClientSession session, ProtocolMessage message) {
        if (message == null || message.type == null) {
            return false;
        }
        if (message.type == MessageType.HELLO) {
            if (session != null) session.sendError(message.requestId, "AUTH_REQUIRED", "Use AUTH_LOGIN");
            return false;
        }
        if (message.type == MessageType.DISCONNECT
                || message.type == MessageType.AUTH_LOGIN
                || message.type == MessageType.AUTH_REGISTER_INIT
                || message.type == MessageType.AUTH_REGISTER_VERIFY_OTP
                || message.type == MessageType.AUTH_REGISTER_RESEND_OTP
                || message.type == MessageType.AUTH_FORGOT_INIT
                || message.type == MessageType.AUTH_FORGOT_RESET) {
            return true;
        }
        if (session == null || !session.isAuthenticated() || session.getUsername() == null || session.getUsername().isBlank()) {
            sendUnauthorizedError(session, message.messageId);
            return false;
        }
        return true;
    }
    private void sendUnauthorizedError(ClientSession session, String messageId) {
        if (session == null) {
            return;
        }
        ProtocolMessage err = new ProtocolMessage(MessageType.ERROR);
        err.messageId = messageId;
        err.sender = "SERVER";
        err.errorCode = "UNAUTHORIZED";
        err.errorMessage = "Phiên làm việc không hợp lệ hoặc đã hết hạn";
        err.timestamp = System.currentTimeMillis();
        try {
            session.sendMessage(err);
        } catch (Exception ignored) {
        }
    }
}
