package vn.edu.ut.udm08.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.dto.AuthLoginRequest;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import vn.edu.ut.udm08.support.TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class ProcessBoundaryTest {
    @TempDir Path dir;
    @Test @Timeout(20) void authenticatesAgainstServerInSeparateJvm() throws Exception {
        int port; try (ServerSocket probe = new ServerSocket(0)) { port = probe.getLocalPort(); }
        Properties config = new Properties(); config.setProperty("server.port", Integer.toString(port));
        config.setProperty("db.url", "jdbc:sqlite:" + dir.resolve("separate.db"));
        var users = TestDatabase.repository(config.getProperty("db.url"));
        User user = new User(); user.setUsername("processdemo"); user.setPhoneNumber("0901122334");
        user.setEmail("processdemo@example.test"); user.setPasswordHash(new vn.edu.ut.udm08.shared.security.PasswordEncoder().encode("DemoPass123!")); users.save(user);
        Path file = dir.resolve("server.properties"); try (var out = Files.newOutputStream(file)) { config.store(out, "test"); }
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String configPath = file.toAbsolutePath().toString().replace('\\', '/');
        Process server = new ProcessBuilder(executable, "-Dudm08.server.config=" + configPath, "-Dudm08.log.directory=" + dir.resolve("logs"),
            "-cp", System.getProperty("java.class.path"), "vn.edu.ut.udm08.server.core.ServerApp")
            .redirectErrorStream(true).redirectOutput(dir.resolve("server-output.log").toFile()).start();
        try {
            Socket socket = null; long deadline = System.nanoTime() + 8_000_000_000L;
            while (socket == null && System.nanoTime() < deadline) {
                assertTrue(server.isAlive(), "Server process stopped during startup");
                try { socket = new Socket("127.0.0.1", port); } catch (ConnectException e) { Thread.sleep(50); }
            }
            assertNotNull(socket);
            try (Socket connected = socket) {
                connected.setSoTimeout(4000);
                var writer = new PrintWriter(connected.getOutputStream(), true, StandardCharsets.UTF_8);
                var reader = new BufferedReader(new InputStreamReader(connected.getInputStream(), StandardCharsets.UTF_8));
                ProtocolMessage login = new ProtocolMessage(MessageType.AUTH_LOGIN); login.requestId = "process-check";
                login.content = JsonUtil.toJson(new AuthLoginRequest("processdemo@example.test", "DemoPass123!"));
                writer.println(JsonUtil.toJson(login));
                assertEquals(MessageType.AUTH_LOGIN_OK, JsonUtil.fromJson(reader.readLine()).type);
                Files.writeString(Path.of("target/process-smoke.txt"), "PASS " + java.time.Instant.now() + "\nClient JVM PID=" + ProcessHandle.current().pid() + "\nServer JVM PID=" + server.pid() + "\nTransport=TCP loopback; AUTH_LOGIN_OK received\n");
            }
        } finally { server.destroy(); if (!server.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(); } }
    }
}
