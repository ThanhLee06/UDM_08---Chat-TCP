package vn.edu.ut.udm08.integration;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
public class LoginIntegrationServiceTest {
    @Test
    void testServiceInitialization() {
        LoginIntegrationService service = new LoginIntegrationService();
        assertNotNull(service.getOnlineUserRegistry());
        assertNotNull(service.getLoginHandler());
    }
    @Test
    void testServiceWithCustomRegistry() {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginIntegrationService service = new LoginIntegrationService(registry);
        assertNotNull(service.getOnlineUserRegistry());
        assertNotNull(service.getLoginHandler());
    }
    @Test
    void testServiceThrowsOnNullRegistry() {
        assertThrows(IllegalArgumentException.class, () -> new LoginIntegrationService(null));
    }
    @Test
    void testProcessLoginSuccess() throws Exception {
        LoginIntegrationService service = new LoginIntegrationService();
        try (TestSocketConnection conn = new TestSocketConnection()) {
            ProtocolMessage hello = createHelloMessage("ThanhUser", "avatar1");
            boolean loggedIn = service.processLogin(conn.session, hello);
            assertTrue(loggedIn);
            ProtocolMessage helloOk = conn.readMessage();
            ProtocolMessage userList = conn.readMessage();
            assertEquals(MessageType.HELLO_OK, helloOk.type);
            assertEquals("ThanhUser", helloOk.target);
            assertEquals(MessageType.USER_LIST, userList.type);
            assertEquals(1, userList.users.size());
            assertNotNull(service.getOnlineUserRegistry().find("ThanhUser"));
        }
    }
    @Test
    void testProcessLoginRejectsInvalidUsername() throws Exception {
        LoginIntegrationService service = new LoginIntegrationService();
        try (TestSocketConnection conn = new TestSocketConnection()) {
            ProtocolMessage hello = createHelloMessage("user name", "avatar1");
            boolean loggedIn = service.processLogin(conn.session, hello);
            assertFalse(loggedIn);
            ProtocolMessage error = conn.readMessage();
            assertEquals(MessageType.ERROR, error.type);
            assertEquals("INVALID_USERNAME", error.errorCode);
            assertFalse(conn.session.isAuthenticated());
        }
    }
    @Test
    void testProcessLoginRejectsDuplicateUsername() throws Exception {
        LoginIntegrationService service = new LoginIntegrationService();
        try (TestSocketConnection conn1 = new TestSocketConnection();
             TestSocketConnection conn2 = new TestSocketConnection()) {
            assertTrue(service.processLogin(conn1.session, createHelloMessage("UserA", "avatar1")));
            conn1.readMessage();
            conn1.readMessage();
            boolean secondLogin = service.processLogin(conn2.session, createHelloMessage("USERA", "avatar2"));
            assertFalse(secondLogin);
            ProtocolMessage error = conn2.readMessage();
            assertEquals(MessageType.ERROR, error.type);
            assertEquals("USERNAME_TAKEN", error.errorCode);
        }
    }
    @Test
    void testProcessDisconnect() throws Exception {
        LoginIntegrationService service = new LoginIntegrationService();
        try (TestSocketConnection conn1 = new TestSocketConnection();
             TestSocketConnection conn2 = new TestSocketConnection()) {
            service.processLogin(conn1.session, createHelloMessage("UserA", "avatar1"));
            conn1.readMessage();
            conn1.readMessage();
            service.processLogin(conn2.session, createHelloMessage("UserB", "avatar2"));
            conn2.readMessage();
            conn2.readMessage();
            conn1.readMessage();
            service.processDisconnect(conn2.session);
            ProtocolMessage updatedList = conn1.readMessage();
            assertEquals(MessageType.USER_LIST, updatedList.type);
            assertEquals(1, updatedList.users.size());
            assertNull(service.getOnlineUserRegistry().find("UserB"));
        }
    }
    @Test
    void testClientLoginService() {
        ClientLoginService service1 = new ClientLoginService();
        assertNotNull(service1.getChatClient());
        service1.disconnect();
        ChatClient client = new ChatClient();
        ClientLoginService service2 = new ClientLoginService(client);
        assertEquals(client, service2.getChatClient());
        service2.disconnect();
    }
    private ProtocolMessage createHelloMessage(String username, String avatarId) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.HELLO);
        msg.sender = username;
        msg.avatarId = avatarId;
        return msg;
    }
    private static class TestSocketConnection implements AutoCloseable {
        private Socket clientSocket;
        private Socket serverSocket;
        private ClientSession session;
        private BufferedReader reader;
        private PrintWriter writer;
        private TestSocketConnection() throws IOException {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
                clientSocket = new Socket(loopback, listener.getLocalPort());
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
