package vn.edu.ut.udm08.server.routing;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.MessageDao;
import vn.edu.ut.udm08.server.room.Transaction;
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
    private final ConversationDao conversationDao;
    private final Transaction transaction;
    private final MessageDao messageDao;

    public MessageRouter(OnlineUserRegistry registry) {
        this(registry, (IConversationRegistry) null, null, null);
    }

    public MessageRouter(IConversationRegistry conversationRegistry) {
        this((OnlineUserRegistry) null, conversationRegistry, null, null);
    }

    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry) {
        this(registry, conversationRegistry, null, null);
    }

    public MessageRouter(OnlineUserRegistry registry, ConversationDao conversationDao, Transaction transaction) {
        this(registry, (IConversationRegistry) null, conversationDao, transaction);
    }

    public MessageRouter(OnlineUserRegistry registry, MessageDao messageDao, ConversationDao conversationDao, Transaction transaction) {
        this.registry = registry;
        this.conversationRegistry = null;
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
        this.transaction = transaction != null ? transaction : (messageDao != null ? new Transaction(messageDao) : null);
    }

    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry, ConversationDao conversationDao, Transaction transaction) {
        this.registry = registry;
        this.conversationRegistry = conversationRegistry;
        this.conversationDao = conversationDao;
        this.transaction = transaction;
        this.messageDao = (transaction != null) ? transaction.getMessageDao() : null;
    }

    public MessageDao getMessageDao() {
        return messageDao;
    }

    public ConversationDao getConversationDao() {
        return conversationDao;
    }

    public Transaction getTransaction() {
        return transaction;
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

            if (msg == null) {
                return;
            }

            // 2. Xac thuc Sender: Lay senderId tu Session (khong tin Client truyen len)
            String senderId = senderSession.getUsername();
            if (msg.sender != null && !msg.sender.equals(senderId)) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_SENDER", "Nguoi gui khong khop voi phien lam viec");
                return;
            }
            msg.sender = senderId;

            // 3. Kiem tra nguoi nhan (target) hoac cuoc tro chuyen (convId)
            String convId = msg.convId;
            String targetUser = msg.target;
            if ((convId == null || convId.isBlank()) && (targetUser == null || targetUser.isBlank())) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_TARGET", "Hoi thoai (convId) hoac nguoi nhan khong duoc de trong");
                return;
            }

            // 4. Kiem tra noi dung tin nhan (content)
            if (msg.content == null || msg.content.trim().isEmpty()) {
                sendErrorMessage(senderSession, msg.messageId, "INVALID_CONTENT", "Noi dung tin nhan khong duoc de trong");
                return;
            }
            if (msg.content.length() > 5000) {
                sendErrorMessage(senderSession, msg.messageId, "CONTENT_TOO_LONG", "Noi dung tin nhan qua dai (toi da 5000 ky tu)");
                return;
            }

            // 5. Phan quyen: Kiem tra User co nam trong cuoc tro chuyen khong bang ConversationDao.isMember(convId, userId)
            if (conversationDao != null) {
                String effectiveConvId = (convId != null && !convId.isBlank()) ? convId : targetUser;
                if (effectiveConvId != null && !effectiveConvId.isBlank()) {
                    List<String> members = conversationDao.getMembers(effectiveConvId);
                    if (members != null && !members.isEmpty()) {
                        if (!conversationDao.isMember(effectiveConvId, senderId)) {
                            sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN", "Khong co quyen gui tin vao hoi thoai nay");
                            return;
                        }
                    } else {
                        conversationDao.addMember(effectiveConvId, senderId);
                        if (targetUser != null && !targetUser.isBlank() && !targetUser.equals(senderId)) {
                            conversationDao.addMember(effectiveConvId, targetUser.trim());
                        }
                    }
                }
            } else if (convId != null && !convId.isBlank() && conversationRegistry != null) {
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

            // 6. Luu DB truoc: Goi Transaction cua TV2 (ST-103) de luu tin vao SQLite/DB
            // Chi khi DB xac nhan da luu thanh cong thi Server moi phat tin di cho nguoi nhan va bao ve cho nguoi gui
            ProtocolMessage messageToSend = msg;
            if (transaction != null) {
                ProtocolMessage savedMsg = transaction.saveMessage(msg);
                if (savedMsg == null) {
                    sendErrorMessage(senderSession, msg.messageId, "DB_SAVE_FAILED", "Khong the luu tin nhan vao co so du lieu");
                    return;
                }
                messageToSend = savedMsg;
            } else {
                if (messageToSend.timestamp == null || messageToSend.timestamp <= 0) {
                    messageToSend.timestamp = System.currentTimeMillis();
                }
            }

            // 7. Ban tin Realtime: Tim cac thanh vien dang Online trong cuoc tro chuyen de gui
            List<ClientSession> targets = findTargetSessions(senderSession, convId, targetUser);
            List<ClientSession> recipients = new ArrayList<>();
            for (ClientSession session : targets) {
                if (session != null && session.isConnected() && !session.equals(senderSession)) {
                    recipients.add(session);
                }
            }

            if (transaction != null) {
                // Xu ly rot mang nguoi nhan: Neu gui Realtime cho nguoi nhan bi loi (do nguoi nhan rot mang dung luc do)
                // thi KHONG bao loi cho nguoi gui. Tin nhan van da nam an toan trong DB, nguoi nhan se doc lai qua lich su khi online lai.
                for (ClientSession recipient : recipients) {
                    try {
                        recipient.sendMessage(messageToSend);
                    } catch (Exception e) {
                        System.err.println("Goi tin realtime toi nguoi nhan that bai (rot mang): " + e.getMessage());
                        if (registry != null && !recipient.isConnected()) {
                            registry.remove(recipient);
                        }
                    }
                }

                // 8. Phan hoi cho nguoi gui: Tra ve goi tin xac nhan (type = "MESSAGE_ACK")
                // kem messageId, timestamp chinh thuc va trang thai SENT cho nguoi gui
                ProtocolMessage ackMsg = new ProtocolMessage(MessageType.MESSAGE_ACK);
                ackMsg.messageId = messageToSend.messageId;
                ackMsg.convId = messageToSend.convId;
                ackMsg.sender = "SERVER";
                ackMsg.target = senderId;
                ackMsg.timestamp = messageToSend.timestamp;
                ackMsg.sequence = messageToSend.sequence;
                ackMsg.status = "SENT";

                try {
                    senderSession.sendMessage(ackMsg);
                } catch (Exception e) {
                    System.err.println("Khong the gui MESSAGE_ACK ve cho sender (da ngat ket noi): " + e.getMessage());
                }
            } else {
                // Luong mac dinh cu khi khong dung transaction: Giu nguyen de khong anh huong code cu cua nguoi khac
                if (recipients.isEmpty()) {
                    sendErrorMessage(senderSession, msg.messageId, "USER_OFFLINE", "Nguoi nhan khong ton tai hoac da offline trong hoi thoai");
                    return;
                }

                boolean anyDelivered = false;
                for (ClientSession recipient : recipients) {
                    try {
                        recipient.sendMessage(messageToSend);
                        anyDelivered = true;
                    } catch (Exception ignored) {
                    }
                }

                if (!anyDelivered) {
                    sendErrorMessage(senderSession, msg.messageId, "DELIVERY_FAILED", "Khong the gui tin nhan toi hoi thoai");
                    return;
                }

                ProtocolMessage okMsg = new ProtocolMessage(MessageType.CHAT_OK);
                okMsg.messageId = msg.messageId;
                okMsg.sender = "SERVER";
                okMsg.target = msg.sender;
                okMsg.convId = msg.convId;
                okMsg.timestamp = System.currentTimeMillis();

                senderSession.sendMessage(okMsg);
            }

        } catch (Exception e) {
            System.err.println("Loi dinh tuyen tin nhan: " + e.getMessage());
            sendErrorMessage(senderSession, msg != null ? msg.messageId : null, "ERROR", "Loi he thong dinh tuyen");
        }
    }

    private List<ClientSession> findTargetSessions(ClientSession senderSession, String convId, String targetUser) {
        List<ClientSession> result = new ArrayList<>();

        if (conversationDao != null && convId != null && !convId.isBlank()) {
            List<String> members = conversationDao.getMembers(convId);
            if (members != null) {
                for (String member : members) {
                    if (member != null && !member.equalsIgnoreCase(senderSession.getUsername()) && registry != null) {
                        ClientSession userSession = registry.find(member);
                        if (userSession != null && userSession.isConnected() && !result.contains(userSession)) {
                            result.add(userSession);
                        }
                    }
                }
            }
        }

        if (ConvId.isPublicRoom(convId) || "PUBLIC".equalsIgnoreCase(targetUser)) {
            if (conversationRegistry != null) {
                List<ClientSession> convSessions = conversationRegistry.getSessions(ConvId.PUBLIC_ROOM_ID);
                if (convSessions != null && !convSessions.isEmpty()) {
                    result.addAll(convSessions);
                }
            }
            if (result.isEmpty() && registry != null) {
                result.addAll(registry.getSessions());
            }
            return result;
        }
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
        if (result.isEmpty() && ConvId.isDm(convId) && senderSession != null && senderSession.getUsername() != null && registry != null) {
            String other = ConvId.getOtherUser(convId, senderSession.getUsername());
            if (other != null) {
                ClientSession userSession = registry.find(other);
                if (userSession != null && userSession.isConnected()) {
                    result.add(userSession);
                }
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
