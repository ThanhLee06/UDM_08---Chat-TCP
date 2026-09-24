package vn.edu.ut.udm08.server.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import vn.edu.ut.udm08.server.conversation.ConversationRegistry;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.handler.AuthHandler;
import vn.edu.ut.udm08.server.handler.RegisterHandler;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.server.routing.MessageRouter;
import vn.edu.ut.udm08.server.search.UserSearchHandler;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.server.session.SessionValidator;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class ChatServer {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ChatServer.class.getName());
    private final java.util.Set<ClientSession> allSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final int configuredPort;
    private final int idleTimeoutMs;

    private final OnlineUserRegistry registry;
    private final ConversationRegistry conversationRegistry;
    private final LoginHandler loginHandler;
    private final MessageRouter messageRouter;
    private final SessionValidator sessionValidator;
    private final UserSearchHandler userSearchHandler;
    private final vn.edu.ut.udm08.server.handler.GroupHandler groupHandler;
    private final vn.edu.ut.udm08.server.handler.ReadStateHandler readStateHandler;
    private final AuthHandler authHandler;
    private final vn.edu.ut.udm08.server.handler.ProfileHandler profileHandler;
    private final RegisterHandler registerHandler;
    private final ExecutorService clientExecutor;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    private final vn.edu.ut.udm08.server.config.DatabaseConnectionFactory dbFactory;
    private final vn.edu.ut.udm08.server.config.DatabaseInitializer dbInitializer;

    public ChatServer(ServerConfig config) {
        this(config, new vn.edu.ut.udm08.server.auth.EmailOtpService(new vn.edu.ut.udm08.server.auth.SmtpOtpEmailSender()));
    }

    public ChatServer(ServerConfig config, vn.edu.ut.udm08.server.auth.IOtpService otpService) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig must not be null");
        }

        this.configuredPort = config.getPort();
        this.idleTimeoutMs = config.getIdleTimeoutMs();
        this.dbFactory = new vn.edu.ut.udm08.server.config.DatabaseConnectionFactory(config.getDbUrl(), config.getDbBusyTimeout());
        this.dbInitializer = new vn.edu.ut.udm08.server.config.DatabaseInitializer(dbFactory);
        this.registry = new OnlineUserRegistry();
        this.conversationRegistry = new ConversationRegistry();
        this.loginHandler = new LoginHandler(registry, conversationRegistry);
        this.messageRouter = new MessageRouter(registry, conversationRegistry, dbFactory);
        this.sessionValidator = new SessionValidator();
        UserRepository userRepository = new UserRepository(dbFactory);
        this.userSearchHandler = new UserSearchHandler(userRepository);
        this.groupHandler = new vn.edu.ut.udm08.server.handler.GroupHandler(new vn.edu.ut.udm08.server.repository.GroupRepository(dbFactory), registry);
        this.readStateHandler = new vn.edu.ut.udm08.server.handler.ReadStateHandler(new vn.edu.ut.udm08.server.repository.ReadStateRepository(dbFactory));
        java.nio.file.Path avatarDirectory = java.nio.file.Path.of(config.getDbUrl().replace("jdbc:sqlite:", "")).toAbsolutePath().getParent().resolve("avatars");
        this.profileHandler = new vn.edu.ut.udm08.server.handler.ProfileHandler(userRepository, new vn.edu.ut.udm08.server.service.AvatarStore(avatarDirectory), loginHandler);

        vn.edu.ut.udm08.server.service.UserLoginService loginService = new vn.edu.ut.udm08.server.service.UserLoginService(userRepository);
        vn.edu.ut.udm08.server.service.UserRegisterService registerService = new vn.edu.ut.udm08.server.service.UserRegisterService(userRepository, new vn.edu.ut.udm08.shared.security.PasswordEncoder(), otpService);

        this.authHandler = new AuthHandler(loginService, registry, conversationRegistry, loginHandler);
        this.registerHandler = new RegisterHandler(registerService, loginService, otpService);

        this.clientExecutor = Executors.newCachedThreadPool();
        this.boundPort = configuredPort;
    }

    public int getPort() {
        return boundPort;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() throws IOException {
        synchronized (this) {
            if (running) {
                throw new IllegalStateException("ChatServer is already running");
            }

            dbInitializer.initialize();
            ServerLog.configure();
            ServerSocket socket = new ServerSocket(configuredPort);
            serverSocket = socket;
            boundPort = socket.getLocalPort();
            running = true;
        }

        LOG.info("SERVER_START port=" + boundPort);

        try {
            acceptClients();
        } finally {
            stop();
            synchronized (this) {
                running = false;
                serverSocket = null;
            }
        }
    }

    private void acceptClients() throws IOException {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                if (!running) {
                    closeSocket(socket);
                    break;
                }
                if (allSessions.size() >= 128) {
                    LOG.warning("CONNECTION_LIMIT");
                    closeSocket(socket);
                    continue;
                }

                createClientSession(socket);
            } catch (IOException e) {
                if (running) {
                    throw e;
                }

                break;
            }
        }
    }

    private void createClientSession(Socket socket) {
        ClientSession session;

        try {
            socket.setSoTimeout(idleTimeoutMs);
            socket.setTcpNoDelay(true);
            session = ClientSession.createAnonymous(socket);
        } catch (RuntimeException | IOException e) {
            closeSocket(socket);
            return;
        }

        allSessions.add(session);
        LOG.info("CONNECT session=" + session.getSessionId());
        session.setMessageHandler(message -> dispatch(session, message));
        session.setDisconnectHandler(() -> {
            allSessions.remove(session);
            LOG.info("DISCONNECT session=" + session.getSessionId());
            loginHandler.handleDisconnect(session);
        });

        clientExecutor.submit(session);
    }

    private void dispatch(ClientSession session, ProtocolMessage message) {
        if (message == null || message.type == null) {
            session.sendError("INVALID_MESSAGE", "Message type is required");
            return;
        }
        LOG.info("REQUEST session=" + session.getSessionId() + " type=" + message.type);

        if (!sessionValidator.validate(session, message)) {
            return;
        }

        switch (message.type) {
            case CONVERSATION_READ -> readStateHandler.handle(session, message);
            case GROUP_REQUEST -> groupHandler.handle(session, message);
            case PROFILE_GET, PROFILE_UPDATE, AVATAR_GET -> profileHandler.handle(session, message);
            case HELLO -> loginHandler.handleHello(session, message);
            case CHAT -> messageRouter.handleChatMessage(session, message);
            case LOGOUT -> loginHandler.handleLogout(session, message);
            case USER_SEARCH_REQUEST -> userSearchHandler.handleSearchRequest(session, message);
            case CONVERSATION_LIST_REQUEST -> messageRouter.handleConversationListRequest(session, message);
            case HISTORY_REQUEST -> messageRouter.handleHistoryRequest(session, message);
            case OPEN_DM_REQUEST -> messageRouter.handleOpenDmRequest(session, message);
            case AUTH_LOGIN -> authHandler.handleLogin(session, message);
            case AUTH_REGISTER_INIT -> registerHandler.handleRegisterInit(session, message);
            case AUTH_REGISTER_VERIFY_OTP -> registerHandler.handleVerifyOtp(session, message);
            case AUTH_REGISTER_RESEND_OTP -> registerHandler.handleResendOtp(session, message);
            case AUTH_FORGOT_INIT -> registerHandler.handleForgotInit(session, message);
            case AUTH_FORGOT_RESET -> registerHandler.handleForgotReset(session, message);
            case DISCONNECT -> {
                loginHandler.handleDisconnect(session);
            }
            default -> {
                session.sendError("UNSUPPORTED_MESSAGE", "Unsupported client message type");
            }
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        ServerSocket socket = serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        clientExecutor.shutdownNow();
        for (ClientSession session : allSessions) session.close();
        allSessions.clear();
        LOG.info("SERVER_STOP");
    }

    public OnlineUserRegistry getRegistry() {
        return registry;
    }

    public IConversationRegistry getConversationRegistry() {
        return conversationRegistry;
    }

    private void closeSocket(Socket socket) {
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
