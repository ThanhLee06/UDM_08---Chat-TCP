package vn.edu.ut.udm08.server.core;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void shouldLoadValidPort() {
        Properties properties = new Properties();
        properties.setProperty("server.port", "8080");

        ServerConfig config = ServerConfig.fromProperties(properties);

        assertEquals(8080, config.getPort());
    }

    @Test
    void shouldRejectMissingPort() {
        Properties properties = new Properties();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ServerConfig.fromProperties(properties)
        );

        assertTrue(exception.getMessage().contains("server.port"));
    }

    @Test
    void shouldRejectNonNumericPort() {
        Properties properties = new Properties();
        properties.setProperty("server.port", "abc");

        assertThrows(
                IllegalStateException.class,
                () -> ServerConfig.fromProperties(properties)
        );
    }

    @Test
    void shouldRejectPortBelowOne() {
        Properties properties = new Properties();
        properties.setProperty("server.port", "0");

        assertThrows(
                IllegalStateException.class,
                () -> ServerConfig.fromProperties(properties)
        );
    }

    @Test
    void shouldRejectPortAbove65535() {
        Properties properties = new Properties();
        properties.setProperty("server.port", "65536");

        assertThrows(
                IllegalStateException.class,
                () -> ServerConfig.fromProperties(properties)
        );
    }

    @Test
    void shouldPassConfigurationToChatServer() {
        Properties properties = new Properties();
        properties.setProperty("server.port", "9090");

        ServerConfig config = ServerConfig.fromProperties(properties);
        ChatServer server = new ChatServer(config);

        assertEquals(9090, server.getPort());
    }
}
