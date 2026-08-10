package vn.edu.ut.udm08.server.session;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
