package vn.edu.ut.udm08.server.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import vn.edu.ut.udm08.server.routing.MessageRouter;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public class ChatServer {

    private final int configuredPort;

    private final OnlineUserRegistry registry;
    private final LoginHandler loginHandler;
    private final MessageRouter messageRouter;
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
        this.loginHandler = new LoginHandler(registry);
        this.messageRouter = new MessageRouter(registry);
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
        session.setDisconnectHandler(() -> loginHandler.handleDisconnect(session));

        clientExecutor.submit(session);
    }

    private void dispatch(ClientSession session, ProtocolMessage message) {
        if (message == null || message.type == null) {
            return;
        }

        switch (message.type) {
            case HELLO -> loginHandler.handleHello(session, message);
            case CHAT -> messageRouter.handleChatMessage(session, message);
            case DISCONNECT -> loginHandler.handleDisconnect(session);
            default -> {
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
        System.out.println("ChatServer stopped");
    }

    public OnlineUserRegistry getRegistry() {
        return registry;
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