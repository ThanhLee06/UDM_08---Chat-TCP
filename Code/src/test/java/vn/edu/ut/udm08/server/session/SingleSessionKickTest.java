package vn.edu.ut.udm08.server.session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.conversation.ConversationRegistry;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.server.service.UserLoginService;
import vn.edu.ut.udm08.shared.dto.LoginRequest;
import vn.edu.ut.udm08.shared.dto.LoginResponse;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleSessionKickTest {
    @Test
    void testWrongPasswordDoesNotKickExistingSession() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler loginHandler = new LoginHandler(registry);
        IUserRepository userRepository = new UserRepository();
        UserLoginService loginService = new UserLoginService(userRepository);

        try (TestConnection m1 = new TestConnection();
             TestConnection m2 = new TestConnection()) {
            boolean m1Success = loginHandler.handleHello(m1.session, hello("user108", "01"));
            assertTrue(m1Success);
            assertNotNull(registry.find("user108"));

            LoginRequest wrongRequest = new LoginRequest("0900000108", "wrong_password");
            LoginResponse response = loginService.login(m2.session, wrongRequest, loginHandler);
            assertFalse(response.isSuccess());

            assertNotNull(registry.find("user108"));
            assertTrue(m1.session.isConnected());
            assertTrue(m1.session.isAuthenticated());
        }
    }
    @Test
    void testCorrectPasswordKicksExistingSessionWithNotification() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        ConversationRegistry convRegistry = new ConversationRegistry();
        LoginHandler loginHandler = new LoginHandler(registry, convRegistry);

        try (TestConnection m1 = new TestConnection();
             TestConnection m2 = new TestConnection()) {
            assertTrue(loginHandler.handleHello(m1.session, hello("alice108", "01")));
            m1.readMessage();
            m1.readMessage();
            assertNotNull(registry.find("alice108"));

            assertTrue(loginHandler.handleHello(m2.session, hello("alice108", "02")));
            m2.readMessage();
            m2.readMessage();

            ProtocolMessage kickedMsg = m1.readMessage();
            assertEquals(MessageType.SESSION_KICKED, kickedMsg.type);
            assertEquals("SESSION_KICKED", kickedMsg.errorCode);
            assertFalse(m1.session.isAuthenticated());

            assertEquals(m2.session, registry.find("alice108"));
            assertTrue(m2.session.isAuthenticated());
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
        private ClientSession session;
        private BufferedReader reader;
        private TestConnection() throws IOException {
            InetAddress address = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, address)) {
                clientSocket = new Socket(address, listener.getLocalPort());
                Socket serverSocket = listener.accept();
                clientSocket.setSoTimeout(2000);
                session = ClientSession.createAnonymous(serverSocket);
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            }
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
