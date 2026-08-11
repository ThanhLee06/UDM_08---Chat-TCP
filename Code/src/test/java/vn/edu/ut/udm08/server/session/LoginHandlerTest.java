package vn.edu.ut.udm08.server.session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginHandlerTest {
    @Test
    void acceptsHelloAndSendsHelloOkAndUserList() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection connection = new TestConnection()) {
            connection.sendHello("Thành", "01");
            ProtocolMessage hello = connection.session.readMessage();
            boolean loggedIn = handler.handleHello(connection.session, hello);
            ProtocolMessage helloOk = connection.readMessage();
            ProtocolMessage userList = connection.readMessage();
            assertTrue(loggedIn);
            assertEquals(MessageType.HELLO_OK, helloOk.type);
            assertEquals("Thành", helloOk.target);
            assertEquals(MessageType.USER_LIST, userList.type);
            assertEquals(1, userList.users.size());
            assertEquals("Thành", userList.users.get(0).username);
            assertNotNull(registry.find("thành"));
        }
    }
    @Test
    void rejectsInvalidUsername() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection connection = new TestConnection()) {
            ProtocolMessage hello = hello("user 1", "01");
            boolean loggedIn = handler.handleHello(connection.session, hello);
            ProtocolMessage error = connection.readMessage();
            assertFalse(loggedIn);
            assertEquals(MessageType.ERROR, error.type);
            assertEquals("INVALID_USERNAME", error.errorCode);
            assertFalse(connection.session.isAuthenticated());
            assertTrue(registry.getOnlineUsers().isEmpty());
        }
    }
    @Test
    void rejectsBlankAvatar() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection connection = new TestConnection()) {
            boolean loggedIn = handler.handleHello(connection.session, hello("user1", "   "));
            ProtocolMessage error = connection.readMessage();
            assertFalse(loggedIn);
            assertEquals("INVALID_AVATAR", error.errorCode);
            assertFalse(connection.session.isAuthenticated());
        }
    }
    @Test
    void rejectsDuplicateUsernameIgnoringCase() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection first = new TestConnection();
             TestConnection second = new TestConnection()) {
            assertTrue(handler.handleHello(first.session, hello("user1", "01")));
            first.readMessage();
            first.readMessage();
            boolean secondLogin = handler.handleHello(second.session, hello("USER1", "02"));
            ProtocolMessage error = second.readMessage();
            assertFalse(secondLogin);
            assertEquals(MessageType.ERROR, error.type);
            assertEquals("USERNAME_TAKEN", error.errorCode);
            assertEquals(1, registry.getOnlineUsers().size());
            assertEquals("user1", registry.find("USER1").getUsername());
            assertFalse(second.session.isConnected());
        }
    }
    @Test
    void broadcastsNewListWhenUserJoinsAndLeaves() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection first = new TestConnection();
            TestConnection second = new TestConnection()) {
            assertTrue(handler.handleHello(first.session, hello("An", "01")));
            first.readMessage();
            first.readMessage();
            assertTrue(handler.handleHello(second.session, hello("Bình", "02")));
            second.readMessage();
            ProtocolMessage listForSecond = second.readMessage();
            ProtocolMessage listForFirst = first.readMessage();
            assertEquals(2, listForFirst.users.size());
            assertEquals(2, listForSecond.users.size());
            handler.handleDisconnect(second.session);
            ProtocolMessage listAfterDisconnect = first.readMessage();
            assertEquals(MessageType.USER_LIST, listAfterDisconnect.type);
            assertEquals(1, listAfterDisconnect.users.size());
            assertTrue(containsUser(listAfterDisconnect, "An"));
            assertNull(registry.find("Bình"));
            assertFalse(second.session.isConnected());
        }
    }
    private ProtocolMessage hello(String username, String avatarId) {
        ProtocolMessage message = new ProtocolMessage(MessageType.HELLO);
        message.sender = username;
        message.avatarId = avatarId;
        return message;
    }
    private boolean containsUser(ProtocolMessage message, String username) {
        for (UserProfile user : message.users) {
            if (user.username.equals(username)) {
                return true;
            }
        }
        return false;
    }
    private static class TestConnection implements AutoCloseable {
        private Socket clientSocket;
        private Socket serverSocket;
        private ClientSession session;
        private BufferedReader reader;
        private PrintWriter writer;
        private TestConnection() throws IOException {
            InetAddress address = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, address)) {
                clientSocket = new Socket(address, listener.getLocalPort());
                serverSocket = listener.accept();
            }
            clientSocket.setSoTimeout(2000);
            session = ClientSession.createAnonymous(serverSocket);
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        private void sendHello(String username, String avatarId) throws IOException {
            ProtocolMessage message = new ProtocolMessage(MessageType.HELLO);
            message.sender = username;
            message.avatarId = avatarId;
            writer.println(JsonUtil.toJson(message));
        }
        private ProtocolMessage readMessage() throws IOException {
            String json = reader.readLine();
            assertNotNull(json);
            return JsonUtil.fromJson(json);
        }
        @Override
        public void close() throws IOException {
            session.close();
            clientSocket.close();
        }
    }
}
