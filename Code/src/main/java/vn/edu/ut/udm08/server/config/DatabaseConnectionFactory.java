package vn.edu.ut.udm08.server.config;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
public class DatabaseConnectionFactory {
    private final String dbUrl;
    public DatabaseConnectionFactory() {
        this(System.getProperty("udm08.db.url", "jdbc:sqlite:udm08_chat.db"));
    }
    public DatabaseConnectionFactory(String dbUrl) {
        this.dbUrl = dbUrl;
    }
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }
    public String getDbUrl() {
        return dbUrl;
    }
}
