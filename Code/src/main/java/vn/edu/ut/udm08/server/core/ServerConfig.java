package vn.edu.ut.udm08.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {

    private static final String CONFIG_FILE = "/server.properties";
    private static final String PORT_KEY = "server.port";

    private final int port;
    private final String dbUrl;
    private final int dbBusyTimeout;
    private final int messageMaxLength;
    private final int historyMaxLimit;
    private final int searchMaxLength;
    private int idleTimeoutMs = 300000;

    private ServerConfig(int port, String dbUrl, int dbBusyTimeout, int messageMaxLength, int historyMaxLimit, int searchMaxLength) {
        this.port = port;
        this.dbUrl = dbUrl;
        this.dbBusyTimeout = dbBusyTimeout;
        this.messageMaxLength = messageMaxLength;
        this.historyMaxLimit = historyMaxLimit;
        this.searchMaxLength = searchMaxLength;
    }

    public static ServerConfig load() {
        Properties properties = new Properties();

        try (InputStream inputStream =
                     ServerConfig.class.getResourceAsStream(CONFIG_FILE)) {

            if (inputStream == null) {
                throw new IllegalStateException("Configuration file not found: " + CONFIG_FILE);
            }

            properties.load(inputStream);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to load server configuration", e);
        }

        String sysConfig = System.getProperty("udm08.server.config");
        java.nio.file.Path external = (sysConfig != null && !sysConfig.isBlank())
                ? java.nio.file.Path.of(sysConfig)
                : java.nio.file.Path.of("config/server.properties");
        if (java.nio.file.Files.exists(external)) {
            try (InputStream input = java.nio.file.Files.newInputStream(external)) { properties.load(input); }
            catch (IOException e) { throw new IllegalStateException("Cannot read server configuration", e); }
        }

        return fromProperties(properties);
    }

    public static ServerConfig fromProperties(Properties properties) {
        String portValue = properties.getProperty(PORT_KEY);

        if (portValue == null || portValue.isBlank()) {
            throw new IllegalStateException("Missing required configuration: " + PORT_KEY);
        }

        final int port;

        try {
            port = Integer.parseInt(portValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid server port: " + portValue, e);
        }

        if (port < 0 || port > 65535) {
            throw new IllegalStateException("Server port must be between 1 and 65535: " + port);
        }

        String dbUrl = properties.getProperty("db.url", "jdbc:sqlite:data/udm08_chat.db").trim();
        int busyTimeout = parseOrDefault(properties.getProperty("db.busyTimeout"), 5000);
        int msgMax = parseOrDefault(properties.getProperty("message.maxLength"), 5000);
        int histMax = parseOrDefault(properties.getProperty("history.maxLimit"), 100);
        int searchMax = parseOrDefault(properties.getProperty("search.maxLength"), 100);

        ServerConfig config = new ServerConfig(port, dbUrl, busyTimeout, msgMax, histMax, searchMax);
        config.idleTimeoutMs = parseOrDefault(properties.getProperty("connection.idleTimeout"), 300000);
        if (config.idleTimeoutMs < 1000 || config.idleTimeoutMs > 3600000) {
            throw new IllegalStateException("connection.idleTimeout must be between 1000 and 3600000 ms");
        }
        return config;
    }

    private static int parseOrDefault(String val, int def) {
        if (val == null || val.isBlank()) return def;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getPort() {
        return port;
    }
    public int getIdleTimeoutMs() { return idleTimeoutMs; }

    public String getDbUrl() {
        return dbUrl;
    }

    public int getDbBusyTimeout() {
        return dbBusyTimeout;
    }

    public int getMessageMaxLength() {
        return messageMaxLength;
    }

    public int getHistoryMaxLimit() {
        return historyMaxLimit;
    }

    public int getSearchMaxLength() {
        return searchMaxLength;
    }
}
