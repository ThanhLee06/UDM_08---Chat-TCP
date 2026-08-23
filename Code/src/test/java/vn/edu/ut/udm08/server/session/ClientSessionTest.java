package vn.edu.ut.udm08.server.session;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import static org.junit.jupiter.api.Assertions.*;

public class ClientSessionTest {
  @Test
  public void createsAnonymousSession() throws Exception {
    try (Socket socketA = new Socket()) {
      ClientSession sessionA = ClientSession.createAnonymous(socketA);
      assertNotNull(sessionA);
      assertFalse(sessionA.isAuthenticated());
      assertNull(sessionA.getUsername());
      assertNull(sessionA.getAvatarId());
    }
  }
  @Test
  public void createsAuthenticatedSession() throws Exception {
      try (Socket socketB = new Socket()) {
          ClientSession sessionB = ClientSession.createAnonymous(socketB);
          boolean authenticated = sessionB.authenticate("user1", "01");
          assertNotNull(sessionB);
          assertTrue(authenticated);
          assertTrue(sessionB.isAuthenticated());
          assertEquals("user1", sessionB.getUsername());
          assertEquals("01", sessionB.getAvatarId());
      }
  }
  @Test
  public void doesNotOverwriteAuthenticatedUser() throws Exception {
    try (Socket socketC = new Socket()) {
      ClientSession sessionC = ClientSession.createAnonymous(socketC);
      boolean firstAttempt = sessionC.authenticate("user1", "01");
      boolean secondAttempt = sessionC.authenticate("user2", "02");
      assertTrue(firstAttempt);
      assertFalse(secondAttempt);
      assertEquals("user1", sessionC.getUsername());
      assertEquals("01", sessionC.getAvatarId());
    }
  }
  @Test
  void rejectsInvalidIdentity() throws Exception {
      try (Socket socketD = new Socket()) {
          ClientSession sessionD = ClientSession.createAnonymous(socketD);
          boolean a = sessionD.authenticate(null, "01");
          boolean b = sessionD.authenticate("", "01");
          boolean c = sessionD.authenticate("user 1", "01");
          boolean d = sessionD.authenticate("user1", null);
          boolean e = sessionD.authenticate("user1", "");
          boolean f = sessionD.authenticate("user1", "   ");
          assertFalse(a);
          assertFalse(b);
          assertFalse(c);
          assertFalse(d);
          assertFalse(e);
          assertFalse(f);
          assertFalse(sessionD.isAuthenticated());
          assertNull(sessionD.getUsername());
          assertNull(sessionD.getAvatarId());
      }
    }
    @Test
    void rejectsNullSocketUsingAssertThrows() {
        assertThrows(IllegalArgumentException.class, () -> ClientSession.createAnonymous(null));
    }


    //===========================
    @Test
    void createsIndependentSessions() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientA = new Socket("localhost", serverSocket.getLocalPort());
             Socket clientB = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverA = serverSocket.accept();
             Socket serverB = serverSocket.accept()) {

            ClientSession sessionA = ClientSession.createAnonymous(serverA);
            ClientSession sessionB = ClientSession.createAnonymous(serverB);

            assertNotNull(sessionA);
            assertNotNull(sessionB);
            assertNotSame(sessionA, sessionB);

            assertFalse(sessionA.isAuthenticated());
            assertFalse(sessionB.isAuthenticated());
        }
    }

    @Test
    void keepsSessionStateIndependent() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientA = new Socket("localhost", serverSocket.getLocalPort());
             Socket clientB = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverA = serverSocket.accept();
             Socket serverB = serverSocket.accept()) {

            ClientSession sessionA = ClientSession.createAnonymous(serverA);
            ClientSession sessionB = ClientSession.createAnonymous(serverB);

            assertTrue(sessionA.authenticate("user1", "01"));
            assertFalse(sessionB.isAuthenticated());

            assertEquals("user1", sessionA.getUsername());
            assertNull(sessionB.getUsername());
        }
    }

    @Test
    void supportsMultipleConnectedSessions() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientA = new Socket("localhost", serverSocket.getLocalPort());
             Socket clientB = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverA = serverSocket.accept();
             Socket serverB = serverSocket.accept()) {

            ClientSession sessionA = ClientSession.createAnonymous(serverA);
            ClientSession sessionB = ClientSession.createAnonymous(serverB);

            assertTrue(sessionA.isConnected());
            assertTrue(sessionB.isConnected());
        }
    }

    @Test
    void closingOneSessionDoesNotAffectAnotherSession() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientA = new Socket("localhost", serverSocket.getLocalPort());
             Socket clientB = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverA = serverSocket.accept();
             Socket serverB = serverSocket.accept()) {

            ClientSession sessionA = ClientSession.createAnonymous(serverA);
            ClientSession sessionB = ClientSession.createAnonymous(serverB);

            assertTrue(sessionA.isConnected());
            assertTrue(sessionB.isConnected());

            sessionA.close();

            assertFalse(sessionA.isConnected());
            assertTrue(sessionB.isConnected());
        }
    }

    //========================
    @Test
    void shouldReadValidJsonMessage() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket client = new Socket("localhost", serverSocket.getLocalPort());
             Socket server = serverSocket.accept()) {

            ClientSession session = ClientSession.createAnonymous(server);

            ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
            message.sender = "alice";
            message.target = "bob";
            message.content = "Hello Bob";

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);

            writer.println(JsonUtil.toJson(message));

            ProtocolMessage received = session.readMessage();

            assertNotNull(received);
            assertEquals(MessageType.CHAT, received.type);
            assertEquals("alice", received.sender);
            assertEquals("bob", received.target);
            assertEquals("Hello Bob", received.content);
        }
    }

    @Test
    void shouldReadMultipleJsonLinesAsSeparateMessages() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket client = new Socket("localhost", serverSocket.getLocalPort());
             Socket server = serverSocket.accept()) {

            ClientSession session = ClientSession.createAnonymous(server);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);

            ProtocolMessage first = new ProtocolMessage(MessageType.CHAT);
            first.content = "First";

            ProtocolMessage second = new ProtocolMessage(MessageType.CHAT);
            second.content = "Second";

            writer.println(JsonUtil.toJson(first));
            writer.println(JsonUtil.toJson(second));

            ProtocolMessage receivedFirst = session.readMessage();
            ProtocolMessage receivedSecond = session.readMessage();

            assertEquals("First", receivedFirst.content);
            assertEquals("Second", receivedSecond.content);
        }
    }

    @Test
    void shouldPreserveUtf8Content() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
            Socket client = new Socket("localhost", serverSocket.getLocalPort());
            Socket server = serverSocket.accept()) {

            ClientSession session = ClientSession.createAnonymous(server);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);

            ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
            message.content = "Xin chào Việt Nam 👋";

            writer.println(JsonUtil.toJson(message));

            ProtocolMessage received = session.readMessage();

            assertEquals("Xin chào Việt Nam 👋", received.content);
        }
    }

    @Test
    void shouldSendMessageAsJsonLine() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
            Socket client = new Socket("localhost", serverSocket.getLocalPort());
            Socket server = serverSocket.accept()) {

            ClientSession session = ClientSession.createAnonymous(server);

            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

            ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
            message.sender = "alice";
            message.target = "bob";
            message.content = "Hello Bob";

            session.sendMessage(message);

            String json = reader.readLine();

            assertNotNull(json);

            ProtocolMessage received = JsonUtil.fromJson(json);

            assertEquals(MessageType.CHAT, received.type);
            assertEquals("alice", received.sender);
            assertEquals("bob", received.target);
            assertEquals("Hello Bob", received.content);
        }
    }

    @Test
    void shouldPreserveAllProtocolFieldsThroughJsonLine() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
            Socket client = new Socket("localhost", serverSocket.getLocalPort());
            Socket server = serverSocket.accept()) {

            ClientSession session = ClientSession.createAnonymous(server);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);

            ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
            message.messageId = "msg-001";
            message.sender = "alice";
            message.target = "bob";
            message.content = "Hello";
            message.avatarId = "avatar-01";
            message.timestamp = 123456789L;

            writer.println(JsonUtil.toJson(message));

            ProtocolMessage received = session.readMessage();

            assertEquals("msg-001", received.messageId);
            assertEquals("alice", received.sender);
            assertEquals("bob", received.target);
            assertEquals("Hello", received.content);
            assertEquals("avatar-01", received.avatarId);
            assertEquals(123456789L, received.timestamp);
        }
    }

    @Test
    void shouldRejectInvalidJsonSafely() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
            Socket client = new Socket("localhost", serverSocket.getLocalPort());
            Socket server = serverSocket.accept()) {

            ClientSession session = ClientSession.createAnonymous(server);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);

            writer.println("this-is-not-valid-json");

            assertThrows(IOException.class, session::readMessage);
        }
    }

    @Test
    void shouldRejectNullMessage() throws Exception {
        try (Socket socket = new Socket()) {
            ClientSession session = ClientSession.createAnonymous(socket);
            assertThrows(IllegalArgumentException.class, () -> session.sendMessage(null));
        }
    }


}
