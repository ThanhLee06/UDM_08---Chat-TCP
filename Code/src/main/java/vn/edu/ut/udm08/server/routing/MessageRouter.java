package vn.edu.ut.udm08.server.routing;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.ConvId;
import java.util.ArrayList;
import java.util.List;

// Lop dinh tuyen tin nhan giua Client va Server
public class MessageRouter implements IMessageRouter {
    private final OnlineUserRegistry registry;
    private final IConversationRegistry conversationRegistry;

    public MessageRouter(OnlineUserRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Registry must not be null!");
        }
        this.registry = registry;
        this.conversationRegistry = null;
    }
    public MessageRouter(IConversationRegistry conversationRegistry) {
        this.registry = null;
        this.conversationRegistry = conversationRegistry;
    }
    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry) {
        this.registry = registry;
        this.conversationRegistry = conversationRegistry;
    }

    // Xu ly dinh tuyen tin nhan CHAT rieng tu nguoi gui den nguoi nhan
    @Override
    public void handleChatMessage(ClientSession senderSession, ProtocolMessage msg) {
        try {
            // 1. Kiem tra phien lam viec nguoi gui
            if (senderSession == null || senderSession.getUsername() == null) {
                sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "UNAUTHORIZED", "Chua dang nhap");
                return;
            }

            // 2. Kiem tra nguoi gui (sender) co khop voi session hien tai hay khong
            if (msg == null || msg.sender == null || !msg.sender.equals(senderSession.getUsername())) {
                sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "INVALID_SENDER", "Nguoi gui khong khop voi phien lam viec");
                return;
            }

            // 3. Kiem tra nguoi nhan (target) co bi rong khong
            String convId = msg.convId;
            String targetUser = msg.target;
            if ((convId == null || convId.isBlank()) && (targetUser == null || targetUser.isBlank())) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_TARGET", "Hoi thoai (convId) hoac nguoi nhan khong duoc de trong");
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

            if (convId != null && !convId.isBlank() && conversationRegistry != null) {
                if (!ConvId.isDm(convId) && !conversationRegistry.isMember(convId, senderSession)) {
                    sendErrorMessage(senderSession, msg.messageId, "NOT_A_MEMBER", "Khong co quyen gui tin vao hoi thoai nay");
                    return;
                }
            }

            if (msg.replyTo != null) {
                if (msg.replyTo.isBlank()) {
                    sendErrorMessage(senderSession, msg.messageId, "INVALID_REPLY_TARGET", "Tin goc khong ton tai hoac khong thuoc hoi thoai nay");
                    return;
                }
                if (msg.kind == null || msg.kind.isBlank()) {
                    msg.kind = "reply";
                }
            }

            if (msg.fwdFrom != null) {
                if (msg.fwdFrom.isBlank()) {
                    sendErrorMessage(senderSession, msg.messageId, "INVALID_FORWARD_SOURCE", "Tin nguon khong ton tai hoac khong co quyen doc");
                    return;
                }
                if (msg.kind == null || msg.kind.isBlank()) {
                    msg.kind = "forward";
                }
            }

            // 5. Tim ClientSession cua nguoi nhan trong OnlineUserRegistry
            List<ClientSession> targets = findTargetSessions(convId, targetUser);
            List<ClientSession> recipients = new ArrayList<>();
            for (ClientSession session : targets) {
                if (session != null && session.isConnected() && !session.equals(senderSession)) {
                    recipients.add(session);
                }
            }

            if (recipients.isEmpty()) {
                // Nguoi nhan khong ton tai hoac da offline
                sendErrorMessage(senderSession, msg.messageId, "USER_OFFLINE", "Nguoi nhan khong ton tai hoac da offline trong hoi thoai");
                return;
            }

            // 6. Chuyen tiep nguyen ven goi tin CHAT sang nguoi nhan
            boolean anyDelivered = false;
            for (ClientSession recipient : recipients) {
                try {
                    recipient.sendMessage(msg);
                    anyDelivered = true;
                } catch (Exception ignored) {
                }
            }

            if (!anyDelivered) {
                sendErrorMessage(senderSession, msg.messageId, "DELIVERY_FAILED", "Khong the gui tin nhan toi hoi thoai");
                return;
            }

            // 7. Phan hoi goi tin CHAT_OK ve cho nguoi gui xac nhan da toi dich
            ProtocolMessage okMsg = new ProtocolMessage(MessageType.CHAT_OK);
            okMsg.messageId = msg.messageId;
            okMsg.sender = "SERVER";
            okMsg.target = msg.sender;
            okMsg.convId = msg.convId;
            okMsg.timestamp = System.currentTimeMillis();

            senderSession.sendMessage(okMsg);

        } catch (Exception e) {
            // Try-catch an toan: bat moi loi de khong lam sap server
            System.err.println("Loi dinh tuyen tin nhan: " + e.getMessage());
            sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "ERROR", "Loi he thong dinh tuyen");
        }
    }
    private List<ClientSession> findTargetSessions(String convId, String targetUser) {
        List<ClientSession> result = new ArrayList<>();
        if (convId != null && !convId.isBlank() && conversationRegistry != null) {
            List<ClientSession> convSessions = conversationRegistry.getSessions(convId);
            if (convSessions != null && !convSessions.isEmpty()) {
                result.addAll(convSessions);
            }
        }
        if (result.isEmpty() && targetUser != null && !targetUser.isBlank() && registry != null) {
            ClientSession userSession = registry.find(targetUser);
            if (userSession != null && userSession.isConnected()) {
                result.add(userSession);
            }
        }
        if (result.isEmpty() && convId != null && !convId.isBlank() && registry != null) {
            ClientSession userSession = registry.find(convId);
            if (userSession != null && userSession.isConnected()) {
                result.add(userSession);
            }
        }
        return result;
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
