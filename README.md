# 🚀 UDM_08 — Chat TCP Client-Server Application

Ứng dụng trò chuyện thời gian thực (Desktop Chat Application) dựa trên giao thức TCP mạng Client-Server đa luồng, được xây dựng bằng **Java 21**, **JavaFX**, **RichTextFX**, cơ sở dữ liệu **SQLite** tập trung và dịch vụ gửi mail **SMTP OTP**.

---

## 📋 Mục lục
1. [Giới thiệu dự án](#-giới-thiệu-dự-án)
2. [Tính năng chính](#-tính-năng-chính)
3. [Kiến trúc hệ thống & Giao thức](#-kiến-trúc-hệ-thống--giao-thức)
4. [Yêu cầu môi trường](#-yêu-cầu-môi-trường)
5. [Cấu trúc thư mục & Các File cấu hình](#-cấu-trúc-thư-mục--các-file-cấu-hình)
6. [Hướng dẫn Thiết lập & Cấu hình (Setup Configuration)](#-hướng-dẫn-thiết-lập--cấu-hình-setup-configuration)
7. [Hướng dẫn Khởi chạy Ứng dụng (Running Guide)](#-hướng-dẫn-khởi-chạy-ứng-dụng-running-guide)
8. [Tài khoản kiểm thử mẫu (Sample Accounts)](#-tài-khoản-kiểm-thử-mẫu-sample-accounts)
9. [Kiểm thử & Đóng gói (Test & Build)](#-kiểm-thử--đóng-gói-test--build)

---

## 💡 Giới thiệu dự án

**UDM_08 Chat TCP** là hệ thống nhắn tin desktop đa nền tảng. Hệ thống tách biệt hoàn toàn giữa ứng dụng **Server** (quản lý kết nối TCP, xử lý logic nghiệp vụ, CSDL SQLite và SMTP gửi mail) và ứng dụng **Client** (giao diện người dùng JavaFX). 

- **Đa luồng (Multi-threading):** Server xử lý đồng thời nhiều kết nối Client thông qua Socket TCP.
- **Bảo mật:** Mật khẩu người dùng được băm mã hóa bằng thuật toán **BCrypt** trước khi lưu trữ.
- **Xác thực OTP:** Hỗ trợ đăng ký và quên mật khẩu thông qua mã xác minh gửi về Email qua SMTP.
- **Trải nghiệm giàu tính năng:** Giao diện Rich Text gõ emoji trực tiếp, reply, forward tin nhắn, lịch sử trò chuyện.

---

## ✨ Tính năng chính

### 🔐 1. Xác thực & Quản lý tài khoản
- **Đăng ký tài khoản:** Xác minh mã OTP 6 chữ số gửi qua Email trước khi khởi tạo tài khoản.
- **Đăng nhập linh hoạt:** Đăng nhập bằng Email hoặc Số điện thoại.
- **Bảo mật mật khẩu:** Băm mật khẩu bằng BCrypt (`PasswordEncoder`).
- **Quên / Đặt lại mật khẩu:** Gửi OTP qua Email để khôi phục mật khẩu an toàn.
- **Đơn phiên (Single Session Enforcement):** Mỗi tài khoản chỉ được mở 1 phiên làm việc. Nếu đăng nhập ở thiết bị/màn hình khác, phiên cũ sẽ nhận thông báo `SESSION_KICKED` và bị đăng xuất.

### 💬 2. Trò chuyện thời gian thực
- **Phòng chat chung (General Channel):** Mọi người dùng trực tuyến đều có thể tham gia trò chuyện.
- **Chat riêng 1-1 (Direct Messaging - DM):** Nhắn tin riêng tư giữa 2 người dùng.
- **Phản hồi (Reply) & Chuyển tiếp (Forward):** Hỗ trợ trích dẫn tin nhắn gốc khi phản hồi hoặc chuyển tiếp tin nhắn sang cuộc hội thoại khác.
- **Emoji màu Twemoji Inline:** Hỗ trợ bộ 64 emoji Twemoji chất lượng cao, chèn và hiển thị trực tiếp ngay trong dòng văn bản gõ (RichTextFX).
- **Lịch sử tin nhắn (Message History):** Lưu trữ tập trung tại CSDL SQLite Server. Tải lại lịch sử khi mở hội thoại (phân trang tối đa 100 tin/lần).

### 🖥️ 3. Quản lý Server & Tìm kiếm
- **Giao diện quản lý Server (Server GUI):** Cho phép bật/tắt Server, thay đổi Port, theo dõi log kết nối realtime.
- **Server CLI:** Cho phép khởi chạy Server môi trường dòng lệnh (headless).
- **Tìm kiếm người dùng:** Tìm kiếm bạn bè qua tên người dùng (username), email hoặc số điện thoại.

---

## 🏗️ Kiến trúc hệ thống & Giao thức

### Mô hình giao tiếp
- **Giao thức:** TCP Sockets.
- **Định dạng dữ liệu:** Chuỗi JSON mã hóa **UTF-8**, phân tách theo dòng (`\n`).
- **Cấu trúc khung tin (Envelope Protocol):**
  ```json
  {
    "type": "AUTH_LOGIN",
    "requestId": "req-12345",
    "messageId": "msg-67890",
    "content": "{\"email\":\"user@example.com\",\"password\":\"Pass123!\"}"
  }
  ```

---

## 💻 Yêu cầu môi trường

- **Java Development Kit (JDK):** JDK 21 trở lên (khuyên dùng JDK 21 hoặc JDK 25).
- **Build Tool:** Apache Maven 3.9+ (Dự án đã tích hợp sẵn **Maven Wrapper** `./mvnw` / `.\mvnw.cmd`, không bắt buộc phải cài đặt sẵn Maven trong hệ thống).
- **Mạng:** Kết nối Internet ở lần đầu khởi chạy để Maven tự động tải các thư viện phụ thuộc.

---

## 📁 Cấu trúc thư mục & Các File cấu hình

Dưới đây là sơ đồ cây thư mục chính và vị trí các file cấu hình quan trọng:

```
UDM_08---Chat-TCP/
├── Code/                          # Thư mục mã nguồn chính
│   ├── config/                    # Thư mục chứa cấu hình Client & Server
│   │   ├── client.properties      # Cấu hình IP/Port kết nối của Client
│   │   ├── client.properties.example
│   │   ├── server.properties      # Cấu hình Port, DB, Timeout của Server
│   │   └── server.properties.example
│   ├── data/                      # Thư mục chứa cơ sở dữ liệu (tự động tạo)
│   │   └── udm08_chat.db          # File SQLite CSDL Server
│   ├── logs/                      # Nhật ký hoạt động của Server
│   ├── src/                       # Mã nguồn Java (Client, Server, Shared)
│   ├── smtp.properties            # Cấu hình tài khoản SMTP gửi Mail OTP
│   ├── smtp.properties.example
│   ├── pom.xml                    # File cấu hình Maven
│   ├── run.ps1                    # Script PowerShell khởi chạy nhanh
│   ├── mvnw / mvnw.cmd            # Maven Wrapper
│   └── DEMO_GUIDE.md              # Hướng dẫn chi tiết kịch bản demo
└── README.md                      # Tài liệu hướng dẫn sử dụng dự án
```

### Các file cấu hình chi tiết:

| File cấu hình | Vị trí | Mô tả |
|---|---|---|
| `config/server.properties` | `Code/config/` | Cấu hình cho Server (Port, đường dẫn DB, timeout kết nối, giới hạn độ dài tin nhắn...). |
| `config/client.properties` | `Code/config/` | Cấu hình cho Client (Địa chỉ IP Host, Port kết nối, timeout chờ phản hồi...). |
| `smtp.properties` | `Code/` | Cấu hình tài khoản Mail Server SMTP gửi mã OTP (dùng cho tính năng Đăng ký & Reset mật khẩu). |
| `data/udm08_chat.db` | `Code/data/` | File CSDL SQLite do Server quản lý. |

---

## ⚙️ Hướng dẫn Thiết lập & Cấu hình (Setup Configuration)

Trước khi khởi chạy, bạn cần tạo các file cấu hình tương ứng từ các file mẫu (`.example`):

### Bước 1: Cấu hình Server (`config/server.properties`)
Tạo file `Code/config/server.properties` (hoặc copy từ `server.properties.example`):

```properties
server.port=5000
db.url=jdbc:sqlite:data/udm08_chat.db
db.busyTimeout=5000
message.maxLength=5000
history.maxLimit=100
search.maxLength=100
connection.idleTimeout=300000
```

> 💡 **Lưu ý:** Bạn cũng có thể thay đổi Port trực tiếp trên giao diện Server GUI khi ứng dụng khởi chạy.

### Bước 2: Cấu hình Client (`config/client.properties`)
Tạo file `Code/config/client.properties` (hoặc copy từ `client.properties.example`):

```properties
server.host=127.0.0.1
server.port=5000
connection.timeout=5000
request.timeout=15000
```

* Nếu Client và Server chạy trên cùng 1 máy: Giữ nguyên `server.host=127.0.0.1`.
* Nếu Client ở máy khác trong cùng mạng LAN: Đổi `server.host` thành địa chỉ IP LAN của máy chạy Server (Ví dụ: `192.168.1.15`).

### Bước 3: Cấu hình SMTP gửi Mail OTP (`smtp.properties`) *(Tùy chọn)*
Nếu bạn muốn sử dụng tính năng **Đăng ký tài khoản mới** hoặc **Quên mật khẩu** có gửi mã OTP thật về Email, hãy tạo file `Code/smtp.properties` (copy từ `smtp.properties.example`):

```properties
smtp.host=smtp.gmail.com
smtp.port=587
smtp.username=email-cua-ban@gmail.com
smtp.password=mat-khau-ung-dung-app-password
```

> ⚠️ **Lưu ý an toàn:** Tuyệt đối không commit file `smtp.properties` chứa mật khẩu thật lên Git repository.
> 
> Bạn cũng có thể thiết lập qua Biến môi trường hệ thống thay vì lưu file:
> - `UDM08_SMTP_HOST`
> - `UDM08_SMTP_PORT`
> - `UDM08_SMTP_USERNAME`
> - `UDM08_SMTP_PASSWORD`

---

## 🚀 Hướng dẫn Khởi chạy Ứng dụng (Running Guide)

Chuyển vào thư mục `Code` trước khi thực hiện các lệnh bên dưới:
```powershell
cd Code
```

### ⚡ Cách 1: Sử dụng Script `run.ps1` (Khuyên dùng trên Windows)

**Terminal 1: Khởi chạy Server GUI**
```powershell
.\run.ps1 -Mode server
```
* Trên giao diện Server GUI: Kiểm tra Port (mặc định `5000`) -> Bấm **"Lưu và bật Server"**.

**Terminal 2: Khởi chạy Client 1**
```powershell
.\run.ps1 -Mode client
```

**Terminal 3: Khởi chạy Client 2**
```powershell
.\run.ps1 -Mode client
```

---

### 🛠️ Cách 2: Sử dụng Maven / Maven Wrapper (`mvnw`)

Nếu hệ thống chưa cài sẵn Maven, hãy sử dụng `.\mvnw.cmd` (trên Windows) hoặc `./mvnw` (trên Linux/macOS).

**Khởi chạy Server GUI:**
```powershell
.\mvnw.cmd compile javafx:run '-Djavafx.mainClass=vn.edu.ut.udm08.server.core.ServerApp'
```

**Khởi chạy Server CLI (không giao diện):**
```powershell
.\mvnw.cmd compile exec:java
```

**Khởi chạy Client GUI:**
```powershell
.\mvnw.cmd compile javafx:run
```

---

## 👥 Tài khoản kiểm thử mẫu (Sample Accounts)

Để thuận tiện cho việc chạy thử nghiệm mà không cần cấu hình dịch vụ Email SMTP thật, khi Server khởi chạy lần đầu tiên, hệ thống sẽ **tự động khởi tạo 2 tài khoản mẫu** trong CSDL:

| STT | Username | Email | Số điện thoại | Mật khẩu mẫu | Ghi chú |
|---|---|---|---|---|---|
| 1 | `testalice` | `testalice@example.test` | `0909990001` | `TestPass123!` | Dùng đăng nhập ở Client 1 |
| 2 | `testbob` | `testbob@example.test` | `0909990002` | `TestPass123!` | Dùng đăng nhập ở Client 2 |

> 📌 Bạn có thể đăng nhập bằng **Username**, **Email** hoặc **Số điện thoại** kết hợp với mật khẩu tương ứng.

---

## 🧪 Kiểm thử & Đóng gói (Test & Build)

### Chạy Unit & Integration Tests
Dự án bao gồm bộ kiểm thử tự động với JUnit 5 (sử dụng CSDL SQLite tạm thời và dịch vụ giả lập):

```powershell
# Chạy toàn bộ bộ test
.\mvnw.cmd test

# Chạy cụ thể các test tích hợp & giao diện
.\mvnw.cmd '-Dtest=SubmissionProtocolTest,SubmissionGuiTest,ProcessBoundaryTest,LoadProfileTest' test
```

### Đóng gói ứng dụng (Build JAR)
```powershell
.\mvnw.cmd clean package -DskipTests
```

---

## 📝 Tài liệu tham khảo thêm
- [Hướng dẫn Kịch bản Chạy Demo chi tiết (DEMO_GUIDE.md)](Code/DEMO_GUIDE.md)
- [Checklist kiểm tra nghiệm thu (docs/SUBMISSION_CHECKLIST.md)](docs/SUBMISSION_CHECKLIST.md)
