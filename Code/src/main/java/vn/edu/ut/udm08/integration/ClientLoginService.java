package vn.edu.ut.udm08.integration;
import java.io.IOException;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ChatListener;
public class ClientLoginService {
    private final ChatClient chatClient;
    public ClientLoginService() {
        this.chatClient = new ChatClient();
    }
    public ClientLoginService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    public void connectAndLogin(String host, int port, String username, String avatarId, ChatListener listener) throws IOException {
        chatClient.connect(host, port, username, avatarId, listener);
    }
    public void disconnect() {
        if (chatClient != null) {
            chatClient.disconnect();
        }
    }
    public ChatClient getChatClient() {
        return chatClient;
    }
}
