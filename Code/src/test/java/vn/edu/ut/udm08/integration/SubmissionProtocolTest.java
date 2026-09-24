package vn.edu.ut.udm08.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import vn.edu.ut.udm08.server.auth.EmailOtpService;
import vn.edu.ut.udm08.server.core.*;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.shared.dto.*;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.protocol.*;
import vn.edu.ut.udm08.support.TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class SubmissionProtocolTest {
    @TempDir Path directory;
    private ChatServer server;
    private Thread thread;
    private ServerConfig config;
    private UserRepository users;
    private final Map<String, String> mail = new ConcurrentHashMap<>();
    private final MutableClock clock = new MutableClock();
    private static final String PASSWORD = "DemoPass123!";

    @BeforeEach void start() throws Exception {
        Properties p = new Properties(); p.setProperty("server.port", "0");
        p.setProperty("db.url", "jdbc:sqlite:" + directory.resolve("server.db"));
        config = ServerConfig.fromProperties(p);
        users = TestDatabase.repository(config.getDbUrl());
        String hash = new vn.edu.ut.udm08.shared.security.PasswordEncoder().encode(PASSWORD);
        int i = 0;
        for (String name : List.of("alice", "bob", "charlie")) {
            User user = new User(); user.setUsername(name); user.setEmail(name + "@example.test");
            user.setPhoneNumber("090000000" + (++i)); user.setPasswordHash(hash);
            user.setAvatarType("PRESET"); user.setAvatarPath("avatar" + i); users.save(user);
        }
        launchServer();
    }
    private void launchServer() throws Exception {
        server = new ChatServer(config, new EmailOtpService(mail::put, clock));
        thread = new Thread(() -> { try { server.start(); } catch (IOException e) { throw new AssertionError(e); } });
        thread.setDaemon(true); thread.start();
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!server.isRunning() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(server.isRunning());
    }
    @AfterEach void stop() throws Exception { if (server != null) server.stop(); if (thread != null) thread.join(2000); }

    @Test void helloCannotImpersonateOrKickExistingUser() throws Exception {
        try (Wire alice = new Wire(); Wire forged = new Wire()) {
            alice.login("alice", PASSWORD);
            ProtocolMessage hello = new ProtocolMessage(MessageType.HELLO); hello.sender = "alice"; hello.avatarId = "avatar1";
            assertEquals("AUTH_REQUIRED", forged.request(hello, MessageType.ERROR).errorCode);
            assertEquals(MessageType.CHAT_OK, alice.request(chat("room:public", "hello"), MessageType.CHAT_OK).type);
            assertEquals(1, server.getRegistry().getSessions().size());
        }
    }
    @Test void wrongPasswordDoesNotKickButCorrectPasswordDoes() throws Exception {
        try (Wire alice = new Wire(); Wire other = new Wire()) {
            alice.login("alice", PASSWORD);
            ProtocolMessage bad = auth("alice", "WrongPass123!");
            assertEquals("LOGIN_FAILED", other.request(bad, MessageType.ERROR).errorCode);
            alice.request(chat("room:public", "still online"), MessageType.CHAT_OK);
            other.login("alice", PASSWORD);
            assertEquals(MessageType.SESSION_KICKED, alice.await(MessageType.SESSION_KICKED, null).type);
        }
    }
    @Test void registerResendWrongOtpThenSuccessNeverExposesHash() throws Exception {
        try (Wire client = new Wire()) {
            ProtocolMessage init = message(MessageType.AUTH_REGISTER_INIT, new RegisterInitRequest("newuser", "0901234567", "newuser@example.test", PASSWORD, "PRESET", "avatar2"));
            var pending = client.request(init, MessageType.AUTH_REGISTER_OTP_REQUIRED);
            String id = JsonUtil.fromJson(pending.content, RegisterResponse.class).getRegistrationId();
            assertFalse(users.existsByEmail("newuser@example.test"));
            assertNotNull(id);
            ProtocolMessage wrong = message(MessageType.AUTH_REGISTER_VERIFY_OTP, new RegisterOtpVerifyRequest(id, "invalid"));
            assertEquals("VERIFY_FAILED", client.request(wrong, MessageType.ERROR).errorCode);
            clock.now = clock.now.plusSeconds(61);
            ProtocolMessage resend = new ProtocolMessage(MessageType.AUTH_REGISTER_RESEND_OTP); resend.content = id;
            var resent = client.request(resend, MessageType.AUTH_REGISTER_OTP_REQUIRED);
            assertEquals(id, JsonUtil.fromJson(resent.content, RegisterResponse.class).getRegistrationId());
            ProtocolMessage verify = message(MessageType.AUTH_REGISTER_VERIFY_OTP, new RegisterOtpVerifyRequest(id, mail.get("newuser@example.test")));
            var registered = client.request(verify, MessageType.AUTH_REGISTER_OK);
            assertFalse(registered.content.contains("passwordHash"));
            assertFalse(registered.content.contains("$2a$"));
        }
        try (Wire differentClient = new Wire()) { differentClient.login("newuser", PASSWORD); }
    }
    @Test void resetPasswordThroughTcpPersistsAcrossConnections() throws Exception {
        try (Wire client = new Wire()) {
            client.request(message(MessageType.AUTH_FORGOT_INIT, new ForgotInitRequest("alice@example.test")), MessageType.AUTH_FORGOT_OTP_REQUIRED);
            client.request(message(MessageType.AUTH_FORGOT_RESET, new ForgotResetRequest("alice@example.test", mail.get("alice@example.test"), "NewPass123!")), MessageType.AUTH_FORGOT_OK);
        }
        try (Wire client = new Wire()) {
            client.request(auth("alice", PASSWORD), MessageType.ERROR);
            client.login("alice", "NewPass123!");
        }
    }
    @Test void replyForwardEmojiAvatarAndHistorySurviveServerRestart() throws Exception {
        String dm = ConvId.forDm("alice", "bob");
        ProtocolMessage original = chat(dm, "Xin chào 😀 tiếng Việt"); original.target = "bob";
        ProtocolMessage reply = chat(dm, "Trả lời 👍"); reply.target = "alice";
        reply.replyToMessageId = original.messageId;
        try (Wire alice = new Wire(); Wire bob = new Wire()) {
            alice.login("alice", PASSWORD); bob.login("bob", PASSWORD);
            alice.request(original, MessageType.CHAT_OK);
            ProtocolMessage received = bob.await(MessageType.CHAT, original.messageId);
            assertEquals(original.content, received.content); assertEquals("avatar1", received.avatarId);
            bob.request(reply, MessageType.CHAT_OK);
            ProtocolMessage quoted = alice.await(MessageType.CHAT, reply.messageId);
            assertEquals(original.content, quoted.replyToContent); assertEquals("alice", quoted.replyToSender);
            ProtocolMessage forwarded = chat(ConvId.forDm("bob", "charlie"), "spoofed content");
            forwarded.target = "charlie"; forwarded.forwardFromMessageId = original.messageId;
            bob.request(forwarded, MessageType.CHAT_OK);
            try (Wire charlie = new Wire()) {
                charlie.login("charlie", PASSWORD);
                ProtocolMessage history = new ProtocolMessage(MessageType.HISTORY_REQUEST); history.convId = forwarded.convId;
                var page = charlie.request(history, MessageType.HISTORY_RESPONSE);
                assertEquals(original.content, page.messages.get(0).content);
                assertTrue(page.messages.get(0).isForwarded);
            }
        }
        server.stop(); thread.join(2000); launchServer();
        try (Wire bob = new Wire()) {
            bob.login("bob", PASSWORD);
            ProtocolMessage history = new ProtocolMessage(MessageType.HISTORY_REQUEST); history.convId = dm;
            var page = bob.request(history, MessageType.HISTORY_RESPONSE);
            assertEquals(2, page.messages.size());
            assertTrue(page.messages.stream().anyMatch(m -> original.content.equals(m.replyToContent)));
        }
    }
    @Test void unrelatedUserCannotReadOrSendAnotherDm() throws Exception {
        try (Wire alice = new Wire(); Wire charlie = new Wire()) {
            alice.login("alice", PASSWORD); charlie.login("charlie", PASSWORD);
            String dm = ConvId.forDm("alice", "bob");
            ProtocolMessage sent = chat(dm, "private"); sent.target = "bob"; alice.request(sent, MessageType.CHAT_OK);
            ProtocolMessage history = new ProtocolMessage(MessageType.HISTORY_REQUEST); history.convId = dm;
            assertEquals("NOT_A_MEMBER", charlie.request(history, MessageType.ERROR).errorCode);
            ProtocolMessage forged = chat(dm, "forged"); forged.target = "bob";
            assertEquals("NOT_A_MEMBER", charlie.request(forged, MessageType.ERROR).errorCode);
        }
    }
    @Test void partialFrameAndAbruptDisconnectDoNotStopOtherClients() throws Exception {
        try (Wire alice = new Wire()) {
            alice.login("alice", PASSWORD);
            try (Socket bad = new Socket("127.0.0.1", server.getPort())) {
                bad.getOutputStream().write("{\"type\":\"HELLO\"}".getBytes(StandardCharsets.UTF_8));
                bad.shutdownOutput();
            }
            alice.request(chat("room:public", "after disconnect"), MessageType.CHAT_OK);
            assertTrue(server.isRunning());
        }
    }
    private static ProtocolMessage auth(String name, String password) { return message(MessageType.AUTH_LOGIN, new AuthLoginRequest(name + "@example.test", password)); }
    private static ProtocolMessage message(MessageType type, Object payload) { ProtocolMessage m = new ProtocolMessage(type); m.content = JsonUtil.toJson(payload); return m; }
    private static ProtocolMessage chat(String conv, String content) { ProtocolMessage m = new ProtocolMessage(MessageType.CHAT); m.convId = conv; m.content = content; m.messageId = UUID.randomUUID().toString(); return m; }
    private final class Wire implements AutoCloseable {
        final Socket socket = new Socket("127.0.0.1", server.getPort());
        final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        final PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        String name;
        Wire() throws IOException { socket.setSoTimeout(4000); }
        void login(String name, String password) throws Exception { request(auth(name, password), MessageType.AUTH_LOGIN_OK); this.name = name; }
        ProtocolMessage request(ProtocolMessage m, MessageType expected) throws Exception {
            m.requestId = UUID.randomUUID().toString(); if (m.type == MessageType.CHAT) m.sender = name;
            writer.println(JsonUtil.toJson(m));
            return await(expected, m.type == MessageType.CHAT ? m.messageId : m.requestId);
        }
        ProtocolMessage await(MessageType expected, String id) throws Exception {
            while (true) {
                String line = reader.readLine(); assertNotNull(line, "Unexpected disconnect");
                ProtocolMessage m = JsonUtil.fromJson(line);
                if (m.type == expected && (id == null || id.equals(m.requestId) || id.equals(m.messageId))) return m;
                if (m.type == MessageType.ERROR && expected != MessageType.ERROR) fail(m.errorCode + ": " + m.errorMessage);
            }
        }
        public void close() throws IOException { socket.close(); }
    }
    private static final class MutableClock extends Clock {
        volatile Instant now = Instant.now();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
