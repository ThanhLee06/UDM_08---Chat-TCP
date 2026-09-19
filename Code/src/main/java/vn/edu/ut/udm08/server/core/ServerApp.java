package vn.edu.ut.udm08.server.core;

public class ServerApp {
    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.load();
            ChatServer server = new ChatServer(config);
            System.out.println("=================================");
            System.out.println("     UDM08 CHAT SERVER          ");
            System.out.println("=================================");
            System.out.println("Port    : " + config.getPort());
            System.out.println("Database: " + config.getDbUrl());
            System.out.println("Status  : RUNNING");
            System.out.println("=================================");
            server.start();
        } catch (Exception e) {
            System.err.println("Lỗi khởi chạy ServerApp: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
