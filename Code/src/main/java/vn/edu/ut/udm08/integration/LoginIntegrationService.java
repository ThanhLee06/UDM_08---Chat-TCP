package vn.edu.ut.udm08.integration;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
public class LoginIntegrationService {
    private final OnlineUserRegistry onlineUserRegistry;
    private final LoginHandler loginHandler;
    public LoginIntegrationService() {
        this.onlineUserRegistry = new OnlineUserRegistry();
        this.loginHandler = new LoginHandler(onlineUserRegistry);
    }
    public LoginIntegrationService(OnlineUserRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Registry khong duoc null");
        }
        this.onlineUserRegistry = registry;
        this.loginHandler = new LoginHandler(onlineUserRegistry);
    }
    public boolean processLogin(ClientSession session, ProtocolMessage message) {
        return loginHandler.handleHello(session, message);
    }
    public void processDisconnect(ClientSession session) {
        loginHandler.handleDisconnect(session);
    }
    public OnlineUserRegistry getOnlineUserRegistry() {
        return onlineUserRegistry;
    }
    public LoginHandler getLoginHandler() {
        return loginHandler;
    }
}
