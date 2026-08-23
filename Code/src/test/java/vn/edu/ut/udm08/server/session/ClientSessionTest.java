package vn.edu.ut.udm08.server.session;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

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
}
