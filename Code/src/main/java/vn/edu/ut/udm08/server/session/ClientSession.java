package vn.edu.ut.udm08.server.session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public class ClientSession {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private String avatarId;
    private ClientSession(Socket socket) {
        this.socket = socket;
    }
    public static ClientSession createAnonymous(Socket socket) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket != null");
        }
        return new ClientSession(socket);
    }
    public boolean isAuthenticated() {
        return username != null;
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
    public ProtocolMessage readMessage() throws IOException {
        if (!isConnected()) {
            throw new IOException("Socket mat ket noi");
        }
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
        }
        String json = reader.readLine();
        if (json == null) {
            return null;
        }
        return JsonUtil.fromJson(json);
    }
    public void sendMessage(ProtocolMessage message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Message != null");
        }
        if (!isConnected()) {
            throw new IOException("Socket mat ket noi");
        }
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        writer.println(JsonUtil.toJson(message));
        if (writer.checkError()) {
            throw new IOException("Khong the gui tin nhan");
        }
    }
    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {}
    }
    public String getUsername() {
        return username;
    }
    public String getAvatarId() {
        return avatarId;
    }
}
