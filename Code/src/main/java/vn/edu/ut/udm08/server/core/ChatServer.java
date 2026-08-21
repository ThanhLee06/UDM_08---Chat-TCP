package vn.edu.ut.udm08.server.core;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import vn.edu.ut.udm08.server.routing.MessageRouter;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.server.session.SessionRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
public class ChatServer {
    private final int port;
    private final OnlineUserRegistry onlineUserRegistry;
    private final SessionRegistry sessionRegistry;
    private final LoginHandler loginHandler;
    private final MessageRouter messageRouter;
    private ServerSocket serverSocket;
    private boolean isRunning;
    public ChatServer(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig khong duoc de trong");
        }
        this.port = config.getPort();
        this.onlineUserRegistry = new OnlineUserRegistry();
        this.sessionRegistry = new SessionRegistry();
        this.loginHandler = new LoginHandler(onlineUserRegistry);
        this.messageRouter = new MessageRouter(sessionRegistry);
        this.isRunning = false;
    }
    public void start() throws IOException {
        if (isRunning) {
            return;
        }
        serverSocket = new ServerSocket(port);
        isRunning = true;
        Thread acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                listenForClients();
            }
        });
        acceptThread.start();
    }
    private void listenForClients() {
        while (isRunning && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handleClient(clientSocket);
                    }
                });
                clientThread.start();
            } catch (IOException e) {
                if (!isRunning) {
                    break;
                }
            }
        }
    }
    private void handleClient(Socket socket) {
        ClientSession session = ClientSession.createAnonymous(socket);
        try {
            ProtocolMessage firstMessage = session.readMessage();
            if (firstMessage == null) {
                session.close();
                return;
            }
            boolean isLoginSuccess = loginHandler.handleHello(session, firstMessage);
            if (!isLoginSuccess) {
                return;
            }
            sessionRegistry.register(session);
            while (isRunning && session.isConnected()) {
                ProtocolMessage message = session.readMessage();
                if (message == null || message.type == MessageType.DISCONNECT) {
                    break;
                }
                if (message.type == MessageType.CHAT) {
                    messageRouter.handleChatMessage(session, message);
                }
            }
        } catch (Exception e) {
        } finally {
            if (session.getUsername() != null) {
                sessionRegistry.unregister(session.getUsername());
            }
            loginHandler.handleDisconnect(session);
        }
    }
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
        }
    }
    public int getPort() {
        return port;
    }
    public boolean isRunning() {
        return isRunning;
    }
}
