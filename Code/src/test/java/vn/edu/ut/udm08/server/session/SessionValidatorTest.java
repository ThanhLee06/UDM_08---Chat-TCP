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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionValidatorTest {
    @Test
    void testNullOrInvalidMessageReturnsFalse() throws Exception {
        SessionValidator validator = new SessionValidator();
        try (TestConnection connection = new TestConnection()) {
            assertFalse(validator.validate(connection.session, null));
            ProtocolMessage emptyMsg = new ProtocolMessage();
            assertFalse(validator.validate(connection.session, emptyMsg));
        }
    }
    @Test
    void testHelloAndDisconnectAllowedWithoutAuth() throws Exception {
        SessionValidator validator = new SessionValidator();
        try (TestConnection connection = new TestConnection()) {
            ProtocolMessage hello = new ProtocolMessage(MessageType.HELLO);
            assertTrue(validator.validate(connection.session, hello));

            ProtocolMessage disconnect = new ProtocolMessage(MessageType.DISCONNECT);
            assertTrue(validator.validate(connection.session, disconnect));
        }
    }
    @Test
    void testBusinessRequestsBlockedWhenUnauthenticated() throws Exception {
        SessionValidator validator = new SessionValidator();
        try (TestConnection connection = new TestConnection()) {
            ProtocolMessage chat = new ProtocolMessage(MessageType.CHAT);
            chat.messageId = "msg-101";

            assertFalse(validator.validate(connection.session, chat));
            ProtocolMessage err = connection.readMessage();
            assertEquals(MessageType.ERROR, err.type);
            assertEquals("UNAUTHORIZED", err.errorCode);
            assertEquals("msg-101", err.messageId);

            ProtocolMessage logout = new ProtocolMessage(MessageType.LOGOUT);
            assertFalse(validator.validate(connection.session, logout));
        }
    }
    @Test
    void testAuthenticatedSessionAllowed() throws Exception {
        SessionValidator validator = new SessionValidator();
        try (TestConnection connection = new TestConnection()) {
            connection.session.authenticate("alice", "avatar1");

            ProtocolMessage chat = new ProtocolMessage(MessageType.CHAT);
            assertTrue(validator.validate(connection.session, chat));

            ProtocolMessage logout = new ProtocolMessage(MessageType.LOGOUT);
            assertTrue(validator.validate(connection.session, logout));
        }
    }
    @Test
    void testRevokedSessionBlocked() throws Exception {
        SessionValidator validator = new SessionValidator();
        try (TestConnection connection = new TestConnection()) {
            connection.session.authenticate("alice", "avatar1");
            assertTrue(validator.validate(connection.session, new ProtocolMessage(MessageType.CHAT)));

            connection.session.unauthenticate();
            assertFalse(validator.validate(connection.session, new ProtocolMessage(MessageType.CHAT)));
            ProtocolMessage err = connection.readMessage();
            assertEquals(MessageType.ERROR, err.type);
            assertEquals("UNAUTHORIZED", err.errorCode);
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
