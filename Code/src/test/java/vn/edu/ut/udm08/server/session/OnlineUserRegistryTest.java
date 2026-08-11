package vn.edu.ut.udm08.server.session;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.UserProfile;
import java.net.Socket;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
class OnlineUserRegistryTest {
  @Test
  void rejectsNullAndAnonymousSessions() throws Exception {
    OnlineUserRegistry registry = new OnlineUserRegistry();
    assertFalse(registry.register(null));
    try (Socket socket = new Socket()) {
        ClientSession anonymousSession = ClientSession.createAnonymous(socket);
        assertFalse(registry.register(anonymousSession));
    }
  }
  @Test
  void registersAuthenticatedSession() throws Exception {
    OnlineUserRegistry registry = new OnlineUserRegistry();
    try (Socket socket = new Socket()) {
        ClientSession session = ClientSession.createAnonymous(socket);
        boolean authenticated = session.authenticate("user1", "01");
        boolean registered = registry.register(session);
        assertTrue(authenticated);
        assertTrue(registered);
    }
  }
  @Test
  void rejectsDuplicateUsernameIgnoringCase() throws Exception {
    OnlineUserRegistry registry = new OnlineUserRegistry();
    try (Socket socket1 = new Socket(); Socket socket2 = new Socket()) {
        ClientSession session1 = ClientSession.createAnonymous(socket1);
        ClientSession session2 = ClientSession.createAnonymous(socket2);
        boolean authenticated1 = session1.authenticate("user1", "01");
        boolean authenticated2 = session2.authenticate("USER1", "02");
        boolean registered1 = registry.register(session1);
        boolean registered2 = registry.register(session2);
        assertTrue(authenticated1);
        assertTrue(authenticated2);
        assertTrue(registered1);
        assertFalse(registered2);
    }
  }
  @Test
  void findsRegisteredSessionIgnoringCase() throws Exception {
    OnlineUserRegistry registry = new OnlineUserRegistry();
    try (Socket socket = new Socket()) {
      ClientSession session = ClientSession.createAnonymous(socket);
      assertTrue(session.authenticate("User1", "01"));
      assertTrue(registry.register(session));
      assertSame(session, registry.find("user1"));
      assertSame(session, registry.find("USER1"));
      assertNull(registry.find("unknown"));
      assertNull(registry.find(null));
    }
  }
  @Test
  void removesRegisteredSession() throws Exception {
    OnlineUserRegistry registry = new OnlineUserRegistry();
    try (Socket socket = new Socket()) {
      ClientSession session = ClientSession.createAnonymous(socket);
      assertTrue(session.authenticate("user1", "01"));
      assertTrue(registry.register(session));
      assertSame(session, registry.find("user1"));
      assertTrue(registry.remove(session));
      assertNull(registry.find("user1"));
      assertFalse(registry.remove(session));
    }
  }
  @Test
  void returnsOnlineUserSnapshot() throws Exception {
    OnlineUserRegistry registry = new OnlineUserRegistry();
    try (Socket socketBinh = new Socket(); Socket socketAn = new Socket()) {
      ClientSession binh = ClientSession.createAnonymous(socketBinh);
      ClientSession an = ClientSession.createAnonymous(socketAn);
      assertTrue(binh.authenticate("Binh", "avatar-02"));
      assertTrue(an.authenticate("An", "avatar-01"));
      assertTrue(registry.register(binh));
      assertTrue(registry.register(an));
      List<UserProfile> snapshot = registry.getOnlineUsers();
      assertEquals(2, snapshot.size());
      boolean foundAn = false;
      boolean foundBinh = false;
      for (UserProfile user : snapshot) {
        if (user.username.equals("An") && user.avatarId.equals("avatar-01")) {
          foundAn = true;
        }
        if (user.username.equals("Binh") && user.avatarId.equals("avatar-02")) {
          foundBinh = true;
        }
      }
      assertTrue(foundAn);
      assertTrue(foundBinh);
      assertTrue(registry.remove(an));
      assertEquals(2, snapshot.size());
      assertEquals(1, registry.getOnlineUsers().size());
    }
  }
}
