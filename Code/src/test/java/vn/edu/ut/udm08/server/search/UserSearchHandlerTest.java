package vn.edu.ut.udm08.server.search;

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
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserSearchHandlerTest {
    private UserRepository repository;
    private UserSearchHandler handler;

    @BeforeEach
    public void setUp() {
        String testDb = "jdbc:sqlite:target/test_user_search_" + System.currentTimeMillis() + ".db";
        repository = vn.edu.ut.udm08.support.TestDatabase.repository(testDb);
        handler = new UserSearchHandler(repository);

        User u1 = new User();
        u1.setUsername("alice");
        u1.setPhoneNumber("0900000001");
        u1.setPasswordHash("pass1");
        u1.setAvatarType("avatar1");
        repository.save(u1);

        User u2 = new User();
        u2.setUsername("bob");
        u2.setPhoneNumber("0900000002");
        u2.setPasswordHash("pass2");
        u2.setAvatarType("avatar2");
        repository.save(u2);

        User u3 = new User();
        u3.setUsername("charlie");
        u3.setPhoneNumber("0900000003");
        u3.setPasswordHash("pass3");
        u3.setAvatarType("avatar3");
        repository.save(u3);
    }
    @AfterEach
    public void tearDown() {
        repository.deleteAll();
    }
    @Test
    void testEmptyKeywordReturnsEmptyList() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-1";
            req.keyword = "  ";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertEquals("req-1", res.requestId);
            assertNotNull(res.users);
            assertTrue(res.users.isEmpty());
        }
    }
    @Test
    void testSearchByPhoneNumber() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-2";
            req.keyword = "0900000002";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(1, res.users.size());
            assertEquals("bob", res.users.get(0).username);
        }
    }
    @Test
    void testSearchByPhoneNumberWithSpaces() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-2b";
            req.keyword = "090 000 0002";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(1, res.users.size());
            assertEquals("bob", res.users.get(0).username);
        }
    }
    @Test
    void testSearchByEmail() throws Exception {
        User uEmail = new User();
        uEmail.setUsername("dave");
        uEmail.setPhoneNumber("0900000004");
        uEmail.setEmail("dave@example.com");
        uEmail.setPasswordHash("pass4");
        repository.save(uEmail);
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-3";
            req.keyword = "dave@example.com";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(1, res.users.size());
            assertEquals("dave", res.users.get(0).username);
        }
    }
    @Test
    void testSearchCaseInsensitive() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-4";
            req.keyword = "BOB";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(1, res.users.size());
            assertEquals("bob", res.users.get(0).username);
        }
    }
    @Test
    void testSearchRankingPriority() throws Exception {
        User rank1 = new User();
        rank1.setUsername("user_phone");
        rank1.setPhoneNumber("thanh");
        rank1.setPasswordHash("p");
        repository.save(rank1);

        User rank2 = new User();
        rank2.setUsername("thanh");
        rank2.setPhoneNumber("0911111111");
        rank2.setPasswordHash("p");
        repository.save(rank2);

        User rank3 = new User();
        rank3.setUsername("thanh le");
        rank3.setPhoneNumber("0922222222");
        rank3.setPasswordHash("p");
        repository.save(rank3);

        User rank4 = new User();
        rank4.setUsername("nguyen thanh huy");
        rank4.setPhoneNumber("0933333333");
        rank4.setPasswordHash("p");
        repository.save(rank4);

        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-5";
            req.keyword = "thanh";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(4, res.users.size());
            assertEquals("user_phone", res.users.get(0).username);
            assertEquals("thanh", res.users.get(1).username);
            assertEquals("thanh le", res.users.get(2).username);
            assertEquals("nguyen thanh huy", res.users.get(3).username);
        }
    }
    @Test
    void testSearchExcludesSelfByUserId() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-6";
            req.keyword = "a";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertTrue(res.users.stream().noneMatch(u -> String.valueOf(self.getId()).equals(u.userId)));
            assertTrue(res.users.stream().noneMatch(u -> "alice".equalsIgnoreCase(u.username)));
        }
    }
    @Test
    void testSearchLimitCappedAt20() throws Exception {
        for (int i = 10; i <= 35; i++) {
            User u = new User();
            u.setUsername("bulk_user_" + i);
            u.setPhoneNumber("09900000" + i);
            u.setPasswordHash("pass");
            repository.save(u);
        }
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-7";
            req.keyword = "bulk_user";
            req.limit = 100;
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(20, res.users.size());
        }
    }
    @Test
    void testResponseContainsOnlyPublicFields() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-8";
            req.keyword = "bob";
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertEquals(1, res.users.size());
            var profile = res.users.get(0);
            assertNotNull(profile.userId);
            assertNotNull(profile.username);
            assertNotNull(profile.displayName);
            assertNotNull(profile.avatarId);
        }
    }
    @Test
    void testSearchTooLongKeywordHandledSafely() throws Exception {
        try (TestConnection connection = new TestConnection()) {
            User self = repository.findByPhoneNumber("0900000001").orElseThrow();
            connection.session.authenticate(self);
            ProtocolMessage req = new ProtocolMessage(MessageType.USER_SEARCH_REQUEST);
            req.requestId = "req-long";
            req.keyword = "a".repeat(500);
            handler.handleSearchRequest(connection.session, req);
            ProtocolMessage res = connection.readMessage();
            assertEquals(MessageType.USER_SEARCH_RESPONSE, res.type);
            assertNotNull(res.users);
            assertTrue(res.users.isEmpty());
        }
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
