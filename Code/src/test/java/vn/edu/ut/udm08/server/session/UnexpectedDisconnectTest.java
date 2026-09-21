package vn.edu.ut.udm08.server.session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnexpectedDisconnectTest {
    @Test
    void testUnexpectedDisconnectCleansRegistryAndBroadcasts() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);

        try (TestConnection userA = new TestConnection();
             TestConnection userB = new TestConnection()) {
            assertTrue(handler.handleHello(userA.session, hello("userA", "01")));
            userA.readMessage();
            userA.readMessage();

            assertTrue(handler.handleHello(userB.session, hello("userB", "02")));
            userB.readMessage();
            userB.readMessage();
            userA.readMessage();

            handler.handleDisconnect(userA.session);
            assertNull(registry.find("userA"));
            assertFalse(userA.session.isAuthenticated());

            ProtocolMessage updatedList = userB.readMessage();
            assertEquals(MessageType.USER_LIST, updatedList.type);
            assertEquals(1, updatedList.users.size());
            assertEquals("userB", updatedList.users.get(0).username);
        }
    }
    @Test
    void testRaceConditionReconnectionPreservesNewSession() throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();
        LoginHandler handler = new LoginHandler(registry);

        try (TestConnection s1 = new TestConnection();
             TestConnection s2 = new TestConnection()) {
            assertTrue(handler.handleHello(s1.session, hello("alice", "01")));
            s1.readMessage();
            s1.readMessage();
            assertEquals(s1.session, registry.find("alice"));

            assertTrue(handler.handleHello(s2.session, hello("alice", "02")));
            s2.readMessage();
            s2.readMessage();

            handler.handleDisconnect(s1.session);
            assertEquals(s2.session, registry.find("alice"));
            assertTrue(registry.find("alice").isAuthenticated());
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
