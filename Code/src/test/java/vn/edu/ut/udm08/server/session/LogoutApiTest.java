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
import vn.edu.ut.udm08.server.conversation.ConversationRegistry;
import vn.edu.ut.udm08.server.conversation.IConversationRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.ConvId;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class LogoutApiTest {
    @Test
    void handleLogoutRevokesSessionAndSendsLogoutOk() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        IConversationRegistry convRegistry = new ConversationRegistry();
        LoginHandler handler = new LoginHandler(registry, convRegistry);
        try (TestConnection connection = new TestConnection()) {
            boolean loggedIn = handler.handleHello(connection.session, hello("user1", "01"));
            assertTrue(loggedIn);
            connection.readMessage();
            connection.readMessage();
            assertNotNull(registry.find("user1"));
            assertEquals(1, convRegistry.getSessions(ConvId.PUBLIC_ROOM_ID).size());
            ProtocolMessage logoutMsg = new ProtocolMessage(MessageType.LOGOUT);
            boolean success = handler.handleLogout(connection.session, logoutMsg);
            assertTrue(success);
            ProtocolMessage logoutOk = connection.readMessage();
            assertEquals(MessageType.LOGOUT_OK, logoutOk.type);
            assertNull(registry.find("user1"));
            assertFalse(connection.session.isAuthenticated());
            assertTrue(convRegistry.getSessions(ConvId.PUBLIC_ROOM_ID).isEmpty());
        }
    }
    @Test
    void handleLogoutBroadcastsUpdatedOnlineListToOtherUsers() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection first = new TestConnection();
             TestConnection second = new TestConnection()) {
            assertTrue(handler.handleHello(first.session, hello("UserA", "01")));
            first.readMessage();
            first.readMessage();
            assertTrue(handler.handleHello(second.session, hello("UserB", "02")));
            second.readMessage();
            second.readMessage();
            first.readMessage();
            ProtocolMessage logoutMsg = new ProtocolMessage(MessageType.LOGOUT);
            handler.handleLogout(first.session, logoutMsg);
            ProtocolMessage logoutOk = first.readMessage();
            assertEquals(MessageType.LOGOUT_OK, logoutOk.type);
            ProtocolMessage listForSecond = second.readMessage();
            assertEquals(MessageType.USER_LIST, listForSecond.type);
            assertEquals(1, listForSecond.users.size());
            assertEquals("UserB", listForSecond.users.get(0).username);
            assertNull(registry.find("UserA"));
        }
    }
    @Test
    void handleLogoutIsIdempotent() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);
        try (TestConnection connection = new TestConnection()) {
            assertTrue(handler.handleHello(connection.session, hello("user1", "01")));
            connection.readMessage();
            connection.readMessage();
            ProtocolMessage logoutMsg = new ProtocolMessage(MessageType.LOGOUT);
            assertTrue(handler.handleLogout(connection.session, logoutMsg));
            assertTrue(handler.handleLogout(connection.session, logoutMsg));
        }
    }
    private ProtocolMessage hello(String username, String avatarId) {
        ProtocolMessage message = new ProtocolMessage(MessageType.HELLO);
        message.sender = username;
        message.avatarId = avatarId;
        return message;
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
