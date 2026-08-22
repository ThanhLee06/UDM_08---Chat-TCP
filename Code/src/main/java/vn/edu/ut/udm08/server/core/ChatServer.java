package vn.edu.ut.udm08.server.core;

import vn.edu.ut.udm08.server.routing.MessageRouter;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        /*
         * Before start():
         * - configured port is returned.
         * - if configuredPort == 0, actual port is available after start().
         */
        this.boundPort = configuredPort;
    }

    public int getPort() {
        return boundPort;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Starts the TCP server.
     *
     * The method blocks while accepting clients.
     * stop() can be called safely from another thread.
     */
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

                ServerSocket socket = serverSocket;
                serverSocket = null;

                closeServerSocket(socket);
            }
        }
    }

    /**
     * Accepts incoming TCP client connections.
     */
    private void acceptClients() throws IOException {

        while (running) {
            try {
                ServerSocket socket = serverSocket;

                if (socket == null || socket.isClosed()) {
                    break;
                }

                Socket clientSocket = socket.accept();

                if (!running) {
                    closeSocket(clientSocket);
                    break;
                }

                clientExecutor.submit(() -> handleClient(clientSocket));

            } catch (IOException e) {
                /*
                 * Closing ServerSocket is the expected mechanism
                 * used by stop() to unblock accept().
                 */
                if (!running) {
                    break;
                }

                throw e;
            }
        }
    }

    /**
     * Handles one client independently.
     */
    private void handleClient(Socket socket) {

        ClientSession session = null;

        try {
            session = ClientSession.createAnonymous(socket);

            while (running && session.isConnected()) {

                ProtocolMessage message = session.readMessage();

                /*
                 * readLine() returned null:
                 * client closed the TCP connection.
                 */
                if (message == null) {
                    break;
                }

                dispatch(session, message);

                /*
                 * DISCONNECT is already handled by dispatch().
                 * Do not continue reading from a closed session.
                 */
                if (message.type == MessageType.DISCONNECT) {
                    break;
                }
            }

        } catch (IOException e) {
            /*
             * A client failure must not terminate the server.
             */
            System.err.println("Client connection error: " + e.getMessage());

        } catch (RuntimeException e) {

            /*
             * Protect the server from unexpected errors
             * caused by one client.
             */
            System.err.println("Unexpected client error: " + e.getMessage());

        } finally {

            /*
             * Always remove authenticated users and close
             * the client connection.
             */
            if (session != null) {
                loginHandler.handleDisconnect(session);
            } else {
                closeSocket(socket);
            }
        }
    }

    /**
     * Dispatches protocol messages to their handlers.
     */
    private void dispatch(ClientSession session, ProtocolMessage message) {

        if (message == null || message.type == null) {
            return;
        }

        switch (message.type) {

            case HELLO -> loginHandler.handleHello(session, message);

            case CHAT -> messageRouter.handleChatMessage(session, message);

            case DISCONNECT -> loginHandler.handleDisconnect(session);

            default -> {
                /*
                 * Unsupported protocol messages are intentionally
                 * ignored until their corresponding server component
                 * is implemented.
                 */
            }
        }
    }

    /**
     * Stops the server safely.
     *
     * Closing ServerSocket interrupts accept().
     */
    public void stop() {

        synchronized (this) {

            if (!running) {
                return;
            }

            running = false;

            ServerSocket socket = serverSocket;

            if (socket != null && !socket.isClosed()) {
                closeServerSocket(socket);
            }
        }

        /*
         * Interrupt client workers so active client handlers
         * can terminate promptly.
         */
        clientExecutor.shutdownNow();

        System.out.println("ChatServer stopped");
    }

    public OnlineUserRegistry getRegistry() {
        return registry;
    }

    private void closeServerSocket(ServerSocket socket) {

        if (socket == null || socket.isClosed()) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
            // Server is already stopping.
        }
    }

    private void closeSocket(Socket socket) {

        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing else to do.
        }
    }
}