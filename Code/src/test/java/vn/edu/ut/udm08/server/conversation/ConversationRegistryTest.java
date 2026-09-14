package vn.edu.ut.udm08.server.conversation;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.session.ClientSession;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class ConversationRegistryTest {
    @Test
    void rejectsInvalidConvIdAndUnauthenticatedSession() throws Exception {
        IConversationRegistry registry = new ConversationRegistry();
        assertFalse(registry.join(null, null));
        assertFalse(registry.join("", null));
        assertFalse(registry.join("GENERAL", null));
        try (Socket socket = new Socket()) {
            ClientSession session = ClientSession.createAnonymous(socket);
            assertFalse(registry.join("GENERAL", session));
            assertFalse(registry.join(null, session));
            assertFalse(registry.join("  ", session));
        }
    }
    @Test
    void joinsAndLeavesSessionSuccessfully() throws Exception {
        IConversationRegistry registry = new ConversationRegistry();
        try (Socket socket = new Socket()) {
            ClientSession session = ClientSession.createAnonymous(socket);
            assertTrue(session.authenticate("alice", "avatar1"));
            assertTrue(registry.join("conv-100", session));
            assertTrue(registry.isMember("conv-100", session));
            assertEquals(1, registry.getOnlineCount("conv-100"));
            assertEquals(1, registry.getActiveConversationCount());
            List<ClientSession> activeList = registry.getSessions("conv-100");
            assertEquals(1, activeList.size());
            assertSame(session, activeList.get(0));
            Set<ClientSession> sessionSet = registry.getSessionSet("conv-100");
            assertTrue(sessionSet.contains(session));
            assertTrue(registry.leave("conv-100", session));
            assertFalse(registry.isMember("conv-100", session));
            assertEquals(0, registry.getOnlineCount("conv-100"));
            assertEquals(0, registry.getActiveConversationCount());
            assertFalse(registry.leave("conv-100", session));
        }
    }
    @Test
    void supportsMultipleSessionsInSameConversation() throws Exception {
        IConversationRegistry registry = new ConversationRegistry();
        try (Socket s1 = new Socket(); Socket s2 = new Socket()) {
            ClientSession alice = ClientSession.createAnonymous(s1);
            ClientSession bob = ClientSession.createAnonymous(s2);
            assertTrue(alice.authenticate("alice", "avatar1"));
            assertTrue(bob.authenticate("bob", "avatar2"));
            assertTrue(registry.join("conv-group", alice));
            assertTrue(registry.join("conv-group", bob));
            assertEquals(2, registry.getOnlineCount("conv-group"));
            assertTrue(registry.isUserOnlineInConv("conv-group", "ALICE"));
            assertTrue(registry.isUserOnlineInConv("conv-group", "bob"));
            assertFalse(registry.isUserOnlineInConv("conv-group", "charlie"));
            List<ClientSession> sessions = registry.getSessions("conv-group");
            assertEquals(2, sessions.size());
            assertTrue(sessions.contains(alice));
            assertTrue(sessions.contains(bob));
        }
    }
    @Test
    void tracksAllConvIdsForSessionAndUser() throws Exception {
        IConversationRegistry registry = new ConversationRegistry();
        try (Socket s = new Socket()) {
            ClientSession alice = ClientSession.createAnonymous(s);
            assertTrue(alice.authenticate("alice", "avatar1"));
            registry.join("GENERAL", alice);
            registry.join("ROOM_JAVA", alice);
            registry.join("ROOM_DEV", alice);
            List<String> convsForSession = registry.getAllConvIds(alice);
            assertEquals(3, convsForSession.size());
            assertTrue(convsForSession.contains("GENERAL"));
            assertTrue(convsForSession.contains("ROOM_JAVA"));
            assertTrue(convsForSession.contains("ROOM_DEV"));
            List<String> convsForUser = registry.getAllConvIdsForUser("Alice");
            assertEquals(3, convsForUser.size());
        }
    }
    @Test
    void removesSessionFromAllConversationsOnDisconnect() throws Exception {
        IConversationRegistry registry = new ConversationRegistry();
        try (Socket s1 = new Socket(); Socket s2 = new Socket()) {
            ClientSession alice = ClientSession.createAnonymous(s1);
            ClientSession bob = ClientSession.createAnonymous(s2);
            assertTrue(alice.authenticate("alice", "avatar1"));
            assertTrue(bob.authenticate("bob", "avatar2"));
            registry.join("conv-1", alice);
            registry.join("conv-2", alice);
            registry.join("conv-1", bob);
            assertEquals(2, registry.getOnlineCount("conv-1"));
            assertEquals(1, registry.getOnlineCount("conv-2"));
            registry.removeSessionFromAll(alice);
            assertEquals(1, registry.getOnlineCount("conv-1"));
            assertEquals(0, registry.getOnlineCount("conv-2"));
            assertFalse(registry.isMember("conv-1", alice));
            assertTrue(registry.isMember("conv-1", bob));
            assertEquals(1, registry.getActiveConversationCount());
        }
    }
    @Test
    void threadSafetyConcurrentAccess() throws Exception {
        IConversationRegistry registry = new ConversationRegistry();
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        try (Socket s = new Socket()) {
            ClientSession session = ClientSession.createAnonymous(s);
            session.authenticate("concurrentUser", "avatar1");
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String convId = "conv-" + (i % 5);
                            registry.join(convId, session);
                            registry.getSessions(convId);
                            registry.isMember(convId, session);
                            if (threadId % 2 == 0) {
                                registry.leave(convId, session);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();
        }
    }
}
