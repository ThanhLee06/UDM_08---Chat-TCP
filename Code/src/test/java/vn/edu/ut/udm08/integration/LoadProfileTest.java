package vn.edu.ut.udm08.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.ut.udm08.server.core.*;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.dto.AuthLoginRequest;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
import vn.edu.ut.udm08.support.TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class LoadProfileTest {
    @TempDir Path dir;
    @Test @Timeout(120) void measuresTwoConcurrentTcpLoads() throws Exception {
        StringBuilder report = new StringBuilder("# TCP load measurement\n\nExecuted: " + java.time.Instant.now() + "\n\n");
        report.append("Environment: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.arch"))
            .append("; Java ").append(System.getProperty("java.version")).append("; logical CPUs ").append(Runtime.getRuntime().availableProcessors())
            .append("; max heap MiB ").append(Runtime.getRuntime().maxMemory() / 1048576).append("\n\n")
            .append("Loopback TCP sockets, server and load clients in one JVM; SQLite WAL; fake accounts. Login excluded from measurement.\n")
            .append("Each client sends 30 public-room messages, 128 ASCII payload characters, one outstanding request per client. All clients start together. ACK means persisted, not read by recipient.\n\n")
            .append("|Clients|Messages ACKed|Errors|Elapsed ms|Messages/s|Mean ACK ms|P95 ACK ms|\n|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int clients : new int[]{2, 16}) report.append(runLoad(clients));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/load-results.md"), report, StandardCharsets.UTF_8);
    }
    private String runLoad(int count) throws Exception {
        Properties p = new Properties(); p.setProperty("server.port", "0");
        p.setProperty("db.url", "jdbc:sqlite:" + dir.resolve("load-" + count + ".db"));
        var repo = TestDatabase.repository(p.getProperty("db.url"));
        String hash = new vn.edu.ut.udm08.shared.security.PasswordEncoder().encode("DemoPass123!");
        for (int i = 0; i < count; i++) {
            User u = new User(); u.setUsername("load" + i); u.setEmail("load" + i + "@example.test");
            u.setPhoneNumber(String.format("090%07d", i)); u.setPasswordHash(hash); repo.save(u);
        }
        ChatServer server = new ChatServer(ServerConfig.fromProperties(p));
        Thread thread = new Thread(() -> { try { server.start(); } catch (IOException e) { throw new AssertionError(e); } });
        thread.setDaemon(true); thread.start();
        List<Peer> peers = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!server.isRunning() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(server.isRunning());
            for (int i = 0; i < count; i++) peers.add(new Peer(server.getPort(), "load" + i));
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Double>>> futures = new ArrayList<>();
            for (Peer peer : peers) futures.add(pool.submit(() -> {
                start.await(); List<Double> latency = new ArrayList<>();
                for (int n = 0; n < 30; n++) {
                    ProtocolMessage m = new ProtocolMessage(MessageType.CHAT);
                    m.messageId = UUID.randomUUID().toString(); m.sender = peer.name;
                    m.convId = "room:public"; m.content = "x".repeat(128);
                    long before = System.nanoTime(); peer.sendAndAwait(m);
                    latency.add((System.nanoTime() - before) / 1_000_000.0);
                }
                return latency;
            }));
            long begin = System.nanoTime(); start.countDown();
            List<Double> latency = new ArrayList<>();
            for (var f : futures) latency.addAll(f.get(90, TimeUnit.SECONDS));
            double ms = (System.nanoTime() - begin) / 1_000_000.0;
            Collections.sort(latency); assertEquals(count * 30, latency.size());
            return String.format(Locale.ROOT, "|%d|%d|0|%.2f|%.2f|%.2f|%.2f|%n", count, latency.size(), ms,
                latency.size() * 1000 / ms, latency.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                latency.get((int)Math.ceil(latency.size() * .95) - 1));
        } finally { for (Peer peer : peers) peer.socket.close(); pool.shutdownNow(); server.stop(); thread.join(2000); }
    }
    private static final class Peer {
        final String name; final Socket socket; final BufferedReader reader; final PrintWriter writer;
        final ConcurrentHashMap<String, CompletableFuture<Void>> acknowledgements = new ConcurrentHashMap<>();
        Peer(int port, String name) throws Exception {
            this.name = name; socket = new Socket("127.0.0.1", port); socket.setSoTimeout(10000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            ProtocolMessage m = new ProtocolMessage(MessageType.AUTH_LOGIN); m.requestId = UUID.randomUUID().toString();
            m.content = JsonUtil.toJson(new AuthLoginRequest(name + "@example.test", "DemoPass123!"));
            writer.println(JsonUtil.toJson(m)); await(MessageType.AUTH_LOGIN_OK, null);
            socket.setSoTimeout(0);
            Thread receiver = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ProtocolMessage response = JsonUtil.fromJson(line);
                        if (response.messageId == null) continue;
                        CompletableFuture<Void> waiting = acknowledgements.remove(response.messageId);
                        if (waiting != null) {
                            if (response.type == MessageType.CHAT_OK) waiting.complete(null);
                            else waiting.completeExceptionally(new IOException(response.errorCode));
                        }
                    }
                } catch (IOException e) { acknowledgements.values().forEach(f -> f.completeExceptionally(e)); }
            }, "LoadReceiver-" + name);
            receiver.setDaemon(true); receiver.start();
        }
        void sendAndAwait(ProtocolMessage message) throws Exception {
            var ack = new CompletableFuture<Void>(); acknowledgements.put(message.messageId, ack);
            writer.println(JsonUtil.toJson(message)); ack.get(10, TimeUnit.SECONDS);
        }
        void await(MessageType type, String id) throws Exception {
            while (true) {
                String line = reader.readLine(); assertNotNull(line);
                ProtocolMessage m = JsonUtil.fromJson(line);
                assertNotEquals(MessageType.ERROR, m.type, m.errorCode);
                if (m.type == type && (id == null || id.equals(m.messageId))) return;
            }
        }
    }
}
