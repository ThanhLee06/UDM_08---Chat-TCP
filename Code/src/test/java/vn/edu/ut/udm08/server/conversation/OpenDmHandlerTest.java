package vn.edu.ut.udm08.server.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.room.InMemoryConversationDao;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.OnlineUserRegistry;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.User;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử API Mở hoặc Tạo mới cuộc trò chuyện DM (ST-115).
 */
public class OpenDmHandlerTest {

    private InMemoryConversationDao conversationDao;
    private MockUserRepository userRepository;
    private OnlineUserRegistry registry;
    private OpenDmHandler handler;

    private TestClientConnection alice;
    private TestClientConnection bob;
    private TestClientConnection anon;

    @BeforeEach
    void setUp() throws Exception {
        conversationDao = new InMemoryConversationDao();
        userRepository = new MockUserRepository();
        registry = new OnlineUserRegistry();

        userRepository.addUser("Alice");
        userRepository.addUser("Bob");
        userRepository.addUser("Charlie");

        conversationDao.setUserAvatar("Alice", "avatar_alice.png");
        conversationDao.setUserAvatar("Bob", "avatar_bob.png");

        handler = new OpenDmHandler(conversationDao, userRepository, registry);

        alice = new TestClientConnection("Alice", "avatar_alice.png");
        bob = new TestClientConnection("Bob", "avatar_bob.png");
        anon = new TestClientConnection(null, null); // Chưa đăng nhập

        registry.register(alice.session);
        registry.register(bob.session);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (alice != null) alice.close();
        if (bob != null) bob.close();
        if (anon != null) anon.close();
    }

    @Test
    @DisplayName("Chưa từng chat -> Tạo cuộc trò chuyện DM mới với isNew = true")
    void testCreateNewDmSuccess() throws Exception {
        // Chưa có DM nào giữa Alice và Bob
        assertTrue(conversationDao.findDmBetween("Alice", "Bob").isEmpty());

        ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        req.requestId = "req-open-01";
        req.targetUserId = "Bob";

        handler.handleOpenDmRequest(alice.session, req);

        ProtocolMessage resp = alice.readMessage();
        assertNotNull(resp);
        assertEquals(MessageType.OPEN_DM_RESPONSE, resp.type);
        assertEquals("req-open-01", resp.requestId);
        assertNotNull(resp.convId);
        assertEquals("DM", resp.chatType);
        assertEquals("Bob", resp.displayName, "Hiển thị tên của đối phương");
        assertEquals("avatar_bob.png", resp.avatar, "Hiển thị avatar của đối phương");
        assertTrue(resp.isNew, "Lần đầu tạo DM thì isNew phải bằng true");

        // Đảm bảo đối tượng conversation tương thích với ST-097
        assertNotNull(resp.conversation);
        assertEquals(resp.convId, resp.conversation.convId);
        assertEquals("DM", resp.conversation.chatType);
        assertEquals("Bob", resp.conversation.displayName);
        assertEquals("avatar_bob.png", resp.conversation.avatar);

        // Kiểm tra trong DAO đã có DM
        Optional<String> dmInDao = conversationDao.findDmBetween("Alice", "Bob");
        assertTrue(dmInDao.isPresent());
        assertEquals(resp.convId, dmInDao.get());
        assertTrue(conversationDao.isMember(resp.convId, "Alice"));
        assertTrue(conversationDao.isMember(resp.convId, "Bob"));
    }

    @Test
    @DisplayName("Đã từng chat -> Mở lại ô chat cũ với isNew = false và cùng convId")
    void testOpenExistingDmSuccess() throws Exception {
        // Tạo trước 1 DM giữa Alice và Bob
        String existingConvId = conversationDao.getOrCreateDm("Alice", "Bob");

        // Alice bấm chat với Bob
        ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        req.requestId = "req-open-02";
        req.targetUserId = "Bob";

        handler.handleOpenDmRequest(alice.session, req);

        ProtocolMessage resp = alice.readMessage();
        assertNotNull(resp);
        assertEquals(MessageType.OPEN_DM_RESPONSE, resp.type);
        assertEquals(existingConvId, resp.convId, "Phải trả lại đúng convId cũ");
        assertFalse(resp.isNew, "Đã tồn tại trước đó thì isNew phải bằng false");
        assertEquals("Bob", resp.displayName);

        // Bob bấm chat lại với Alice
        ProtocolMessage reqFromBob = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        reqFromBob.requestId = "req-open-03";
        reqFromBob.targetUserId = "Alice";

        handler.handleOpenDmRequest(bob.session, reqFromBob);

        ProtocolMessage respToBob = bob.readMessage();
        assertNotNull(respToBob);
        assertEquals(MessageType.OPEN_DM_RESPONSE, respToBob.type);
        assertEquals(existingConvId, respToBob.convId, "Cả hai phía đều nhận cùng 1 convId duy nhất");
        assertFalse(respToBob.isNew);
        assertEquals("Alice", respToBob.displayName, "Bob nhìn thấy tên của Alice");
        assertEquals("avatar_alice.png", respToBob.avatar, "Bob nhìn thấy avatar của Alice");
    }

    @Test
    @DisplayName("Từ chối khi cố tình tự chat với chính mình")
    void testRejectSelfChat() throws Exception {
        ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        req.requestId = "req-self";
        req.targetUserId = "Alice"; // Tự chat với chính mình

        handler.handleOpenDmRequest(alice.session, req);

        ProtocolMessage err = alice.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("SELF_CHAT_NOT_ALLOWED", err.errorCode);
    }

    @Test
    @DisplayName("Từ chối khi targetUserId không tồn tại trong hệ thống")
    void testRejectUserNotFound() throws Exception {
        ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        req.requestId = "req-not-found";
        req.targetUserId = "UnknownUserXYZ";

        handler.handleOpenDmRequest(alice.session, req);

        ProtocolMessage err = alice.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("USER_NOT_FOUND", err.errorCode);
    }

    @Test
    @DisplayName("Từ chối khi targetUserId để trống")
    void testRejectEmptyTargetUserId() throws Exception {
        ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        req.requestId = "req-empty";
        req.targetUserId = "   ";

        handler.handleOpenDmRequest(alice.session, req);

        ProtocolMessage err = alice.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("BAD_REQUEST", err.errorCode);
    }

    @Test
    @DisplayName("Từ chối phiên chưa đăng nhập (UNAUTHORIZED)")
    void testRejectUnauthenticatedSession() throws Exception {
        ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
        req.requestId = "req-unauth";
        req.targetUserId = "Bob";

        handler.handleOpenDmRequest(anon.session, req);

        ProtocolMessage err = anon.readMessage();
        assertNotNull(err);
        assertEquals(MessageType.ERROR, err.type);
        assertEquals("UNAUTHORIZED", err.errorCode);
    }

    @Test
    @DisplayName("Chống đẻ trùng DM khi 2 người cùng bấm mở chat đồng thời")
    void testConcurrency_NoDuplicateDmBetweenUsers() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threads);

        AtomicReference<String> aliceConvId = new AtomicReference<>();
        AtomicReference<String> bobConvId = new AtomicReference<>();

        // Luồng 1: Alice bấm mở chat với Bob
        executor.submit(() -> {
            try {
                startSignal.await();
                ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
                req.requestId = "req-concurrent-alice";
                req.targetUserId = "Bob";
                handler.handleOpenDmRequest(alice.session, req);

                ProtocolMessage resp = alice.readMessage();
                if (resp != null) {
                    aliceConvId.set(resp.convId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneSignal.countDown();
            }
        });

        // Luồng 2: Bob bấm mở chat với Alice
        executor.submit(() -> {
            try {
                startSignal.await();
                ProtocolMessage req = new ProtocolMessage(MessageType.OPEN_DM_REQUEST);
                req.requestId = "req-concurrent-bob";
                req.targetUserId = "Alice";
                handler.handleOpenDmRequest(bob.session, req);

                ProtocolMessage resp = bob.readMessage();
                if (resp != null) {
                    bobConvId.set(resp.convId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneSignal.countDown();
            }
        });

        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertNotNull(aliceConvId.get());
        assertNotNull(bobConvId.get());
        assertEquals(aliceConvId.get(), bobConvId.get(), "Cả hai luồng phải trả về cùng 1 convId duy nhất");

        // Kiểm tra trong inbox của Alice chỉ có đúng 1 cuộc trò chuyện DM với Bob
        var aliceInbox = conversationDao.getInboxForUser("Alice");
        long dmCount = aliceInbox.stream().filter(c -> "DM".equals(c.chatType) && "Bob".equals(c.displayName)).count();
        assertEquals(1, dmCount, "Chỉ được tồn tại duy nhất 1 DM, không được đẻ trùng");
    }

    private static class MockUserRepository implements IUserRepository {
        private final Set<String> users = Collections.synchronizedSet(new HashSet<>());

        void addUser(String username) {
            users.add(username.toLowerCase());
        }

        @Override
        public boolean existsByUsername(String username) {
            if (username == null) return false;
            return users.contains(username.trim().toLowerCase());
        }

        @Override
        public boolean existsByPhoneNumber(String phoneNumber) { return false; }
        @Override
        public boolean existsByEmail(String email) { return false; }
        @Override
        public Optional<User> findByEmail(String email) { return Optional.empty(); }
        @Override
        public User save(User user) { return user; }
        @Override
        public Optional<User> findByPhoneNumber(String phoneNumber) { return Optional.empty(); }
        @Override
        public boolean updatePassword(String phoneNumber, String newPasswordHash) { return false; }
    }

    private static class TestClientConnection implements AutoCloseable {
        private final Socket clientSocket;
        private final Socket serverSocket;
        private final ClientSession session;
        private final BufferedReader reader;
        private final PrintWriter writer;

        private TestClientConnection(String username, String avatarId) throws IOException {
            InetAddress address = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, address)) {
                clientSocket = new Socket(address, listener.getLocalPort());
                serverSocket = listener.accept();
            }
            clientSocket.setSoTimeout(2000);
            session = ClientSession.createAnonymous(serverSocket);
            if (username != null) {
                session.authenticate(username, avatarId);
            }

            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        private ProtocolMessage readMessage() throws IOException {
            String line = reader.readLine();
            if (line == null) return null;
            return JsonUtil.fromJson(line);
        }

        @Override
        public void close() throws IOException {
            session.close();
            clientSocket.close();
        }
    }
}
