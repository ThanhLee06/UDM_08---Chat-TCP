package vn.edu.ut.udm08.server.session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.validation.UsernameValidator;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

public class ClientSession implements Runnable {

    public static final String ANONYMOUS_USER_ID = "anonymous";

    private final Socket socket;
    private final String sessionId;

    private BufferedReader reader;
    private PrintWriter writer;
    private volatile User user;
    private volatile String username;
    private String avatarId;
    private Consumer<ProtocolMessage> messageHandler;
    private Runnable disconnectHandler;

    private volatile boolean running;

    private ClientSession(Socket socket) {
        this.socket = socket;
        this.sessionId = java.util.UUID.randomUUID().toString();
    }

    public static ClientSession createAnonymous(Socket socket) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket != null");
        }
        return new ClientSession(socket);
    }

    public void setMessageHandler(Consumer<ProtocolMessage> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void setDisconnectHandler(Runnable disconnectHandler) {
        this.disconnectHandler = disconnectHandler;
    }

    @Override
    public void run() {
        if (!isConnected()) {
            return;
        }
        running = true;

        try {
            while (running && isConnected()) {
                ProtocolMessage message = readMessage();

                if (message == null) {
                    break;
                }

                if (messageHandler != null) {
                    try {
                        messageHandler.accept(message);
                    } catch (RuntimeException e) {
                        System.err.println("Client session message handling error: " + e.getMessage());
                    }
                }
            }
        }
        catch (IOException e) {
            if (isAuthenticated() && running) {
                System.err.println("Client session I/O error (" + getUsername() + "): " + e.getMessage());
            }
        }
        finally {
            close();
            if (disconnectHandler != null) {
                disconnectHandler.run();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isAuthenticated() {
        return user != null || username != null;
    }

    public boolean authenticate(User user) {
        if (user == null || user.getId() == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return false;
        }

        if (isAuthenticated() && this.user != null) {
            return false;
        }

        this.user = user;
        this.username = user.getUsername();
        this.avatarId = user.getAvatarPath();
        if (this.avatarId == null) {
            this.avatarId = "default";
        }

        return true;
    }

    public boolean authenticate(String username, String avatarId) {
        if (isAuthenticated()) {
            return false;
        }

        if (!UsernameValidator.isValid(username)) {
            return false;
        }

        if (avatarId == null || avatarId.isBlank()) {
            return false;
        }

        this.username = username;
        this.avatarId = avatarId;
        return true;
    }
    public void unauthenticate() {
        this.user = null;
        this.username = null;
        this.avatarId = null;
    }
    public ProtocolMessage readMessage() throws IOException {
        if (!isConnected()) {
            throw new IOException("Socket mat ket noi");
        }

        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        String json = vn.edu.ut.udm08.shared.protocol.JsonLineReader.read(reader);

        if (json == null) {
            return null;
        }

        try {
            return JsonUtil.fromJson(json);
        } catch (IOException e) {
            sendError("INVALID_MESSAGE", "Invalid JSON frame");
            throw e;
        }
    }

    public synchronized void sendMessage(ProtocolMessage message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Message != null");
        }

        if (!isConnected()) {
            throw new IOException("Socket mat ket noi");
        }

        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        vn.edu.ut.udm08.shared.protocol.SocketWrites.line(socket, writer, JsonUtil.toJson(message), 15000);
        java.util.logging.Logger.getLogger(ClientSession.class.getName()).info(
            "RESPONSE session=" + sessionId + " type=" + message.type +
            (message.errorCode == null ? "" : " code=" + message.errorCode));

        if (writer.checkError()) {
            throw new IOException("Khong the gui tin nhan");
        }
    }

    public void sendError(String errorCode, String errorMessage) {
        sendError(null, errorCode, errorMessage);
    }
    public void sendError(String requestId, String errorCode, String errorMessage) {
        ProtocolMessage msg = new ProtocolMessage(vn.edu.ut.udm08.shared.model.MessageType.ERROR);
        msg.requestId = requestId;
        msg.sender = "SERVER";
        msg.errorCode = errorCode;
        msg.errorMessage = errorMessage;
        msg.timestamp = System.currentTimeMillis();
        try {
            sendMessage(msg);
        } catch (IOException ignored) {
        }
    }
    public void kick(String reason) {
        ProtocolMessage msg = new ProtocolMessage(vn.edu.ut.udm08.shared.model.MessageType.SESSION_KICKED);
        msg.sender = "SERVER";
        msg.errorCode = "SESSION_KICKED";
        msg.errorMessage = reason != null ? reason : "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác";
        msg.timestamp = System.currentTimeMillis();
        try {
            sendMessage(msg);
            if (writer != null) {
                writer.flush();
            }
        } catch (IOException ignored) {
        }
        unauthenticate();
        java.util.concurrent.CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS).execute(this::close);
    }
    public String getSessionId() {
        return sessionId;
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public void close() {
        running = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        catch (IOException ignored) {
        }

        try {
            if (reader != null) {
                reader.close();
            }
        }
        catch (IOException ignored) {
        } finally {
            reader = null;
        }

        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    public String getUsername() {
        return username;
    }

    public String getAvatarId() {
        return avatarId;
    }

    public String getUserId() {
        if (user != null && user.getId() != null) {
            return user.getId().toString();
        }
        if (username != null && !username.isBlank()) {
            return username;
        }
        return ANONYMOUS_USER_ID;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        if (user != null) {
            this.user = user;
            if (this.username == null || this.username.isBlank()) {
                this.username = user.getUsername();
            }
        }
    }
}
