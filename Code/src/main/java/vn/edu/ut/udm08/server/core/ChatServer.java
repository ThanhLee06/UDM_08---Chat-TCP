package vn.edu.ut.udm08.server.core;

public class ChatServer {

    private final int port;

    public ChatServer(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig must not be null");
        }

        this.port = config.getPort();
    }

    public int getPort() {
        return port;
    }
}
