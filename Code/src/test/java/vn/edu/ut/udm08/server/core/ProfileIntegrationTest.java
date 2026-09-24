package vn.edu.ut.udm08.server.core;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.dto.AuthUserDto;
import vn.edu.ut.udm08.shared.dto.ProfileUpdate;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import vn.edu.ut.udm08.support.TestServer;
import static org.junit.jupiter.api.Assertions.*;
class ProfileIntegrationTest {
    @Test
    void profilePersistsAndAvatarIsAvailableToAnotherAuthenticatedClient() throws Exception {
        ChatServer server = TestServer.create();
        Thread thread = new Thread(() -> { try { server.start(); } catch (IOException ignored) {} });
        thread.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertTrue(server.isRunning());
        try (Peer alice = new Peer(server.getPort()); Peer bob = new Peer(server.getPort())) {
            alice.send(TestServer.login("alice"));
            alice.read(MessageType.AUTH_LOGIN_OK);
            bob.send(TestServer.login("bob"));
            bob.read(MessageType.AUTH_LOGIN_OK);
            ProfileUpdate update = new ProfileUpdate();
            update.displayName = "Thanh mới";
            var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", bytes);
            update.avatar = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
            alice.request(MessageType.PROFILE_UPDATE, JsonUtil.toJson(update));
            var saved = JsonUtil.fromJson(alice.read(MessageType.PROFILE_RESPONSE).content, AuthUserDto.class);
            assertEquals("Thanh mới", saved.getDisplayName());
            assertEquals("alice", saved.getUsername());
            assertTrue(saved.getAvatarPath().startsWith("avatar:"));
            bob.request(MessageType.AVATAR_GET, saved.getAvatarPath());
            assertArrayEquals(bytes.toByteArray(), Base64.getDecoder().decode(bob.read(MessageType.AVATAR_RESPONSE).content));
            bob.request(MessageType.PROFILE_GET, "alice");
            assertEquals("bob", JsonUtil.fromJson(bob.read(MessageType.PROFILE_RESPONSE).content, AuthUserDto.class).getUsername());
            update.displayName = " ";
            alice.request(MessageType.PROFILE_UPDATE, JsonUtil.toJson(update));
            assertEquals("PROFILE_ERROR", alice.read(MessageType.ERROR).errorCode);
            alice.request(MessageType.PROFILE_GET, "");
            assertEquals("Thanh mới", JsonUtil.fromJson(alice.read(MessageType.PROFILE_RESPONSE).content, AuthUserDto.class).getDisplayName());
            try (Peer reconnected = new Peer(server.getPort())) {
                reconnected.send(TestServer.login("alice"));
                var loggedIn = reconnected.read(MessageType.AUTH_LOGIN_OK);
                assertFalse(loggedIn.content.contains("passwordHash"));
                assertEquals(saved.getAvatarPath(), JsonUtil.fromJson(loggedIn.content, AuthUserDto.class).getAvatarPath());
            }
        } finally {
            server.stop();
            thread.join(3000);
        }
    }
    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        void send(ProtocolMessage message) { writer.println(JsonUtil.toJson(message)); }
        void request(MessageType type, String content) {
            ProtocolMessage message = new ProtocolMessage(type);
            message.requestId = UUID.randomUUID().toString();
            message.content = content;
            send(message);
        }
        ProtocolMessage read(MessageType type) throws IOException {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                assertNotNull(line);
                ProtocolMessage message = JsonUtil.fromJson(line);
                if (message.type == type) return message;
            }
            throw new IOException("Missing response " + type);
        }
        public void close() throws IOException { socket.close(); }
    }
}
