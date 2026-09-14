package vn.edu.ut.udm08.server.repository;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.ut.udm08.shared.model.User;
import static org.junit.jupiter.api.Assertions.*;
class UserRepositoryMigrationTest {
    @TempDir Path temp;
    @Test void addsEmailWithoutDeletingOldAccountsAndIsRepeatable() throws Exception {
        String url = "jdbc:sqlite:" + temp.resolve("legacy.db");
        String schema;
        try (var stream = getClass().getResourceAsStream("/db/migration/V2__auth.sql")) {
            schema = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("    email TEXT,\n", "");
        }
        try (var conn = DriverManager.getConnection(url); var stmt = conn.createStatement()) {
            stmt.execute(schema);
            stmt.execute("INSERT INTO users(username,phone_number,password_hash) VALUES('Legacy','0901234567','existing-hash')");
        }
        var repo = new UserRepository(url);
        var legacy = repo.findByPhoneNumber("0901234567").orElseThrow();
        assertNull(legacy.getEmail());
        assertEquals("existing-hash", legacy.getPasswordHash());
        User added = new User();
        added.setUsername("NewUser"); added.setPhoneNumber("0909999999");
        added.setEmail("New@Gmail.com"); added.setPasswordHash("new-hash");
        added.setAvatarType("PRESET"); added.setAvatarPath("01.png");
        assertNotNull(repo.save(added));
        var reopened = new UserRepository(url);
        assertEquals(2, reopened.findAll().size());
        assertEquals("new@gmail.com", reopened.findByEmail("NEW@gmail.com").orElseThrow().getEmail());
        try (var conn = DriverManager.getConnection(url); var stmt = conn.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> stmt.execute(
                    "INSERT INTO users(username,phone_number,password_hash,email) VALUES('Duplicate','0908888888','hash','NEW@gmail.com')"));
        }
    }
}
