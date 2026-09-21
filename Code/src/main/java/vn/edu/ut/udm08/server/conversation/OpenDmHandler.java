package vn.edu.ut.udm08.server.conversation;

import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.io.IOException;
import java.util.Optional;

/**
 * Xử lý API mở hoặc tạo mới cuộc trò chuyện DM khi người dùng chọn người và bấm "Chat" (ST-115).
 */
public class OpenDmHandler {

    private final ConversationDao conversationDao;
    private final IUserRepository userRepository;
    private final OnlineUserRegistry onlineRegistry;

    public OpenDmHandler(ConversationDao conversationDao) {
        this(conversationDao, null, null);
    }

    public OpenDmHandler(ConversationDao conversationDao, IUserRepository userRepository) {
        this(conversationDao, userRepository, null);
    }

    public OpenDmHandler(ConversationDao conversationDao, IUserRepository userRepository, OnlineUserRegistry onlineRegistry) {
        if (conversationDao == null) {
            throw new IllegalArgumentException("conversationDao must not be null");
        }
        this.conversationDao = conversationDao;
        this.userRepository = userRepository;
        this.onlineRegistry = onlineRegistry;
    }

    public void handleOpenDmRequest(ClientSession session, ProtocolMessage message) {
        try {
            // 1. Kiểm tra session đăng nhập
            if (session == null || !session.isAuthenticated() || session.getUsername() == null || session.getUsername().isBlank()) {
                sendError(session, message, "UNAUTHORIZED", "Bạn cần đăng nhập để thực hiện chức năng này");
                return;
            }

            if (message == null || message.type != MessageType.OPEN_DM_REQUEST) {
                return;
            }

            String currentUserId = session.getUsername().trim();

            // 2. Lấy targetUserId của người muốn chat
            String targetUserId = message.targetUserId != null ? message.targetUserId.trim()
                    : (message.target != null ? message.target.trim() : null);

            if (targetUserId == null || targetUserId.isBlank()) {
                sendError(session, message, "BAD_REQUEST", "targetUserId không được để trống");
                return;
            }

            // 3. Kiểm tra hợp lệ: Không cho tự chat với chính mình
            if (currentUserId.equalsIgnoreCase(targetUserId)) {
                sendError(session, message, "SELF_CHAT_NOT_ALLOWED", "Không thể tự tạo cuộc trò chuyện với chính mình");
                return;
            }

            // 4. Kiểm tra targetUserId có tồn tại trong bảng users không
            if (userRepository != null) {
                boolean existsInDb = userRepository.existsByUsername(targetUserId);
                boolean existsOnline = (onlineRegistry != null && onlineRegistry.find(targetUserId) != null);
                if (!existsInDb && !existsOnline) {
                    sendError(session, message, "USER_NOT_FOUND", "Người dùng không tồn tại trong hệ thống");
                    return;
                }
            }

            // 5. Quy tắc Get-or-Create: Gọi findDmBetween(userId, targetUserId)
            Optional<String> existingDm = conversationDao.findDmBetween(currentUserId, targetUserId);
            boolean isNew;
            String convId;

            if (existingDm.isPresent()) {
                // Đã từng chat rồi -> Mở lại ô chat cũ
                convId = existingDm.get();
                isNew = false;
            } else {
                // Chưa từng chat -> Tạo mới 1 cuộc trò chuyện DM trong DB
                convId = conversationDao.getOrCreateDm(currentUserId, targetUserId);
                isNew = true;
            }

            // 6. Dữ liệu trả về: Tên và avatar của đối phương
            String displayName = targetUserId;
            String avatar = conversationDao.getUserAvatar(targetUserId);

            ProtocolMessage response = new ProtocolMessage(MessageType.OPEN_DM_RESPONSE);
            response.requestId = message.requestId;
            response.messageId = message.messageId;
            response.convId = convId;
            response.chatType = "DM";
            response.displayName = displayName;
            response.avatar = avatar;
            response.isNew = isNew;
            response.sender = "SERVER";
            response.target = currentUserId;
            response.timestamp = System.currentTimeMillis();

            // Đóng gói đối tượng ConversationSummary tương thích ST-097
            ConversationSummary summary = new ConversationSummary();
            summary.convId = convId;
            summary.chatType = "DM";
            summary.displayName = displayName;
            summary.avatar = avatar;
            summary.lastActivity = System.currentTimeMillis();
            response.conversation = summary;

            session.sendMessage(response);

        } catch (Exception e) {
            System.err.println("Lỗi xử lý OPEN_DM_REQUEST: " + e.getMessage());
            sendError(session, message, "SERVER_ERROR", "Lỗi máy chủ khi mở cuộc trò chuyện");
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
        } catch (IOException ignored) {
        }
    }
}
