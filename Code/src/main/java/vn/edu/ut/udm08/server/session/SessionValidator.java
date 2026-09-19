package vn.edu.ut.udm08.server.session;

import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class SessionValidator {
    public boolean validate(ClientSession session, ProtocolMessage message) {
        if (message == null || message.type == null) {
            return false;
        }
        if (message.type == MessageType.HELLO || message.type == MessageType.DISCONNECT) {
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
