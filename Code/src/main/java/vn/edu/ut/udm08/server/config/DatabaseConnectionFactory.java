package vn.edu.ut.udm08.server.config;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
public class DatabaseConnectionFactory {
    private final String dbUrl;
    private final int busyTimeout;

    public DatabaseConnectionFactory() {
        this(System.getProperty("udm08.db.url", "jdbc:sqlite:udm08_chat.db"), 5000);
    }

    public DatabaseConnectionFactory(String dbUrl) {
        this(dbUrl, 5000);
    }

    public DatabaseConnectionFactory(String dbUrl, int busyTimeout) {
        this.dbUrl = dbUrl;
        this.busyTimeout = busyTimeout > 0 ? busyTimeout : 5000;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = " + busyTimeout + ";");
        }
        return conn;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public int getBusyTimeout() {
        return busyTimeout;
    }
}
