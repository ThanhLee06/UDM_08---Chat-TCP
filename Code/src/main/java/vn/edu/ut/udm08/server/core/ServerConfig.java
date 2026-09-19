package vn.edu.ut.udm08.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {

    private static final String CONFIG_FILE = "/server.properties";
    private static final String PORT_KEY = "server.port";

    private final int port;
    private final int messageMaxLength;
    private final int historyMaxLimit;
    private final int searchMaxLength;

    private ServerConfig(int port, int messageMaxLength, int historyMaxLimit, int searchMaxLength) {
        this.port = port;
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

        int msgMax = parseOrDefault(properties.getProperty("message.maxLength"), 5000);
        int histMax = parseOrDefault(properties.getProperty("history.maxLimit"), 100);
        int searchMax = parseOrDefault(properties.getProperty("search.maxLength"), 100);

        return new ServerConfig(port, msgMax, histMax, searchMax);
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