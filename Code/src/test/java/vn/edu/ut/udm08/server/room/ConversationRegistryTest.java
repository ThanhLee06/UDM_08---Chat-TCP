package vn.edu.ut.udm08.server.room;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.session.ClientSession;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ConversationRegistryTest {

    private ConversationRegistry registry;
    private TestConnection aliceConnection;
    private TestConnection bobConnection;
    private TestConnection charlieConnection;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new ConversationRegistry();

        aliceConnection = new TestConnection("Alice", "01");
        bobConnection = new TestConnection("Bob", "02");
        charlieConnection = new TestConnection("Charlie", "03");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (aliceConnection != null) aliceConnection.close();
        if (bobConnection != null) bobConnection.close();
        if (charlieConnection != null) charlieConnection.close();
    }

    @Test
    public void testJoinAndGetSessions() {
        // 1. Join Alice & Bob vao conv-01
        assertTrue(registry.join("conv-01", aliceConnection.session));
        assertTrue(registry.join("conv-01", bobConnection.session));

        // 2. Kiem tra getSessions
        Set<ClientSession> sessions = registry.getSessions("conv-01");
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains(aliceConnection.session));
        assertTrue(sessions.contains(bobConnection.session));

        // 3. Re-join Alice vao conv-01 (Khong trung lap)
        assertFalse(registry.join("conv-01", aliceConnection.session));
        assertEquals(2, registry.getSessionCount("conv-01"));
    }

    @Test
    public void testLeave() {
        registry.join("conv-01", aliceConnection.session);
        registry.join("conv-01", bobConnection.session);

        // 1. Bob leave conv-01
        assertTrue(registry.leave("conv-01", bobConnection.session));

        // 2. Kiếm tra lai danh sach session
        Set<ClientSession> sessions = registry.getSessions("conv-01");
        assertEquals(1, sessions.size());
        assertTrue(sessions.contains(aliceConnection.session));
        assertFalse(sessions.contains(bobConnection.session));

        // 3. Leave lan 2 -> Tra ve false
        assertFalse(registry.leave("conv-01", bobConnection.session));
    }

    @Test
    public void testGetAllConvIds() {
        registry.join("conv-01", aliceConnection.session);
        registry.join("conv-02", aliceConnection.session);
        registry.join("conv-03", aliceConnection.session);

        registry.join("conv-02", bobConnection.session);

        // 1. Tra cuu theo ClientSession
        Set<String> aliceConvs = registry.getAllConvIds(aliceConnection.session);
        assertEquals(3, aliceConvs.size());
        assertTrue(aliceConvs.contains("conv-01"));
        assertTrue(aliceConvs.contains("conv-02"));
        assertTrue(aliceConvs.contains("conv-03"));

        // 2. Tra cuu theo username
        Set<String> aliceConvsByName = registry.getAllConvIds("Alice");
        assertEquals(3, aliceConvsByName.size());

        Set<String> bobConvsByName = registry.getAllConvIds("Bob");
        assertEquals(1, bobConvsByName.size());
        assertTrue(bobConvsByName.contains("conv-02"));
    }

    @Test
    public void testUnregisterSessionOnDisconnect() {
        registry.join("conv-01", aliceConnection.session);
        registry.join("conv-02", aliceConnection.session);
        registry.join("conv-01", bobConnection.session);

        // Disconnect Alice
        registry.unregisterSession(aliceConnection.session);

        // 1. conv-01 chi con Bob
        Set<ClientSession> conv1Sessions = registry.getSessions("conv-01");
        assertEquals(1, conv1Sessions.size());
        assertTrue(conv1Sessions.contains(bobConnection.session));

        // 2. conv-02 khong con session nao
        Set<ClientSession> conv2Sessions = registry.getSessions("conv-02");
        assertTrue(conv2Sessions.isEmpty());

        // 3. getAllConvIds của Alice tro thanh rong
        assertTrue(registry.getAllConvIds(aliceConnection.session).isEmpty());
    }

    @Test
    public void testConcurrentJoinLeaveThreadSafety() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String convId = "conv-" + (j % 5);
                        ClientSession session = (threadIdx % 2 == 0)
                                ? aliceConnection.session
                                : bobConnection.session;

                        registry.join(convId, session);
                        registry.getSessions(convId);
                        registry.getAllConvIds(session);
                        if (j % 3 == 0) {
                            registry.leave(convId, session);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Safe query after execution without exceptions
        assertNotNull(registry.getSessions("conv-0"));
    }

    private static class TestConnection implements AutoCloseable {
        private Socket clientSocket;
        private Socket serverSocket;
        private ClientSession session;

        private TestConnection(String username, String avatarId) throws IOException {
            InetAddress address = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, address)) {
                clientSocket = new Socket(address, listener.getLocalPort());
                serverSocket = listener.accept();
            }
            session = ClientSession.createAnonymous(serverSocket);
            session.authenticate(username, avatarId);
        }

        @Override
        public void close() throws IOException {
            session.close();
            clientSocket.close();
        }
    }
}
