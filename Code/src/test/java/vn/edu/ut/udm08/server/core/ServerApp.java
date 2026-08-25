package vn.edu.ut.udm08.server.core;
import java.util.Properties;
public class ServerApp {
    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            props.setProperty("server.port", "8080");
            ServerConfig config = ServerConfig.fromProperties(props);
            ChatServer server = new ChatServer(config);
            System.out.println("Server TCP đang lắng nghe trên cổng: 8080");
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
