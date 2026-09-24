# UDM08 — Chat TCP desktop

JavaFX Client và Server là các ứng dụng riêng; giao tiếp bằng JSON UTF-8 qua TCP. SQLite và SMTP chỉ chạy ở Server. Client không cần file DB.

## Chạy ứng dụng

Cần JDK 21 trở lên, Maven 3.9 và kết nối tải dependency ở lần build đầu. Bản kiểm thử trên máy Thanh dùng Java 25.0.3; source biên dịch với `release 21`.

Mở hai terminal tại `Code`:

```powershell
# Terminal 1: GUI quản lý Server
.\run.ps1 -Mode server

# Terminal 2: GUI Client; chạy thêm terminal nếu muốn Client thứ hai
.\run.ps1 -Mode client
```

Nếu Maven chưa có trong PATH, truyền `-Maven 'C:\duong-dan\apache-maven\bin\mvn.cmd'`. Có thể chạy trực tiếp:

```powershell
mvn compile javafx:run '-Djavafx.mainClass=vn.edu.ut.udm08.server.core.ServerApplication'
mvn compile javafx:run
```

Server GUI: nhập port → **Lưu và bật Server**. Client: **Cấu hình kết nối** → nhập host/port/timeout → **Lưu**. Mở lại app sẽ tự nạp các giá trị đã lưu.

- Client cùng máy Server: host `127.0.0.1`.
- Client ở máy khác: host là IP LAN máy Server. Hai máy cần mạng cho phép kết nối trực tiếp; kiểm tra firewall cho port đã chọn.
- Không cần chạy Server hoặc copy DB trên máy thành viên.
- Server CLI dùng cho automation: `mvn compile exec:java`.

## Cấu hình ngoài JAR

|File|Nội dung|
|---|---|
|`config/client.properties`|Host, port, timeout kết nối, timeout phản hồi; tạo bởi GUI|
|`config/server.properties`|Port và cấu hình Server; GUI lưu port, giữ các key khác|
|`smtp.properties`|SMTP của máy Server; tuyệt đối không đưa secret thật vào Git|
|`data/udm08_chat.db`|DB Server; không đưa vào Git|
|`logs/server-*.log`|Log xoay vòng, 3 file, tối đa khoảng 2 MB/file|

Có file `.example` để tham khảo. Server nạp mặc định từ resources rồi áp dụng file ngoài; Client nạp trực tiếp file ngoài. Có thể đổi vị trí qua system properties `udm08.client.config`, `udm08.server.config`, `udm08.smtp.config`, `udm08.log.directory`.

SMTP: copy `smtp.properties.example` thành `smtp.properties` ở **Server**, dùng hộp thư demo và credential do nhà cung cấp cấp. Hoặc dùng biến môi trường `UDM08_SMTP_HOST`, `UDM08_SMTP_PORT`, `UDM08_SMTP_USERNAME`, `UDM08_SMTP_PASSWORD`. Không có SMTP hợp lệ thì đăng ký/reset báo lỗi; không có chế độ giả báo gửi email thành công.

## Chức năng

- Đăng ký và xác minh email bằng OTP trước khi lưu tài khoản; đăng nhập/reset qua TCP.
- Một tài khoản chỉ có một phiên. Password sai không kick phiên cũ; HELLO không được dùng để xác thực.
- Chat phòng chung và riêng, reply, forward, avatar preset, chọn emoji.
- Tin được lưu trước khi trả ACK; người nhận offline xem lại qua lịch sử. ACK không có nghĩa đã đọc.
- Timeout kết nối mặc định 5 giây, AUTH/ACK 15 giây; logout chờ ACK tối đa 3 giây.
- Server đóng phiên không gửi dữ liệu quá `connection.idleTimeout`, mặc định 300000 ms. Giới hạn 128 kết nối; giới hạn frame 262144 ký tự; nội dung chat tối đa 5000 ký tự; lịch sử tối đa 100 tin/lần.

## Protocol

Một JSON object trên một dòng, kết thúc bằng LF. Frame thiếu LF lúc EOF hoặc vượt giới hạn không được xử lý như dữ liệu hoàn chỉnh.

Envelope gồm `type`, `requestId` (đối chiếu request/response), `messageId` (định danh tin chat), các trường nội dung tương ứng. Auth DTO được serialize trong `content`; password/hash/OTP không được ghi vào log. `AUTH_LOGIN_OK` và `AUTH_REGISTER_OK` trả DTO tài khoản không có passwordHash.

|Luồng|Request → Response|
|---|---|
|Login|`AUTH_LOGIN` → `AUTH_LOGIN_OK` / `ERROR`|
|Đăng ký|`AUTH_REGISTER_INIT` → `AUTH_REGISTER_OTP_REQUIRED`|
|Xác minh / gửi lại|`AUTH_REGISTER_VERIFY_OTP` → `AUTH_REGISTER_OK`; `AUTH_REGISTER_RESEND_OTP` → `AUTH_REGISTER_OTP_REQUIRED`|
|Reset|`AUTH_FORGOT_INIT` → `AUTH_FORGOT_OTP_REQUIRED`; `AUTH_FORGOT_RESET` → `AUTH_FORGOT_OK`|
|Chat|`CHAT` → `CHAT_OK` / `ERROR`; Server chuyển `CHAT` tới Client nhận|
|Lịch sử / hội thoại|`HISTORY_REQUEST`, `CONVERSATION_LIST_REQUEST`, `OPEN_DM_REQUEST` → response tương ứng|
|Tìm người dùng|`USER_SEARCH_REQUEST` → `USER_SEARCH_RESPONSE`|
|Kết thúc|`LOGOUT` → `LOGOUT_OK`; mất socket thì Server thu hồi phiên|
|Đăng nhập nơi khác|Server gửi `SESSION_KICKED` cho phiên cũ|

Client mở TCP → AUTH → chat/request → logout/đóng kết nối. Các nghiệp vụ chat cần phiên xác thực. Reply và forward được Server đối chiếu tin gốc; lịch sử DM cần quyền thành viên.

## Kiểm thử và bằng chứng

```powershell
mvn test
mvn '-Dtest=SubmissionProtocolTest,SubmissionGuiTest,ProcessBoundaryTest,LoadProfileTest' test
mvn -DskipTests package
```

Test dùng DB riêng, email giả lập và tài khoản demo; không cần cấu hình SMTP thật.

Emoji: 64 biểu tượng trong bảng chọn dùng ảnh Twemoji đóng gói tại `src/main/resources/emoji`, hiển thị màu offline trong bảng chọn, tin nhắn và reply. Ô nhập RichTextFX hiển thị chữ cùng emoji màu ngay trong dòng đang gõ, không có dòng xem trước. Copy/paste và gửi tin chuyển ảnh inline về Unicode gốc; emoji ngoài bộ ảnh giữ cách hiển thị của font hệ thống. Dữ liệu TCP và SQLite vẫn là chuỗi Unicode. Thông tin tác giả và giấy phép CC BY 4.0 nằm trong `emoji/NOTICE.txt` và `emoji/LICENSE-GRAPHICS`.

- `target/surefire-reports`: báo cáo JUnit.
- `target/load-results.md`: số đo 2 và 16 Client TCP, 30 tin/Client, payload 128 ký tự. Client có luồng nhận liên tục như ứng dụng thật.
- `target/process-smoke.txt`: xác thực qua hai JVM riêng.
- `target/registration-ui.png`, `target/server-ui.png`, `target/chat-ui.png`: ảnh render GUI từ test.
- [Checklist nghiệm thu](../docs/SUBMISSION_CHECKLIST.md): các bước email/LAN/demo cần nhóm thực hiện trước khi nộp.

Giới hạn: TCP chat chưa dùng TLS; chỉ dùng tài khoản demo. Số đo loopback không đại diện cho hiệu năng mạng LAN hoặc giới hạn tải tối đa. Báo cáo/slide và video demo thực tế vẫn cần nhóm hoàn thiện.
