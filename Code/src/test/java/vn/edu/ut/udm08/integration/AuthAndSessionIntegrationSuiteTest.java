package vn.edu.ut.udm08.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.conversation.ConversationRegistry;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.server.search.UserSearchHandler;
import vn.edu.ut.udm08.server.service.UserLoginService;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.server.session.SessionValidator;
import vn.edu.ut.udm08.shared.dto.LoginRequest;
import vn.edu.ut.udm08.shared.dto.LoginResponse;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthAndSessionIntegrationSuiteTest {
    private UserRepository userRepository;
    private OnlineUserRegistry registry;
    private ConversationRegistry convRegistry;
    private LoginHandler loginHandler;
    private UserLoginService loginService;
    private UserSearchHandler searchHandler;

    @BeforeEach
    public void setUp() {
        String testDb = "jdbc:sqlite:target/test_st111_" + System.currentTimeMillis() + ".db";
        userRepository = vn.edu.ut.udm08.support.TestDatabase.repository(testDb);
        registry = new OnlineUserRegistry();
        convRegistry = new ConversationRegistry();
        loginHandler = new LoginHandler(registry, convRegistry);
        loginService = new UserLoginService(userRepository);
        searchHandler = new UserSearchHandler(userRepository);

        PasswordEncoder encoder = new PasswordEncoder();
        User u1 = new User();
        u1.setUsername("usera");
        u1.setPhoneNumber("0901111111");
        u1.setPasswordHash(encoder.encode("pass123"));
        u1.setAvatarType("avatar1");
        userRepository.save(u1);

        User u2 = new User();
        u2.setUsername("userb");
        u2.setPhoneNumber("0902222222");
        u2.setPasswordHash(encoder.encode("pass123"));
        u2.setAvatarType("avatar2");
        userRepository.save(u2);
    }
    @AfterEach
    public void tearDown() {
        userRepository.deleteAll();
    }
    @Test
    void testLogoutAndSessionInvalidation() throws Exception {
        try (TestConnection conn = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn.session, hello("usera", "avatar1")));
            conn.readMessage();
            conn.readMessage();
            assertTrue(conn.session.isAuthenticated());

            loginHandler.handleLogout(conn.session);
            ProtocolMessage logoutRes = conn.readMessage();
            assertEquals(MessageType.LOGOUT_OK, logoutRes.type);
            assertFalse(conn.session.isAuthenticated());
            assertNull(registry.find("usera"));

            SessionValidator validator = new SessionValidator();
            ProtocolMessage dummyReq = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            boolean isValid = validator.validate(conn.session, dummyReq);
            assertFalse(isValid);
        }
    }
    @Test
    void testSingleSessionKickScenarios() throws Exception {
        try (TestConnection m1 = new TestConnection();
             TestConnection m2 = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, m1.session, hello("usera", "avatar1")));
            m1.readMessage();
            m1.readMessage();
            assertTrue(m1.session.isAuthenticated());

            LoginRequest wrongRequest = new LoginRequest("0901111111", "wrongpass");
            LoginResponse failRes = loginService.login(m2.session, wrongRequest, loginHandler);
            assertFalse(failRes.isSuccess());
            assertTrue(m1.session.isAuthenticated());

            LoginRequest correctRequest = new LoginRequest("0901111111", "pass123");
            LoginResponse passRes = loginService.login(m2.session, correctRequest, loginHandler);
            assertTrue(passRes.isSuccess());

            ProtocolMessage kickedMsg = m1.readMessage();
            assertEquals(MessageType.SESSION_KICKED, kickedMsg.type);
            assertFalse(m1.session.isAuthenticated());
            assertEquals(m2.session, registry.find("usera"));
        }
    }
    @Test
    void testKickedSessionCannotCallProtectedApi() throws Exception {
        try (TestConnection m1 = new TestConnection();
             TestConnection m2 = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, m1.session, hello("usera", "avatar1")));
            m1.readMessage();
            m1.readMessage();
            assertTrue(m1.session.isAuthenticated());

            LoginRequest correctRequest = new LoginRequest("0901111111", "pass123");
            LoginResponse passRes = loginService.login(m2.session, correctRequest, loginHandler);
            assertTrue(passRes.isSuccess());

            ProtocolMessage kickedMsg = m1.readMessage();
            assertEquals(MessageType.SESSION_KICKED, kickedMsg.type);
            assertFalse(m1.session.isAuthenticated());

            SessionValidator validator = new SessionValidator();
            ProtocolMessage protectedReq = new ProtocolMessage(MessageType.HISTORY_REQUEST);
            protectedReq.messageId = "msg-kicked-test";
            boolean isValid = validator.validate(m1.session, protectedReq);
            assertFalse(isValid);
        }
    }
    @Test
    void testUserSearchIntegrationAndPrivacy() throws Exception {
        try (TestConnection conn = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn.session, hello("usera", "avatar1")));
            conn.readMessage();
            conn.readMessage();

            ProtocolMessage searchReq = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            searchReq.requestId = "req-search";
            searchReq.keyword = "userb";
            searchHandler.handleSearchRequest(conn.session, searchReq);

            ProtocolMessage searchRes = conn.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, searchRes.type);
            assertNotNull(searchRes.users);
            assertEquals(1, searchRes.users.size());

            UserProfile profile = searchRes.users.get(0);
            assertEquals("userb", profile.username);
            assertNotNull(profile.userId);
            assertNotNull(profile.displayName);
            assertNotNull(profile.avatarId);

            ProtocolMessage noMatchReq = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            noMatchReq.keyword = "unknownuserxyz";
            searchHandler.handleSearchRequest(conn.session, noMatchReq);
            ProtocolMessage noMatchRes = conn.readMessage();
            assertTrue(noMatchRes.users.isEmpty());

            ProtocolMessage blankReq = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            blankReq.keyword = "   ";
            searchHandler.handleSearchRequest(conn.session, blankReq);
            ProtocolMessage blankRes = conn.readMessage();
            assertTrue(blankRes.users.isEmpty());
        }
    }
    @Test
    void testSearchTooLongKeywordHandledSafely() throws Exception {
        try (TestConnection conn = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn.session, hello("usera", "avatar1")));
            conn.readMessage();
            conn.readMessage();

            ProtocolMessage longReq = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            longReq.keyword = "a".repeat(1000);
            searchHandler.handleSearchRequest(conn.session, longReq);

            ProtocolMessage longRes = conn.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, longRes.type);
            assertNotNull(longRes.users);
            assertTrue(longRes.users.isEmpty());
        }
    }
    @Test
    void testAccountSwitchingOnSameClient() throws Exception {
        try (TestConnection conn1 = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn1.session, hello("usera", "avatar1")));
            conn1.readMessage();
            conn1.readMessage();
            assertEquals("usera", conn1.session.getUsername());
            assertEquals(conn1.session, registry.find("usera"));

            loginHandler.handleLogout(conn1.session);
            conn1.readMessage();
            assertNull(registry.find("usera"));
            assertFalse(conn1.session.isAuthenticated());

            try (TestConnection conn2 = new TestConnection()) {
                assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn2.session, hello("userb", "avatar2")));
                conn2.readMessage();
                conn2.readMessage();
                assertEquals("userb", conn2.session.getUsername());
                assertEquals(conn2.session, registry.find("userb"));
                assertNull(registry.find("usera"));
            }
        }
    }
    @Test
    void testUnexpectedDisconnectCleanupAndReLogin() throws Exception {
        try (TestConnection conn1 = new TestConnection()) {
            assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn1.session, hello("usera", "avatar1")));
            conn1.readMessage();
            conn1.readMessage();
            assertEquals(conn1.session, registry.find("usera"));

            loginHandler.handleDisconnect(conn1.session);
            assertNull(registry.find("usera"));

            try (TestConnection conn2 = new TestConnection()) {
                assertTrue(vn.edu.ut.udm08.support.TrustedLogin.establish(loginHandler, conn2.session, hello("userb", "avatar1")));
                conn2.readMessage();
                conn2.readMessage();
                assertEquals(conn2.session, registry.find("userb"));
            }
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
