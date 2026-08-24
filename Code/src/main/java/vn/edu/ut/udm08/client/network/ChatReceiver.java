package vn.edu.ut.udm08.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

/**
 * Luồng chạy ẩn (Background Reader Thread) liên tục lắng nghe và đọc tin nhắn từ Server.
 */
public class ChatReceiver implements Runnable {
    private final ChatClient client;
    private final BufferedReader reader;
    private final ChatListener listener;
    private volatile boolean running = true;

    /**
     * Khởi tạo luồng đọc ngầm dữ liệu từ socket.
     *
     * @param client ChatClient quản lý kết nối.
     * @param reader Luồng đọc ký tự từ Server.
     * @param listener Callback nhận xử lý gói tin.
     */
    public ChatReceiver(ChatClient client, BufferedReader reader, ChatListener listener) {
        this.client = client;
        this.reader = reader;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    // Giải mã gói tin JSON từ Server
                    ProtocolMessage message = JsonUtil.fromJson(line);
                    if (message != null && message.type != null) {
                        dispatchMessage(message);
                    }
                } catch (Exception e) {
                    // Báo lỗi cú pháp gói tin JSON, nhưng vẫn giữ luồng chạy tiếp
                    if (listener != null) {
                        listener.onErrorReceived("JSON_PARSE_ERROR", "Lỗi cú pháp gói tin nhận được: " + e.getMessage());
                    }
                }
            }

            // Nếu Server đóng luồng mạng (EOF line == null) trong khi client chưa chủ động stop()
            if (running) {
                notifyConnectionLost(new IOException("Máy chủ đã ngắt kết nối"));
            }
        } catch (IOException e) {
            // Khi luồng bị ngắt đột ngột (Socket đóng hoặc cáp mạng đứt)
            if (running) {
                notifyConnectionLost(e);
            }
        } finally {
            running = false;
        }
    }

    /**
     * Dừng luồng đọc ngầm từ xa.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Kiểm tra trạng thái đang chạy của luồng đọc.
     *
     * @return true nếu luồng đang hoạt động, ngược lại false.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Phân loại và chuyển giao gói tin tới callback tương ứng.
     */
    void dispatchMessage(ProtocolMessage message) {
        if (listener == null || message == null || message.type == null) {
            return;
        }

        switch (message.type) {
            case HELLO_OK:
                listener.onLoginSuccess(message);
                break;
            case USER_LIST:
                listener.onUserListUpdated(message.users != null ? message.users : Collections.emptyList());
                break;
            case CHAT:
                listener.onMessageReceived(message);
                break;
            case CHAT_OK:
                listener.onMessageSentSuccess(message.messageId);
                break;
            case ERROR:
                listener.onErrorReceived(message.errorCode, message.errorMessage);
                break;
            default:
                // Gói tin không xác định / chưa hỗ trợ
                break;
        }
    }

    /**
     * Báo lỗi mất kết nối đường truyền đột ngột.
     */
    private void notifyConnectionLost(Throwable cause) {
        if (listener != null) {
            listener.onConnectionLost(cause);
        }
        // Gọi đóng Socket ở client để đồng bộ lại trạng thái kết nối
        client.disconnect();
    }
}
