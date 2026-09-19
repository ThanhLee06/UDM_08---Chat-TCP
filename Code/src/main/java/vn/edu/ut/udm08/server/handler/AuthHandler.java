package vn.edu.ut.udm08.server.handler;

import vn.edu.ut.udm08.server.service.UserLoginService;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.dto.AuthLoginRequest;
import vn.edu.ut.udm08.shared.dto.AuthUserDto;
import vn.edu.ut.udm08.shared.dto.LoginRequest;
import vn.edu.ut.udm08.shared.dto.LoginResponse;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.shared.protocol.ConvId;

public class AuthHandler {
    private final UserLoginService loginService;
    private final OnlineUserRegistry onlineUserRegistry;
    private final IConversationRegistry conversationRegistry;
    private final LoginHandler loginHandler;

    public AuthHandler(UserLoginService loginService, OnlineUserRegistry onlineUserRegistry) {
        this(loginService, onlineUserRegistry, null, null);
    }

    public AuthHandler(UserLoginService loginService, OnlineUserRegistry onlineUserRegistry,
                       IConversationRegistry conversationRegistry, LoginHandler loginHandler) {
        this.loginService = loginService;
        this.onlineUserRegistry = onlineUserRegistry;
        this.conversationRegistry = conversationRegistry;
        this.loginHandler = loginHandler;
    }

    public void handleLogin(ClientSession session, ProtocolMessage message) {
        if (session == null || message == null) {
            return;
        }

        try {
            AuthLoginRequest req = JsonUtil.fromJson(message.content, AuthLoginRequest.class);
            if (req == null || req.getUsernameOrPhone() == null || req.getPassword() == null) {
                sendError(session, message.messageId, "INVALID_REQUEST", "Thông tin đăng nhập không hợp lệ");
                return;
            }

            LoginRequest loginReq = new LoginRequest(req.getUsernameOrPhone(), req.getPassword());
            LoginResponse response = loginService.login(loginReq);

            if (!response.isSuccess() || response.getUser() == null) {
                sendError(session, message.messageId, "LOGIN_FAILED", response.getMessage() != null ? response.getMessage() : "Tài khoản hoặc mật khẩu không chính xác");
                return;
            }

            vn.edu.ut.udm08.shared.model.User user = response.getUser();

            session.authenticate(user);
            if (onlineUserRegistry != null) {
                onlineUserRegistry.kickSession(user.getUsername(), "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác", session);
                onlineUserRegistry.register(session);
            }
            if (conversationRegistry != null) {
                conversationRegistry.join(ConvId.PUBLIC_ROOM_ID, session);
            }
            if (loginHandler != null) {
                loginHandler.broadcastUserList();
            }

            ProtocolMessage responseMsg = new ProtocolMessage(MessageType.AUTH_LOGIN_OK);
            responseMsg.messageId = message.messageId;
            responseMsg.sender = "SERVER";
            responseMsg.target = user.getUsername();
            responseMsg.content = JsonUtil.toJson(new AuthUserDto(user));
            responseMsg.timestamp = System.currentTimeMillis();
            session.sendMessage(responseMsg);

        } catch (Exception e) {
            sendError(session, message.messageId, "SERVER_ERROR", "Lỗi hệ thống khi đăng nhập: " + e.getMessage());
        }
    }

    private void sendError(ClientSession session, String messageId, String errorCode, String errorMessage) {
        ProtocolMessage err = new ProtocolMessage(MessageType.ERROR);
        err.messageId = messageId;
        err.sender = "SERVER";
        err.errorCode = errorCode;
        err.errorMessage = errorMessage;
        err.timestamp = System.currentTimeMillis();
        try {
            session.sendMessage(err);
        } catch (Exception ignored) {
        }
    }
}
