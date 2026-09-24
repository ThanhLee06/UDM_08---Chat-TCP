package vn.edu.ut.udm08.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Properties;
import vn.edu.ut.udm08.client.network.ClientConfig;

/** External configuration: validate before use, persist before reporting success. */
public final class ClientConfigStore {
    private final Path path;
    public ClientConfigStore() {
        this(Path.of(System.getProperty("udm08.client.config", "config/client.properties")));
    }
    public ClientConfigStore(Path path) { this.path = path; }
    public ClientConfig load() throws IOException {
        if (!Files.exists(path)) return new ClientConfig("127.0.0.1", 8080);
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        try {
            return new ClientConfig(p.getProperty("server.host", "127.0.0.1"),
                Integer.parseInt(p.getProperty("server.port", "8080")),
                Integer.parseInt(p.getProperty("connection.timeout", "5000")),
                Integer.parseInt(p.getProperty("request.timeout", "15000")));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid connection configuration: " + path, e);
        }
    }
    public void save(ClientConfig config) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "client-", ".tmp");
        Properties p = new Properties();
        p.setProperty("server.host", config.getHost());
        p.setProperty("server.port", Integer.toString(config.getPort()));
        p.setProperty("connection.timeout", Integer.toString(config.getConnectTimeoutMs()));
        p.setProperty("request.timeout", Integer.toString(config.getRequestTimeoutMs()));
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "UDM08 connection"); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}