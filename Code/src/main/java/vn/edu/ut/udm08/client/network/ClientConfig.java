package vn.edu.ut.udm08.client.network;

/**
 * Lớp cấu hình lưu trữ và kiểm tra tính hợp lệ của địa chỉ Server (Host và Port).
 */
public final class ClientConfig {

    private final String host;
    private final int port;

    /**
     * Khởi tạo cấu hình kết nối Client với kiểm tra tính hợp lệ của Host và Port.
     *
     * @param host Địa chỉ IP hoặc tên miền của Server (không được null hoặc rỗng).
     * @param port Cổng dịch vụ của Server (phải nằm trong khoảng 1 - 65535).
     * @throws IllegalArgumentException Nếu host hoặc port không hợp lệ.
     */
    public ClientConfig(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host không được để trống");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port phải nằm trong khoảng từ 1 đến 65535: " + port);
        }
        this.host = host.trim();
        this.port = port;
    }

    /**
     * Factory method tiện ích tạo ClientConfig.
     */
    public static ClientConfig of(String host, int port) {
        return new ClientConfig(host, port);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
