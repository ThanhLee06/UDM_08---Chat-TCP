package vn.edu.ut.udm08.server.session;
import java.net.Socket;
public class ClientSession {
    private Socket socket;
    private String username;
    private String avatarId;
    private ClientSession(Socket socket) {
        this.socket = socket;
    }
    public static ClientSession createAnonymous(Socket socket) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
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
    public String getUsername() {
        return username;
    }
    public String getAvatarId() {
        return avatarId;
    }
}
