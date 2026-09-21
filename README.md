# UDM08 — Chat TCP Client–Server

Ứng dụng desktop JavaFX, giao tiếp TCP, SQLite tập trung ở Server.

## Chạy thử với hai tài khoản có sẵn

Cần JDK 21+ và Internet ở lần đầu để Maven Wrapper tải thư viện. Mở ba terminal trong thư mục `Code`.

Terminal 1 chạy server bình thường:

```powershell
.\mvnw.cmd compile javafx:run '-Djavafx.mainClass=vn.edu.ut.udm08.server.core.ServerApp'
```

Chờ `SERVER_START`, rồi chạy lệnh sau ở cả terminal 2 và 3:

```powershell
.\mvnw.cmd compile javafx:run
```

| Client | Email hoặc số điện thoại | Mật khẩu mẫu |
|---|---|---|
| 1 | `testalice@example.test` hoặc `0909990001` | `TestPass123!` |
| 2 | `testbob@example.test` hoặc `0909990002` | `TestPass123!` |

Server tự tạo hai tài khoản mẫu trong DB đang cấu hình (mặc định `data/udm08_chat.db`). Chạy lại không ghi đè tài khoản, mật khẩu hay lịch sử đã có. Dữ liệu mẫu công khai nằm trong `src/main/resources/sample-accounts.properties`; không cần commit DB.

Đây là ứng dụng bình thường, không có chế độ demo riêng: chat, reply, forward, emoji, đăng ký và reset vẫn dùng luồng thật. Đăng ký/reset cần SMTP được cấu hình để gửi OTP; hai tài khoản có sẵn đăng nhập ngay mà không cần SMTP. Email mẫu không phải hộp thư thật.

- [Hướng dẫn chạy và protocol](Code/README.md)
- [Checklist nghiệm thu theo yêu cầu đề tài](docs/SUBMISSION_CHECKLIST.md)
