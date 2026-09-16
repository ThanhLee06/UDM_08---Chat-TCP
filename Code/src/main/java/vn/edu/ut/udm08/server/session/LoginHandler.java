package vn.edu.ut.udm08.server.session;
import java.io.IOException;
import java.util.List;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.ConvId;
public class LoginHandler {
    private OnlineUserRegistry registry;
    private IConversationRegistry conversationRegistry;
    public LoginHandler(OnlineUserRegistry registry) {
        this(registry, null);
    }
    public LoginHandler(OnlineUserRegistry registry, IConversationRegistry conversationRegistry) {
        if (registry == null) {
            throw new IllegalArgumentException("Registry != null");
        }
        this.registry = registry;
        this.conversationRegistry = conversationRegistry;
    }
    public boolean handleLoginSuccess(ClientSession session, vn.edu.ut.udm08.shared.model.User user) {
        if (session == null || user == null || user.getId() == null) {
            return false;
        }
        if (session.isAuthenticated()) {
            sendError(session, "ALREADY_AUTHENTICATED", "Phien nay da dang nhap");
            return false;
        }

        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            registry.kickSession(user.getUsername(), "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác.");
        }
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            registry.kickSession(user.getPhoneNumber(), "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác.");
        }

        boolean authenticated = session.authenticate(user);
        if (!authenticated) {
            sendError(session, "INVALID_IDENTITY", "Thong tin dang nhap khong hop le");
            return false;
        }
        boolean registered = registry.register(session);
        if (!registered) {
            sendError(session, "USERNAME_TAKEN", "Tai khoan da dang nhap tren thiet bi khac");
            return false;
        }
        if (conversationRegistry != null) {
            conversationRegistry.join(ConvId.PUBLIC_ROOM_ID, session);
        }
        broadcastUserList();
        return true;
    }

    public boolean handleHello(ClientSession session, ProtocolMessage message) {
        if (session == null) {
            return false;
        }
        if (message == null || message.type != MessageType.HELLO) {
            sendError(session, "INVALID_HELLO", "Goi tin dang nhap khong hop le");
            return false;
        }
        if (session.isAuthenticated()) {
            sendError(session, "ALREADY_AUTHENTICATED", "Phien nay da dang nhap");
            return false;
        }
        if (!UsernameValidator.isValid(message.sender)) {
            sendError(session, "INVALID_USERNAME", "Username khong hop le");
            return false;
        }
        if (message.avatarId == null || message.avatarId.isBlank()) {
            sendError(session, "INVALID_AVATAR", "Avatar khong hop le");
            return false;
        }
        registry.kickSession(message.sender, "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác.");
        boolean authenticated = session.authenticate(message.sender, message.avatarId);
        if (!authenticated) {
            sendError(session, "INVALID_IDENTITY", "Thong tin dang nhap khong hop le");
            return false;
        }
        boolean registered = registry.register(session);
        if (!registered) {
            sendError(session, "USERNAME_TAKEN", "Username da co nguoi su dung");
            session.close();
            return false;
        }
        boolean sent = sendHelloOk(session);
        if (!sent) {
            registry.remove(session);
            return false;
        }
        if (conversationRegistry != null) {
            conversationRegistry.join(ConvId.PUBLIC_ROOM_ID, session);
        }
        broadcastUserList();
        return true;
    }
    public void handleDisconnect(ClientSession session) {
        if (session == null) {
            return;
        }
        if (conversationRegistry != null) {
            conversationRegistry.removeSessionFromAll(session);
        }
        boolean removed = registry.remove(session);
        session.close();
        if (removed) {
            broadcastUserList();
        }
    }
    public boolean handleLogout(ClientSession session, ProtocolMessage message) {
        if (session == null) {
            return false;
        }
        boolean wasAuthenticated = session.isAuthenticated();
        String username = session.getUsername();
        ProtocolMessage response = new ProtocolMessage(MessageType.LOGOUT_OK);
        response.sender = "SERVER";
        response.target = username != null ? username : "ANONYMOUS";
        response.timestamp = System.currentTimeMillis();
        try {
            session.sendMessage(response);
        } catch (IOException ignored) {
        }
        if (conversationRegistry != null) {
            conversationRegistry.removeSessionFromAll(session);
        }
        boolean removed = false;
        if (wasAuthenticated) {
            removed = registry.remove(session);
        }
        session.unauthenticate();
        session.close();
        if (removed) {
            broadcastUserList();
        }
        return true;
    }
    public void broadcastUserList() {
        ProtocolMessage message = new ProtocolMessage(MessageType.USER_LIST);
        message.sender = "SERVER";
        message.timestamp = System.currentTimeMillis();
        List<UserProfile> onlineUsers = registry.getOnlineUsers();
        message.users = onlineUsers;
        List<ClientSession> sessions = registry.getSessions();
        for (ClientSession session : sessions) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
            }
        }
    }
    private boolean sendHelloOk(ClientSession session) {
        ProtocolMessage message = new ProtocolMessage(MessageType.HELLO_OK);
        message.sender = "SERVER";
        message.target = session.getUsername();
        message.timestamp = System.currentTimeMillis();

        try {
            session.sendMessage(message);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    private void sendError(ClientSession session, String errorCode, String errorMessage) {
        ProtocolMessage message = new ProtocolMessage(MessageType.ERROR);
        message.sender = "SERVER";
        message.errorCode = errorCode;
        message.errorMessage = errorMessage;
        message.timestamp = System.currentTimeMillis();
        try {session.sendMessage(message);}
        catch (IOException e) {}
    }
}
