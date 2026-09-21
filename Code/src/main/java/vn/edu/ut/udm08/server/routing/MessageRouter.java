package vn.edu.ut.udm08.server.routing;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryMessageDao;
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
        this(registry, conversationRegistry, conversationDao, null, transaction);
    }

    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry, ConversationDao conversationDao, MessageDao messageDao, Transaction transaction) {
        this.registry = registry;
        this.conversationRegistry = conversationRegistry;
        this.conversationDao = conversationDao;
        this.messageDao = messageDao != null ? messageDao : (transaction != null ? transaction.getMessageDao() : new InMemoryMessageDao());
        this.transaction = transaction;
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
                        boolean isFwd = (msg.type == MessageType.FORWARD) || (msg.fwdFrom != null) || (msg.forwardFromMessageId != null) || "forward".equalsIgnoreCase(msg.kind);
                        if (isFwd) {
                            sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN", "Khong co quyen gui tin vao hoi thoai dich");
                            return;
                        }
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

            // 5b. Xu ly logic Reply: Tra cuu tin goc tu DB Server de dam bao trich dan luon dung
            boolean isReply = (msg.type == MessageType.REPLY) || (msg.replyTo != null) || (msg.replyToMessageId != null) || "reply".equalsIgnoreCase(msg.kind);
            if (isReply) {
                String targetMsgId = (msg.replyToMessageId != null && !msg.replyToMessageId.isBlank()) ? msg.replyToMessageId.trim()
                        : (msg.replyTo != null ? msg.replyTo.trim() : null);

                if (targetMsgId == null || targetMsgId.isBlank()) {
                    sendErrorMessage(senderSession, msg.messageId, "INVALID_REPLY_TARGET", "Tin goc khong ton tai hoac khong thuoc hoi thoai nay");
                    return;
                }

                if (conversationDao != null) {
                    ProtocolMessage origMsg = messageDao != null ? messageDao.findByMessageId(targetMsgId) : null;
                    if (origMsg == null) {
                        sendErrorMessage(senderSession, msg.messageId, "MESSAGE_NOT_FOUND", "Tin nhan goc khong ton tai");
                        return;
                    }

                    // Kiem tra quyen Reply: Tin goc phai thuoc dung cuoc tro chuyen dang chat
                    String currentConvId = (convId != null && !convId.isBlank()) ? convId : targetUser;
                    if (origMsg.convId == null || !origMsg.convId.equals(currentConvId)) {
                        sendErrorMessage(senderSession, msg.messageId, "INVALID_REPLY_TARGET", "Tin goc khong thuoc hoi thoai nay");
                        return;
                    }

                    // Lay Metadata chuan tu DB: Server tu lay ten nguoi gui goc va noi dung tin goc
                    msg.replyTo = origMsg.messageId;
                    msg.replyToMessageId = origMsg.messageId;
                    msg.replyToSender = origMsg.sender;
                    msg.replyToContent = origMsg.content;
                } else {
                    msg.replyTo = targetMsgId;
                    msg.replyToMessageId = targetMsgId;
                }
                msg.kind = "reply";
                msg.type = MessageType.REPLY;
            }

            // 5c. Xu ly logic Forward: Tra cuu tin goc tu DB Server va kiem tra quyen nguon/dich
            boolean isForward = (msg.type == MessageType.FORWARD) || (msg.fwdFrom != null) || (msg.forwardFromMessageId != null) || "forward".equalsIgnoreCase(msg.kind);
            if (isForward) {
                String sourceMsgId = (msg.forwardFromMessageId != null && !msg.forwardFromMessageId.isBlank()) ? msg.forwardFromMessageId.trim()
                        : (msg.fwdFrom != null ? msg.fwdFrom.trim() : null);

                if (sourceMsgId == null || sourceMsgId.isBlank()) {
                    sendErrorMessage(senderSession, msg.messageId, "INVALID_FORWARD_SOURCE", "Tin nguon khong ton tai hoac khong co quyen doc");
                    return;
                }

                if (conversationDao != null) {
                    ProtocolMessage origMsg = messageDao != null ? messageDao.findByMessageId(sourceMsgId) : null;
                    if (origMsg == null) {
                        sendErrorMessage(senderSession, msg.messageId, "MESSAGE_NOT_FOUND", "Tin nhan goc khong ton tai");
                        return;
                    }

                    // Kiem tra quyen Forward: User phai co quyen doc tin o nguon goc (la thanh vien convId nguon)
                    String sourceConvId = origMsg.convId;
                    if (sourceConvId != null && !conversationDao.isMember(sourceConvId, senderId)) {
                        sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN", "Khong co quyen doc tin o hoi thoai nguon");
                        return;
                    }

                    // Kiem tra quyen Forward: User phai co quyen gui tin o noi dich (la thanh vien convId dich)
                    String targetConvId = (convId != null && !convId.isBlank()) ? convId : targetUser;
                    if (targetConvId != null && !conversationDao.isMember(targetConvId, senderId)) {
                        sendErrorMessage(senderSession, msg.messageId, "FORBIDDEN", "Khong co quyen gui tin vao hoi thoai dich");
                        return;
                    }

                    // Lay Metadata chuan tu DB: Server tu lay metadata nguon
                    msg.fwdFrom = origMsg.messageId;
                    msg.forwardFromMessageId = origMsg.messageId;
                    msg.forwardFromConvId = origMsg.convId;
                    msg.forwardedFromSender = origMsg.sender;
                } else {
                    msg.fwdFrom = sourceMsgId;
                    msg.forwardFromMessageId = sourceMsgId;
                }
                msg.isForwarded = true;
                msg.kind = "forward";
                msg.type = MessageType.FORWARD;
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
                if (messageDao != null) {
                    messageDao.save(messageToSend);
                }
            }

            if (conversationDao != null) {
                String effectiveConvId = (convId != null && !convId.isBlank()) ? convId : targetUser;
                if (messageToSend.convId == null || messageToSend.convId.isBlank()) {
                    messageToSend.convId = effectiveConvId;
                }
                conversationDao.addMessage(effectiveConvId, messageToSend);
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
                // 8. Phan hoi cho nguoi gui truoc: Tra ve goi tin xac nhan (type = "MESSAGE_ACK")
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

                // 9. Thu gui Realtime cho nguoi nhan: Neu Online thi gui, neu Offline thi bo qua buoc nay
                // Neu nguoi nhan bi rot mang hoac loi Socket thi KHONG bao loi cho nguoi gui vi tin da luu an toan vao DB
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
