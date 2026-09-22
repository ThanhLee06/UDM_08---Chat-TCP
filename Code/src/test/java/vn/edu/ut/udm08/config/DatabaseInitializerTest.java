package vn.edu.ut.udm08.server.config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class DatabaseInitializerTest {
    private File tempDbFile;
    private DatabaseConnectionFactory connectionFactory;
    @BeforeEach
    void setUp() throws Exception {
        tempDbFile = File.createTempFile("test_init_db_", ".db");
        String dbUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();
        connectionFactory = new DatabaseConnectionFactory(dbUrl, 3000);
    }
    @AfterEach
    void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }
    @Test
    void shouldInitializeAuthAndChatSchemasSuccessfully() throws Exception {
        DatabaseInitializer initializer = new DatabaseInitializer(connectionFactory);
        initializer.initialize();
        assertEquals(3000, connectionFactory.getBusyTimeout());
        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            boolean hasUsers = false;
            boolean hasConversations = false;
            boolean hasMessages = false;
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if ("users".equalsIgnoreCase(name)) hasUsers = true;
                    if ("conversations".equalsIgnoreCase(name)) hasConversations = true;
                    if ("messages".equalsIgnoreCase(name)) hasMessages = true;
                }
            }
            assertTrue(hasUsers);
            assertTrue(hasConversations);
            assertTrue(hasMessages);
        }
    }
}