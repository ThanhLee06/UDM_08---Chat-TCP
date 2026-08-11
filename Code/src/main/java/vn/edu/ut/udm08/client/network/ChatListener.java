package vn.edu.ut.udm08.client.network;

import java.util.List;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

/**
 * Interface ChatListener định nghĩa các hàm Callback sự kiện mạng.
 * Tầng giao diện (Controller) sẽ implement interface này để nhận dữ liệu từ Client.
 */
public interface ChatListener {
    
    /**
     * Kích hoạt khi đăng nhập thành công (nhận gói tin HELLO_OK).
     *
     * @param message Chi tiết phản hồi đăng nhập từ Server.
     */
    void onLoginSuccess(ProtocolMessage message);
    
    /**
     * Kích hoạt khi nhận được danh sách cập nhật các người dùng đang online (gói tin USER_LIST).
     *
     * @param users Danh sách thông tin hồ sơ của các người dùng online.
     */
    void onUserListUpdated(List<UserProfile> users);
    
    /**
     * Kích hoạt khi nhận được tin nhắn chat mới từ một người dùng hoặc phòng (gói tin CHAT).
     *
     * @param message Đối tượng chứa thông tin người gửi và nội dung tin nhắn.
     */
    void onMessageReceived(ProtocolMessage message);
    
    /**
     * Kích hoạt khi nhận được xác nhận từ Server rằng tin nhắn gửi đi đã thành công (gói tin CHAT_OK).
     *
     * @param messageId ID của tin nhắn gửi thành công.
     */
    void onMessageSentSuccess(String messageId);
    
    /**
     * Kích hoạt khi nhận được tin nhắn báo lỗi từ Server (gói tin ERROR).
     *
     * @param errorCode Mã lỗi từ Server gửi về.
     * @param errorMessage Mô tả chi tiết thông báo lỗi.
     */
    void onErrorReceived(String errorCode, String errorMessage);
    
    /**
     * Kích hoạt khi kết nối socket đến Server bị ngắt đột ngột (lỗi đường truyền).
     *
     * @param cause Ngoại lệ nguyên nhân gây mất kết nối.
     */
    void onConnectionLost(Throwable cause);
}
