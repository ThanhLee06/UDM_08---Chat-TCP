# UDM08 — nghiệm thu theo yêu cầu đề tài

Mục tiêu: ứng dụng desktop JavaFX, Client và Server dùng TCP thật, DB và SMTP ở Server.
Đây là checklist nghiệm thu; không tự đánh dấu các bước thủ công khi chưa thực hiện.

|Nhóm|Triển khai / bằng chứng tự động|Cần xác nhận thủ công|
|---|---|---|
|Cấu hình|File ngoài JAR; GUI sửa/lưu; test round-trip và lỗi file|Đổi IP/port, đóng/mở Client, kết nối lại|
|Auth|AUTH_LOGIN, HELLO bị từ chối, BCrypt, single-session; test TCP|Hai cửa sổ GUI đăng nhập cùng tài khoản|
|Đăng ký/reset|OTP ở Server; tài khoản chỉ lưu sau xác minh; DTO không chứa hash|SMTP thật, sai OTP, resend, reset rồi đăng nhập|
|Chat|TCP + SQLite; ACK sau lưu, gửi offline có lịch sử|Hai GUI gửi nhận hai chiều|
|Reply/forward|Kiểm tra tin gốc và quyền truy cập; lưu metadata; test restart|Reply/forward bằng menu chuột phải, mở lại lịch sử|
|Avatar/emoji|Preset đóng gói cùng app; emoji Unicode|Avatar ở liên hệ và tin nhắn; chọn emoji từ popup|
|Lỗi/tài nguyên|Timeout, giới hạn frame, từ chối frame thiếu LF, đóng socket khi stop|Rút mạng/tắt Server, GUI báo mất kết nối, kết nối lại|
|Logging|Log có thời gian, session, loại request/response, mã lỗi; không log payload|Kiểm tra logs/server-*.log sau demo|
|Hiệu năng|LoadProfileTest: 2 và 16 Client, mỗi Client 30 tin|Nếu chạy trên máy/LAN khác, đo lại và ghi cấu hình|
|Hai tiến trình|ProcessBoundaryTest mở JVM Server riêng và Client TCP|Mở Server GUI và Client GUI ở hai tiến trình|

## Kịch bản demo cần quay/chụp

1. Mở Server GUI; lưu port, bật Server; chụp trạng thái và log.
2. Mở hai Client GUI; cấu hình địa chỉ Server. Nếu LAN, dùng IP LAN máy Server; không dùng 127.0.0.1 trên máy khác.
3. Đăng ký bằng email demo, thử OTP sai, gửi lại sau cooldown, xác minh đúng. Chụp kết quả, che OTP/secret.
4. Đăng nhập hai tài khoản; gửi tin tiếng Việt + emoji hai chiều; đối chiếu avatar.
5. Reply một tin cụ thể; forward sang người thứ ba; kiểm tra nội dung và người gửi gốc.
6. Tắt Client nhận; gửi tin; mở lại Client nhận và kiểm tra lịch sử.
7. Thử cùng tài khoản: mật khẩu sai không kick; đúng thì phiên cũ bị kick.
8. Tắt Server bất ngờ; Client báo lỗi và không đứng UI. Mở Server lại, đăng nhập, lịch sử còn.
9. Đổi cấu hình rồi mở lại app; kiểm tra giá trị được nhớ.
10. Lưu log, ảnh/video, ngày chạy, cấu hình máy, phiên bản Java và kết quả test vào hồ sơ nộp.

## Giới hạn phải nói đúng khi báo cáo

- Test email tự động sử dụng sender giả lập; không chứng minh SMTP thật đã gửi được.
- Bài đo tải là burst nhỏ trên loopback, chung JVM; không phải giới hạn tải tối đa hay số đo LAN.
- Test hai tiến trình dùng Server CLI; thao tác GUI được kiểm tra riêng và vẫn cần walkthrough bằng người.
- TCP chat hiện chưa có TLS. Dùng tài khoản/mật khẩu demo riêng; không dùng thông tin thật.
- Gửi thành công nghĩa là Server đã lưu tin, không có nghĩa người nhận đã đọc.
- Phiên không gửi dữ liệu quá connection.idleTimeout sẽ bị đóng; mặc định 5 phút, cấu hình tối đa 1 giờ.
- Chưa thực hiện hoặc xác nhận demo trên hai máy vật lý trong phiên làm việc này.

## Hồ sơ nhóm cần bổ sung

Báo cáo và slide do nhóm hoàn thiện với thành viên, phân công, thiết kế, hình demo thực tế và số liệu trong evidence.
Không ghi rằng mọi tiêu chí đã nghiệm thu nếu các bước thủ công bên trên còn chưa thực hiện.
