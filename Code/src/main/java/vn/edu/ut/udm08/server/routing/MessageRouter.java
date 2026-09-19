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
    private final vn.edu.ut.udm08.server.service.IChatStorageService chatStorageService;
    private final vn.edu.ut.udm08.server.repository.IConversationDao conversationDao;
    private final vn.edu.ut.udm08.server.repository.IUserRepository userRepository;

    public MessageRouter(OnlineUserRegistry registry) {
        this(registry, null);
    }
    public MessageRouter(IConversationRegistry conversationRegistry) {
        this(null, conversationRegistry);
    }
    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry) {
        this(registry, conversationRegistry, new vn.edu.ut.udm08.server.config.DatabaseConnectionFactory());
    }
    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry, vn.edu.ut.udm08.server.config.DatabaseConnectionFactory dbFactory) {
        this(registry, conversationRegistry, new vn.edu.ut.udm08.server.service.ChatStorageService(dbFactory), new vn.edu.ut.udm08.server.repository.ConversationDao(dbFactory), new vn.edu.ut.udm08.server.repository.UserRepository(dbFactory));
    }
    public MessageRouter(OnlineUserRegistry registry, IConversationRegistry conversationRegistry, vn.edu.ut.udm08.server.service.IChatStorageService chatStorageService, vn.edu.ut.udm08.server.repository.IConversationDao conversationDao, vn.edu.ut.udm08.server.repository.IUserRepository userRepository) {
        this.registry = registry;
        this.conversationRegistry = conversationRegistry;
        this.chatStorageService = chatStorageService;
        this.conversationDao = conversationDao;
        this.userRepository = userRepository;
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

            if (convId == null || convId.isBlank()) {
                if ("PUBLIC".equalsIgnoreCase(targetUser) || ConvId.isPublicRoom(targetUser)) {
                    convId = ConvId.PUBLIC_ROOM_ID;
                } else {
                    convId = ConvId.forDm(senderSession.getUsername(), targetUser);
                }
                msg.convId = convId;
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

            vn.edu.ut.udm08.shared.model.User senderUser = getCurrentUser(senderSession);
            if (ConvId.isDm(convId)) {
                ensureDmConversation(convId, senderSession.getUsername(), targetUser, senderUser);
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

            String fwdSource = msg.fwdFrom != null ? msg.fwdFrom : (msg.forwardFromConvId != null ? msg.forwardFromConvId : msg.forwardFromMessageId);
            if (fwdSource != null) {
                if (fwdSource.isBlank()) {
                    sendErrorMessage(senderSession, msg.messageId, "INVALID_FORWARD_SOURCE", "Tin nguon khong ton tai hoac khong co quyen doc");
                    return;
                }
                if (msg.kind == null || msg.kind.isBlank()) {
                    msg.kind = "forward";
                }
            }

            vn.edu.ut.udm08.server.model.ChatMessage chatMsg = new vn.edu.ut.udm08.server.model.ChatMessage(
                    msg.messageId,
                    msg.convId,
                    senderSession.getUsername(),
                    msg.content,
                    msg.timestamp != null ? msg.timestamp : System.currentTimeMillis()
            );
            chatMsg.setKind(msg.kind != null ? msg.kind : "text");
            chatMsg.setReplyToMessageId(msg.replyToMessageId != null ? msg.replyToMessageId : msg.replyTo);
            chatMsg.setForwardFromMessageId(msg.forwardFromMessageId != null ? msg.forwardFromMessageId : msg.fwdFrom);
            chatMsg.setForwardFromConvId(msg.forwardFromConvId);

            if (chatStorageService != null) {
                chatStorageService.saveMessageWithTransaction(chatMsg);
            }

            // 5. Tim ClientSession cua nguoi nhan trong OnlineUserRegistry
            List<ClientSession> targets = findTargetSessions(senderSession, convId, targetUser);
            boolean hasOtherRecipientOnline = targets.stream().anyMatch(r -> r != null && r.isConnected() && !r.equals(senderSession));
            if (!hasOtherRecipientOnline && !ConvId.isPublicRoom(convId)) {
                sendErrorMessage(senderSession, msg.messageId, "USER_OFFLINE", "Nguoi nhan hien khong truy cap (offline)");
                return;
            }

            for (ClientSession recipient : targets) {
                if (recipient != null && recipient.isConnected() && !recipient.equals(senderSession)) {
                    try {
                        recipient.sendMessage(msg);
                    } catch (Exception ignored) {
                    }
                }
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

    public void handleConversationListRequest(ClientSession session, ProtocolMessage msg) {
        try {
            vn.edu.ut.udm08.shared.model.User user = getCurrentUser(session);
            if (user == null || user.getId() == null) {
                sendErrorMessage(session, msg != null ? msg.requestId : null, "UNAUTHORIZED", "Chua dang nhap");
                return;
            }

            List<vn.edu.ut.udm08.server.model.Conversation> dbConvs = conversationDao != null ? conversationDao.getInboxForUser(user.getId()) : List.of();
            List<vn.edu.ut.udm08.shared.model.ConversationSummary> summaries = new ArrayList<>();
            for (vn.edu.ut.udm08.server.model.Conversation c : dbConvs) {
                vn.edu.ut.udm08.shared.model.ConversationSummary summary = new vn.edu.ut.udm08.shared.model.ConversationSummary();
                summary.convId = c.getConvId();
                summary.chatType = c.getType();
                if (ConvId.isPublicRoom(c.getConvId())) {
                    summary.displayName = "Phòng chung";
                    summary.avatar = "default";
                } else if (ConvId.isDm(c.getConvId())) {
                    String other = ConvId.getOtherUser(c.getConvId(), user.getUsername());
                    summary.displayName = (other != null && !other.isBlank()) ? other : (c.getName() != null ? c.getName() : "Người dùng");
                    summary.avatar = "default";
                } else {
                    summary.displayName = c.getName() != null ? c.getName() : c.getConvId();
                    summary.avatar = "default";
                }
                summary.lastMessage = c.getLastMessagePreview();
                summary.lastActivity = c.getLastActivity();
                summaries.add(summary);
            }

            ProtocolMessage response = new ProtocolMessage(MessageType.CONVERSATION_LIST_RESPONSE);
            response.requestId = msg != null ? msg.requestId : null;
            response.sender = "SERVER";
            response.conversations = summaries;
            response.timestamp = System.currentTimeMillis();

            session.sendMessage(response);
        } catch (Exception e) {
            System.err.println("Loi xu ly CONVERSATION_LIST_REQUEST: " + e.getMessage());
            sendErrorMessage(session, msg != null ? msg.requestId : null, "ERROR", "Loi tai danh sach hoi thoai");
        }
    }

    public void handleHistoryRequest(ClientSession session, ProtocolMessage msg) {
        try {
            vn.edu.ut.udm08.shared.model.User user = getCurrentUser(session);
            if (user == null) {
                sendErrorMessage(session, msg != null ? msg.requestId : null, "UNAUTHORIZED", "Chua dang nhap");
                return;
            }

            String convId = msg != null ? msg.convId : null;
            if (convId == null || convId.isBlank()) {
                sendErrorMessage(session, msg != null ? msg.requestId : null, "INVALID_TARGET", "Thieu convId");
                return;
            }

            Long beforeSeq = null;
            if (msg.cursor != null && !msg.cursor.isBlank()) {
                try {
                    beforeSeq = Long.parseLong(msg.cursor);
                } catch (Exception ignored) {
                }
            }
            int limit = (msg.limit != null && msg.limit > 0) ? msg.limit : 30;

            vn.edu.ut.udm08.server.model.MessagePagedResult paged = conversationDao != null ? conversationDao.getMessages(convId, beforeSeq, limit) : new vn.edu.ut.udm08.server.model.MessagePagedResult(List.of(), null, false);
            List<ProtocolMessage> messageList = new ArrayList<>();
            for (vn.edu.ut.udm08.server.model.ChatMessage row : paged.getMessages()) {
                ProtocolMessage m = new ProtocolMessage(MessageType.CHAT);
                m.messageId = row.getMessageId();
                m.convId = row.getConvId();
                m.sender = row.getSenderUsername();
                m.content = row.getContent();
                m.timestamp = row.getTimestamp();
                m.kind = row.getKind();
                m.replyToMessageId = row.getReplyToMessageId();
                m.forwardFromMessageId = row.getForwardFromMessageId();
                m.forwardFromConvId = row.getForwardFromConvId();
                if (row.getForwardFromMessageId() != null || "forward".equalsIgnoreCase(row.getKind())) {
                    m.isForwarded = true;
                }
                messageList.add(m);
            }

            ProtocolMessage response = new ProtocolMessage(MessageType.HISTORY_RESPONSE);
            response.requestId = msg != null ? msg.requestId : null;
            response.convId = convId;
            response.sender = "SERVER";
            response.messages = messageList;
            response.nextCursor = paged.getNextCursor() != null ? String.valueOf(paged.getNextCursor()) : null;
            response.hasMore = paged.isHasMore();
            response.timestamp = System.currentTimeMillis();

            session.sendMessage(response);
        } catch (Exception e) {
            System.err.println("Loi xu ly HISTORY_REQUEST: " + e.getMessage());
            sendErrorMessage(session, msg != null ? msg.requestId : null, "ERROR", "Loi tai lich su tin nhan");
        }
    }

    public void handleOpenDmRequest(ClientSession session, ProtocolMessage msg) {
        try {
            vn.edu.ut.udm08.shared.model.User user = getCurrentUser(session);
            if (user == null || user.getId() == null) {
                sendErrorMessage(session, msg != null ? msg.requestId : null, "UNAUTHORIZED", "Chua dang nhap");
                return;
            }

            String targetUserIdOrName = msg != null ? msg.targetUserId : null;
            if (targetUserIdOrName == null || targetUserIdOrName.isBlank()) {
                sendErrorMessage(session, msg != null ? msg.requestId : null, "INVALID_TARGET", "Thieu targetUserId");
                return;
            }

            vn.edu.ut.udm08.shared.model.User targetUser = userRepository != null ? userRepository.findByUsername(targetUserIdOrName).orElse(null) : null;
            if (targetUser == null && userRepository != null) {
                try {
                    long tid = Long.parseLong(targetUserIdOrName);
                    targetUser = userRepository.findById(tid).orElse(null);
                } catch (Exception ignored) {
                }
            }

            if (targetUser == null) {
                sendErrorMessage(session, msg != null ? msg.requestId : null, "USER_NOT_FOUND", "Khong tim thay nguoi dung");
                return;
            }

            String convId = ConvId.forDm(user.getUsername(), targetUser.getUsername());
            ensureDmConversation(convId, user.getUsername(), targetUser.getUsername(), user);

            vn.edu.ut.udm08.shared.model.ConversationSummary summary = new vn.edu.ut.udm08.shared.model.ConversationSummary();
            summary.convId = convId;
            summary.chatType = "DM";
            summary.displayName = targetUser.getUsername();
            summary.avatar = "default";

            ProtocolMessage response = new ProtocolMessage(MessageType.OPEN_DM_RESPONSE);
            response.requestId = msg != null ? msg.requestId : null;
            response.sender = "SERVER";
            response.conversation = summary;
            response.timestamp = System.currentTimeMillis();

            session.sendMessage(response);
        } catch (Exception e) {
            System.err.println("Loi xu ly OPEN_DM_REQUEST: " + e.getMessage());
            sendErrorMessage(session, msg != null ? msg.requestId : null, "ERROR", "Loi mo hoi thoai rieng");
        }
    }

    private void ensureDmConversation(String convId, String senderUsername, String targetUser, vn.edu.ut.udm08.shared.model.User senderUser) {
        if (conversationDao == null || convId == null) return;
        if (conversationDao.findById(convId).isPresent()) return;

        vn.edu.ut.udm08.server.model.Conversation conv = new vn.edu.ut.udm08.server.model.Conversation(convId, "DM", null);
        conversationDao.createConversation(conv);

        if (senderUser != null && senderUser.getId() != null) {
            conversationDao.addMember(convId, senderUser.getId(), "member");
        }

        String otherName = (targetUser != null && !targetUser.isBlank() && !"PUBLIC".equalsIgnoreCase(targetUser)) ? targetUser : ConvId.getOtherUser(convId, senderUsername);
        if (otherName != null && userRepository != null) {
            userRepository.findByUsername(otherName).ifPresent(otherUser -> {
                if (otherUser.getId() != null) {
                    conversationDao.addMember(convId, otherUser.getId(), "member");
                }
            });
        }
    }

    private vn.edu.ut.udm08.shared.model.User getCurrentUser(ClientSession session) {
        if (session == null) return null;
        if (session.getUser() != null) return session.getUser();
        if (session.getUsername() != null && !session.getUsername().isBlank() && userRepository != null) {
            vn.edu.ut.udm08.shared.model.User user = userRepository.findByUsername(session.getUsername()).orElse(null);
            if (user != null) {
                session.setUser(user);
            }
            return user;
        }
        return null;
    }

    private List<ClientSession> findTargetSessions(ClientSession senderSession, String convId, String targetUser) {
        List<ClientSession> result = new ArrayList<>();
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
