package vn.edu.ut.udm08.server.config;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.ut.udm08.server.core.ServerConfig;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class SampleAccountsTest {
    @TempDir Path directory;

    @Test void addsTwoAccountsAndKeepsChangedPasswordsOnRestart() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("server.port", "0");
        properties.setProperty("db.url", "jdbc:sqlite:" + directory.resolve("chat.db"));
        ServerConfig config = ServerConfig.fromProperties(properties);
        SampleAccounts.initialize(config);
        UserRepository users = new UserRepository(config.getDbUrl());
        var alice = users.findByEmail("testalice@example.test").orElseThrow();
        var bob = users.findByEmail("testbob@example.test").orElseThrow();
        PasswordEncoder encoder = new PasswordEncoder();
        assertTrue(encoder.matches("TestPass123!", alice.getPasswordHash()));
        assertTrue(encoder.matches("TestPass123!", bob.getPasswordHash()));
        String changed = encoder.encode("ChangedPass456!");
        assertTrue(users.updatePassword(alice.getPhoneNumber(), changed));
        SampleAccounts.initialize(config);
        assertEquals(changed, users.findByEmail("testalice@example.test").orElseThrow().getPasswordHash());
        try (var connection = java.sql.DriverManager.getConnection(config.getDbUrl());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM users")) {
            assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
        }
    }
}
