package vn.edu.ut.udm08.server.config;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
public class DatabaseInitializer {
    private final DatabaseConnectionFactory connectionFactory;
    public DatabaseInitializer() {
        this(new DatabaseConnectionFactory());
    }
    public DatabaseInitializer(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
    public void initialize() {
        ensureDataDirectoryExists();
        enableWalMode();
        initAuthSchema();
        initChatSchema();
    }

    private void ensureDataDirectoryExists() {
        String dbUrl = connectionFactory.getDbUrl();
        if (dbUrl != null && dbUrl.startsWith("jdbc:sqlite:")) {
            String pathStr = dbUrl.substring("jdbc:sqlite:".length()).trim();
            java.io.File file = new java.io.File(pathStr);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
    }

    private void enableWalMode() {
        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
        } catch (Exception ignored) {
        }
    }
    private void initAuthSchema() {
        try (InputStream is = getClass().getResourceAsStream("/db/migration/V2__auth.sql")) {
            if (is == null) {
                throw new IllegalStateException("Thiếu schema tài khoản V2__auth.sql");
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = connectionFactory.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                boolean hasEmail = false;
                try (ResultSet columns = stmt.executeQuery("PRAGMA table_info(users)")) {
                    while (columns.next()) {
                        if ("email".equalsIgnoreCase(columns.getString("name"))) hasEmail = true;
                    }
                }
                if (!hasEmail) stmt.execute("ALTER TABLE users ADD COLUMN email TEXT");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users(lower(email)) WHERE email IS NOT NULL");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể khởi tạo cơ sở dữ liệu tài khoản", e);
        }
    }
    private void initChatSchema() {
        try (InputStream is = getClass().getResourceAsStream("/db/migration/chat_storage.sql")) {
            if (is != null) {
                String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                try (Connection conn = connectionFactory.getConnection();
                     Statement stmt = conn.createStatement()) {
                    for (String query : sql.split(";")) {
                        if (!query.trim().isEmpty()) {
                            stmt.execute(query.trim());
                        }
                    }
                    ensureColumnExists(stmt, "conversations", "last_message_preview", "ALTER TABLE conversations ADD COLUMN last_message_preview TEXT");
                    ensureColumnExists(stmt, "conversations", "last_activity", "ALTER TABLE conversations ADD COLUMN last_activity INTEGER");
                    ensureColumnExists(stmt, "conversation_members", "role", "ALTER TABLE conversation_members ADD COLUMN role TEXT DEFAULT 'member'");
                    ensureColumnExists(stmt, "messages", "forward_from_conv_id", "ALTER TABLE messages ADD COLUMN forward_from_conv_id TEXT");
                    ensureColumnExists(stmt, "messages", "kind", "ALTER TABLE messages ADD COLUMN kind TEXT DEFAULT 'text'");
                    ensureColumnExists(stmt, "messages", "reply_to_message_id", "ALTER TABLE messages ADD COLUMN reply_to_message_id TEXT");
                    ensureColumnExists(stmt, "messages", "forward_from_message_id", "ALTER TABLE messages ADD COLUMN forward_from_message_id TEXT");

                    stmt.execute("INSERT OR IGNORE INTO conversations (conv_id, type, name) VALUES ('room:public', 'PUBLIC', 'Phòng chung')");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể khởi tạo cơ sở dữ liệu hội thoại", e);
        }
    }

    private void ensureColumnExists(Statement stmt, String tableName, String columnName, String alterSql) {
        try {
            boolean exists = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                stmt.execute(alterSql);
            }
        } catch (Exception ignored) {
        }
    }
}
