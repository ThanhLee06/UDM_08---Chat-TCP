package vn.edu.ut.udm08.client.network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import vn.edu.ut.udm08.client.config.ClientConfigStore;
import static org.junit.jupiter.api.Assertions.*;
class ClientConfigStoreTest {
    @TempDir Path dir;
    @Test void persistsAllNetworkSettingsAcrossInstances() throws Exception {
        Path path = dir.resolve("config/client.properties");
        new ClientConfigStore(path).save(new ClientConfig("192.168.1.50", 9123, 1234, 6789));
        ClientConfig loaded = new ClientConfigStore(path).load();
        assertEquals("192.168.1.50", loaded.getHost()); assertEquals(9123, loaded.getPort());
        assertEquals(1234, loaded.getConnectTimeoutMs()); assertEquals(6789, loaded.getRequestTimeoutMs());
    }
    @Test void rejectsCorruptConfigurationAndReportsWriteFailure() throws Exception {
        Path path = dir.resolve("client.properties"); Files.writeString(path, "server.port=999999");
        assertThrows(java.io.IOException.class, () -> new ClientConfigStore(path).load());
        assertThrows(java.io.IOException.class, () -> new ClientConfigStore(path.resolve("child")).save(new ClientConfig("localhost", 8080)));
    }
}
