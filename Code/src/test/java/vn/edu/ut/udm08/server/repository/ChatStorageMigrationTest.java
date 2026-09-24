package vn.edu.ut.udm08.server.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStorageMigrationTest {
    private File tempDbFile;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() throws Exception {
        tempDbFile = File.createTempFile("test_chat_storage_", ".db");
        String dbUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();
        userRepository = vn.edu.ut.udm08.support.TestDatabase.repository(dbUrl);
    }

    @AfterEach
    void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }

    @Test
    void shouldCreateChatStorageTablesAndIndexes() throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {

            boolean hasConversations = false;
            boolean hasMembers = false;
            boolean hasMessages = false;

            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    String tableName = rs.getString("name");
                    if ("conversations".equalsIgnoreCase(tableName)) {
                        hasConversations = true;
                    }
                    if ("conversation_members".equalsIgnoreCase(tableName)) {
                        hasMembers = true;
                    }
                    if ("messages".equalsIgnoreCase(tableName)) {
                        hasMessages = true;
                    }
                }
            }

            assertTrue(hasConversations);
            assertTrue(hasMembers);
            assertTrue(hasMessages);
            boolean hasConvSeqIndex = false;
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'")) {
                while (rs.next()) {
                    String indexName = rs.getString("name");
                    if ("idx_messages_conv_sequence".equalsIgnoreCase(indexName)) {
                        hasConvSeqIndex = true;
                    }
                }
            }

            assertTrue(hasConvSeqIndex);
        }
    }
}
