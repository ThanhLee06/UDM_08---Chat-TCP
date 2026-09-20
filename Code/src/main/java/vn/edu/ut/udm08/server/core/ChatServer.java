package vn.edu.ut.udm08.server.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import vn.edu.ut.udm08.server.conversation.ConversationListHandler;
import vn.edu.ut.udm08.server.conversation.ConversationRegistry;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.server.history.HistoryHandler;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.server.room.ConversationDao;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.routing.MessageRouter;
import vn.edu.ut.udm08.server.search.UserSearchHandler;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.server.session.SessionValidator;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class ChatServer {

    private final int configuredPort;

    private final OnlineUserRegistry registry;
    private final ConversationRegistry conversationRegistry;
    private final ConversationDao conversationDao;
    private final HistoryHandler historyHandler;
    private final ConversationListHandler conversationListHandler;
    private final LoginHandler loginHandler;
    private final MessageRouter messageRouter;
    private final SessionValidator sessionValidator;
    private final UserSearchHandler userSearchHandler;
    private final ExecutorService clientExecutor;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    public ChatServer(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig must not be null");
        }

        this.configuredPort = config.getPort();
        this.registry = new OnlineUserRegistry();
        this.conversationRegistry = new ConversationRegistry();
        this.conversationDao = new InMemoryConversationDao();
        this.historyHandler = new HistoryHandler(conversationDao);
        this.conversationListHandler = new ConversationListHandler(conversationDao);
        this.loginHandler = new LoginHandler(registry, conversationRegistry);
        this.messageRouter = new MessageRouter(registry, conversationRegistry, conversationDao, null);
        this.sessionValidator = new SessionValidator();
        this.userSearchHandler = new UserSearchHandler(new UserRepository());
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

            ServerSocket socket = new ServerSocket(configuredPort);
            serverSocket = socket;
            boundPort = socket.getLocalPort();
            running = true;
        }

        System.out.println("ChatServer started on port " + boundPort);

        try {
            acceptClients();
        } finally {
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
            session = ClientSession.createAnonymous(socket);
        } catch (RuntimeException e) {
            closeSocket(socket);
            return;
        }

        session.setMessageHandler(message -> dispatch(session, message));
        session.setDisconnectHandler(() -> {
            loginHandler.handleDisconnect(session);
        });

        clientExecutor.submit(session);
    }

    private void dispatch(ClientSession session, ProtocolMessage message) {
        if (message == null || message.type == null) {
            return;
        }

        if (!sessionValidator.validate(session, message)) {
            return;
        }

        switch (message.type) {
            case HELLO -> loginHandler.handleHello(session, message);
            case CHAT -> messageRouter.handleChatMessage(session, message);
            case LOGOUT -> loginHandler.handleLogout(session, message);
            case USER_SEARCH_REQUEST -> userSearchHandler.handleSearchRequest(session, message);
            case HISTORY_REQUEST -> historyHandler.handleHistoryRequest(session, message);
            case CONVERSATION_LIST_REQUEST -> conversationListHandler.handleConversationListRequest(session, message);
            case DISCONNECT -> {
                loginHandler.handleDisconnect(session);
            }
            default -> {
            }
        }
    }

    public ConversationDao getConversationDao() {
        return conversationDao;
    }

    public HistoryHandler getHistoryHandler() {
        return historyHandler;
    }

    public ConversationListHandler getConversationListHandler() {
        return conversationListHandler;
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
        System.out.println("ChatServer stopped");
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