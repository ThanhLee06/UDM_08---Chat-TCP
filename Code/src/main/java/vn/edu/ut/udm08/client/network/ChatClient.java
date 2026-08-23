package vn.edu.ut.udm08.client.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

/**
 * Lớp ChatClient quản lý kết nối TCP đến Server và gửi/nhận thông điệp.
 */
public class ChatClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ChatReceiver receiver;
    
    private String username;
    private String avatarId;

    /**
     * Kết nối đến TCP Server, thiết lập luồng đọc ngầm nhận tin nhắn và gửi gói tin chào hỏi (HELLO).
     *
     * @param host Địa chỉ IP hoặc tên miền của Server.
     * @param port Cổng dịch vụ của Server.
     * @param username Tên người dùng kết nối.
     * @param avatarId ID ảnh đại diện người dùng chọn.
     * @param listener Callback để chuyển tiếp sự kiện mạng lên giao diện.
     * @throws IOException Nếu xảy ra lỗi kết nối mạng hoặc lỗi định dạng JSON.
     * @throws IllegalArgumentException Nếu host hoặc port không hợp lệ.
     */
    public void connect(String host, int port, String username, String avatarId, ChatListener listener) throws IOException {
        connect(new ClientConfig(host, port), username, avatarId, listener);
    }

    /**
     * Kết nối đến TCP Server dựa trên cấu hình ClientConfig.
     *
     * @param config Cấu hình kết nối chứa Host và Port hợp lệ.
     * @param username Tên người dùng kết nối.
     * @param avatarId ID ảnh đại diện người dùng chọn.
     * @param listener Callback để chuyển tiếp sự kiện mạng lên giao diện.
     * @throws IOException Nếu xảy ra lỗi kết nối mạng hoặc lỗi định dạng JSON.
     * @throws IllegalArgumentException Nếu config là null.
     */
    public void connect(ClientConfig config, String username, String avatarId, ChatListener listener) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("ClientConfig không được để null");
        }

        // 1. Thiết lập kết nối Socket TCP
        this.socket = new Socket(config.getHost(), config.getPort());
        
        // 2. Khởi tạo các luồng đọc/ghi dữ liệu sử dụng UTF-8
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        
        this.username = username;
        this.avatarId = avatarId;

        // 3. Khởi chạy luồng nhận tin nhắn chạy ngầm (bọc JavaFX wrapper để an toàn luồng)
        ChatListener safeListener = new JavaFXChatListenerWrapper(listener);
        this.receiver = new ChatReceiver(this, reader, safeListener);
        Thread receiverThread = new Thread(this.receiver, "ChatReceiverThread");
        receiverThread.setDaemon(true); // Đảm bảo thread tự tắt khi app JavaFX chính dừng
        receiverThread.start();

        // 4. Đóng gói gói tin chào hỏi HELLO
        ProtocolMessage helloMessage = new ProtocolMessage(MessageType.HELLO);
        helloMessage.sender = username;
        helloMessage.avatarId = avatarId;
        helloMessage.timestamp = System.currentTimeMillis();

        // 5. Chuyển đổi gói tin sang dạng JSON
        String json = JsonUtil.toJson(helloMessage);

        // 6. Gửi gói tin JSON qua luồng TCP tới Server
        sendRawMessage(json);
    }

    /**
     * Gửi chuỗi tin nhắn thô qua luồng TCP đến Server.
     *
     * @param rawMessage Chuỗi tin nhắn (thường là định dạng JSON).
     */
    public void sendRawMessage(String rawMessage) {
        if (writer != null) {
            writer.println(rawMessage);
        }
    }

    /**
     * Đóng gói và gửi gói tin CHAT đến một đối tượng nhận (target).
     *
     * @param target Tên người nhận tin nhắn (username nhận tin hoặc tên phòng/kênh).
     * @param content Nội dung tin nhắn cần gửi.
     * @throws IOException Nếu kết nối bị ngắt hoặc xảy ra lỗi luồng ghi.
     */
    public void sendMessage(String target, String content) throws IOException {
        // 1. Kiểm tra trạng thái kết nối
        if (!isConnected()) {
            throw new IOException("Không thể gửi tin nhắn: Chưa kết nối đến Server hoặc kết nối đã bị đóng.");
        }

        // 2. Tạo đối tượng tin nhắn CHAT
        ProtocolMessage chatMessage = new ProtocolMessage(MessageType.CHAT);
        chatMessage.sender = this.username;
        chatMessage.target = target;
        chatMessage.content = content;
        chatMessage.timestamp = System.currentTimeMillis();

        // 3. Chuyển đổi đối tượng tin nhắn thành chuỗi JSON
        String json = JsonUtil.toJson(chatMessage);

        // 4. Gửi chuỗi JSON qua Socket
        sendRawMessage(json);

        // 5. Kiểm tra lỗi vật lý trên luồng ghi dữ liệu
        if (writer != null && writer.checkError()) {
            throw new IOException("Không thể gửi tin nhắn: Gặp lỗi vật lý trên luồng truyền dữ liệu TCP Socket.");
        }
    }

    /**
     * Ngắt kết nối TCP và đóng toàn bộ luồng dữ liệu an toàn.
     */
    public void disconnect() {
        try {
            // Gửi gói tin thông báo ngắt kết nối (DISCONNECT) trước khi đóng socket
            if (writer != null && socket != null && !socket.isClosed()) {
                ProtocolMessage disconnectMessage = new ProtocolMessage(MessageType.DISCONNECT);
                disconnectMessage.sender = this.username;
                disconnectMessage.timestamp = System.currentTimeMillis();
                try {
                    String json = JsonUtil.toJson(disconnectMessage);
                    writer.println(json);
                } catch (Exception e) {
                    // Bỏ qua lỗi JSON khi đang đóng kết nối
                }
            }
        } finally {
            // Đảm bảo đóng tất cả tài nguyên dù có lỗi xảy ra
            closeResources();
        }
    }

    /**
     * Đóng an toàn các luồng dữ liệu và socket.
     */
    private void closeResources() {
        // Dừng luồng đọc ngầm trước
        if (receiver != null) {
            receiver.stop();
            receiver = null;
        }

        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            // Bỏ qua hoặc ghi log lỗi đóng luồng đọc
        } finally {
            reader = null;
        }

        try {
            if (writer != null) {
                writer.close();
            }
        } finally {
            writer = null;
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Bỏ qua hoặc ghi log lỗi đóng socket
        } finally {
            socket = null;
        }
    }

    /**
     * Kiểm tra trạng thái kết nối của Client.
     *
     * @return true nếu kết nối vẫn đang mở, ngược lại false.
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public String getUsername() {
        return username;
    }

    public String getAvatarId() {
        return avatarId;
    }
}
