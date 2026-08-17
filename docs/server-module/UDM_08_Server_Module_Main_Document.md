# UDM_08 Chat TCP --- Server Module Technical Overview

> **Personal technical document --- Server TCP & Connection Management**
>
> Project: `UDM_08---Chat-TCP`\
> Technology: Java 21 + Maven + JavaFX\
> Main task: **Xây dựng Server TCP và quản lý kết nối**\
> Parent Issue: **#4**\
> Current working progress: **ST-01, ST-02 completed**

------------------------------------------------------------------------

# 1. Tổng quan phần công việc được giao

## 1.1. Task lớn

**Xây dựng Server TCP và quản lý kết nối**

Phạm vi trách nhiệm cá nhân tập trung vào việc xây dựng phần Server TCP
của hệ thống Chat Client--Server bằng Java.

Server phải:

-   Mở cổng TCP thông qua `ServerSocket`.
-   Đọc Port từ cấu hình thay vì hard-code.
-   Liên tục chờ Client bằng `accept()`.
-   Bàn giao mỗi `Socket` mới cho một `ClientSession` chạy độc lập.
-   Giao tiếp bằng **JSON Lines UTF-8**.
-   Đóng Socket và Stream đúng cách khi Client ngắt kết nối.
-   Cô lập lỗi của từng Client để một Client gặp sự cố không làm Server
    dừng.
-   Tích hợp với Message Protocol dùng chung của hệ thống.

------------------------------------------------------------------------

# 2. Bức tranh tổng thể kiến trúc hệ thống

## 2.1. Kiến trúc mức cao

``` text
                         CHAT TCP SYSTEM
                               │
               ┌───────────────┴───────────────┐
               │                               │
            CLIENT                           SERVER
               │                               │
      ┌────────┴────────┐              ┌───────┴────────┐
      │                 │              │                │
 JavaFX UI        Client Network    ChatServer      Server Modules
      │                 │              │                │
      │                 │              │       ┌────────┼─────────┐
      │                 │              │       │        │         │
      │                 │              │    Session  Routing  Conversation
      │                 │              │       │
      └───────── TCP Socket ───────────┘       │
                                               │
                                         ClientSession
                                               │
                                               │
                                      JSON Lines / UTF-8
                                               │
                                               ▼
                                      Shared Protocol
                                               │
                                    ┌──────────┴──────────┐
                                    │                     │
                              ProtocolMessage       MessageType
```

------------------------------------------------------------------------

## 2.2. Cấu trúc package hiện tại

``` text
src/
└── main/
    ├── java/
    │   └── vn/
    │       └── edu/
    │           └── ut/
    │               └── udm08/
    │                   ├── client/
    │                   │   ├── app/
    │                   │   ├── controller/
    │                   │   └── network/
    │                   │
    │                   ├── server/
    │                   │   ├── core/
    │                   │   ├── session/
    │                   │   ├── routing/
    │                   │   └── conversation/
    │                   │
    │                   └── shared/
    │                       ├── model/
    │                       └── protocol/
    │
    └── resources/
        ├── css/
        ├── fxml/
        ├── images/
        └── server.properties
```

### Trách nhiệm các package Server

  Package                 Trách nhiệm
  ----------------------- ------------------------------------------
  `server/core`           Khởi động, cấu hình và điều phối Server
  `server/session`        Quản lý một phiên kết nối Client
  `server/routing`        Định tuyến Message tới Client phù hợp
  `server/conversation`   Xử lý nghiệp vụ cuộc trò chuyện
  `shared/model`          Model và Message Type dùng chung
  `shared/protocol`       Serialization / deserialization protocol

------------------------------------------------------------------------

# 3. Shared Protocol hiện tại

Client và Server không tự định nghĩa format riêng mà sử dụng model dùng
chung.

## 3.1. MessageType

``` java
public enum MessageType {
    HELLO,
    HELLO_OK,
    USER_LIST,
    CHAT,
    CHAT_OK,
    ERROR,
    DISCONNECT
}
```

Các loại Message này là nền tảng để Server và Client hiểu cùng một
protocol.

------------------------------------------------------------------------

## 3.2. ProtocolMessage

``` text
ProtocolMessage
├── type
├── messageId
├── sender
├── target
├── content
├── avatarId
├── timestamp
├── users
├── errorCode
└── errorMessage
```

`ProtocolMessage` là object trung tâm được truyền giữa Client và Server.

------------------------------------------------------------------------

## 3.3. JSON serialization

`JsonUtil` hiện tại sử dụng Jackson:

``` text
ProtocolMessage
      │
      ▼
ObjectMapper
      │
      ▼
JSON String
      │
      ▼
TCP
      │
      ▼
JSON String
      │
      ▼
ObjectMapper
      │
      ▼
ProtocolMessage
```

Server module sẽ sử dụng cơ chế này khi triển khai JSON Lines ở ST-05.

------------------------------------------------------------------------

# 4. Server Architecture theo từng lớp trách nhiệm

## 4.1. `ServerConfig`

Chịu trách nhiệm:

-   Đọc configuration.
-   Lấy `server.port`.
-   Parse Port.
-   Validate Port.
-   Cung cấp Port cho `ChatServer`.

``` text
server.properties
       │
       ▼
ServerConfig
       │
       ▼
validated port
```

------------------------------------------------------------------------

## 4.2. `ChatServer`

Chịu trách nhiệm cấp Server:

``` text
ChatServer
    │
    ├── nhận ServerConfig
    ├── tạo ServerSocket
    ├── listen
    ├── accept Client
    └── bàn giao Socket
            │
            ▼
       ClientSession
```

`ChatServer` không nên ôm toàn bộ nghiệp vụ Client.

------------------------------------------------------------------------

## 4.3. `ClientSession`

Mỗi Client connection tương ứng với một Session:

``` text
Client A ──► Socket A ──► ClientSession A ──► Thread A

Client B ──► Socket B ──► ClientSession B ──► Thread B

Client C ──► Socket C ──► ClientSession C ──► Thread C
```

Mục tiêu là cô lập connection:

``` text
Client A lỗi
    ↓
Session A kết thúc
    ↓
Client B/C vẫn hoạt động
    ↓
Server không dừng
```

------------------------------------------------------------------------

## 4.4. Routing

Sau khi Session nhận được Message:

``` text
ClientSession
      │
      ▼
ProtocolMessage
      │
      ▼
Routing
      │
      ├── target Client
      │
      └── conversation logic
```

Routing là phần tiếp theo của luồng Server, không thuộc ST-03.

------------------------------------------------------------------------

# 5. Breakdown toàn bộ Task lớn

Parent Issue:

> **#4 --- Xây dựng Server TCP và quản lý kết nối**

Được chia thành 8 Subtask:

  -------------------------------------------------------------------------
  ST                               Issue Nội dung          Trạng thái
  ---------------- --------------------- ----------------- ----------------
  ST-01                               #6 Chuẩn bị môi      ✅ Done
                                         trường và cấu     
                                         trúc Server       

  ST-02                               #7 Xây dựng cơ chế   ✅ Done
                                         cấu hình Server   
                                         Port              

  ST-03                               #8 Xây dựng          ⏳ Next
                                         `ChatServer` và   
                                         cơ chế lắng nghe  
                                         kết nối TCP       

  ST-04                               #9 Xây dựng          ⏳
                                         `ClientSession`   
                                         và xử lý Client   
                                         độc lập           

  ST-05                              #10 Xây dựng cơ chế   ⏳
                                         TCP I/O với JSON  
                                         Lines UTF-8       

  ST-06                              #11 Xử lý Disconnect  ⏳
                                         và giải phóng tài 
                                         nguyên            

  ST-07                              #12 Cô lập Exception  ⏳
                                         và đảm bảo Server 
                                         Stability         

  ST-08                              #13 Tích hợp Server   ⏳
                                         với Message       
                                         Protocol và các   
                                         thành phần Client 
  -------------------------------------------------------------------------

------------------------------------------------------------------------

# 6. Chi tiết mục tiêu từng Subtask

## ST-01 --- Chuẩn bị môi trường và cấu trúc Server

### Mục tiêu

Chuẩn bị project để có thể phát triển Server module.

### Kết quả

``` text
server/
├── core/
├── session/
├── routing/
└── conversation/
```

Đồng thời xác nhận Maven/Java project có thể được mở và phát triển trong
môi trường hiện tại.

### Trạng thái

**DONE**

------------------------------------------------------------------------

## ST-02 --- Cấu hình Server Port

### Mục tiêu

Loại bỏ Port hard-code khỏi Server.

### Thiết kế

``` text
server.properties
server.port=8080
        │
        ▼
ServerConfig
        │
        ▼
validate
        │
        ▼
ChatServer
```

### Implementation

-   `server.properties`
-   `ServerConfig`
-   `ChatServer` nhận `ServerConfig`
-   Validate Port trong khoảng `1–65535`

### Verification

`ServerConfigTest`:

``` text
6 tests passed
6 tests total
0 failures
0 errors
```

### Baseline build fix

Trong quá trình verification, Maven bị chặn bởi lỗi có sẵn:

``` text
Mainapp.java
public class MainApp
```

File được đổi thành:

``` text
MainApp.java
```

Đây là build-blocking baseline fix, không phải chức năng chính của
ST-02.

### Trạng thái

**DONE --- merged to `main`**

------------------------------------------------------------------------

## ST-03 --- ChatServer và TCP Listener

### Mục tiêu

Xây dựng Server thực sự mở TCP port và chờ Client.

### Luồng

``` text
ServerConfig
     │
     ▼
ChatServer
     │
     ▼
ServerSocket(port)
     │
     ▼
while (running)
     │
     ▼
accept()
     │
     ▼
Socket
```

### Chưa xử lý

-   ClientSession implementation chi tiết.
-   JSON Lines.
-   Routing.
-   Conversation.

Những phần này thuộc các ST tiếp theo.

------------------------------------------------------------------------

## ST-04 --- ClientSession

### Mục tiêu

Mỗi Client connection được quản lý bởi một Session riêng.

``` text
Socket
  │
  ▼
ClientSession
  │
  ▼
Thread
```

Mục tiêu chính:

-   Quản lý lifecycle của Client.
-   Độc lập giữa các Client.
-   Chuẩn bị nền tảng cho đọc/gửi Message.

------------------------------------------------------------------------

## ST-05 --- TCP I/O và JSON Lines UTF-8

### Mục tiêu

Chuẩn hóa dữ liệu truyền qua TCP.

``` text
ProtocolMessage
      │
      ▼
JsonUtil.toJson()
      │
      ▼
JSON + "\n"
      │
      ▼
UTF-8 OutputStream/Writer
      │
      ▼
TCP
```

Chiều nhận:

``` text
TCP
 │
 ▼
UTF-8 Reader
 │
 ▼
readLine()
 │
 ▼
JSON
 │
 ▼
JsonUtil.fromJson()
 │
 ▼
ProtocolMessage
```

Một Message tương ứng một JSON line.

------------------------------------------------------------------------

## ST-06 --- Disconnect và Resource Management

### Mục tiêu

Khi Client disconnect:

``` text
Client disconnect
       │
       ▼
detect EOF / IOException
       │
       ▼
close Reader
close Writer
close Socket
       │
       ▼
release Session
```

Không để Socket/Stream bị giữ lại gây leak tài nguyên.

------------------------------------------------------------------------

## ST-07 --- Exception Isolation và Server Stability

### Mục tiêu

Một Client lỗi không được làm Server chết.

``` text
Server
 │
 ├── Session A → lỗi
 │                  ↓
 │              catch/cleanup
 │                  ↓
 │              Session A stop
 │
 ├── Session B → tiếp tục
 │
 └── Session C → tiếp tục
```

Nguyên tắc:

> Exception của một Client phải được xử lý tại phạm vi Client Session,
> không làm văng ra khỏi Server accept loop.

------------------------------------------------------------------------

## ST-08 --- Integration

### Mục tiêu

Kết nối hoàn chỉnh các module:

``` text
Client
  │
  │ TCP
  ▼
ChatServer
  │
  ▼
ClientSession
  │
  ▼
JSON Lines
  │
  ▼
ProtocolMessage
  │
  ▼
Routing / Conversation
  │
  ▼
Client
```

Đây là Subtask hoàn thiện integration của phần Server với protocol và
Client.

------------------------------------------------------------------------

# 7. Cơ sở lý thuyết chính

## 7.1. TCP

TCP là giao thức truyền tải hướng kết nối.

Đặc điểm quan trọng đối với đồ án:

-   Client phải thiết lập kết nối tới Server.
-   Server phải listen trên một Port.
-   Dữ liệu truyền qua một TCP connection.
-   TCP đảm bảo thứ tự byte stream.
-   TCP không tự hiểu Message boundary.

Điểm cuối rất quan trọng:

> TCP cung cấp byte stream, không cung cấp khái niệm "mỗi lần gửi là một
> Message".

Vì vậy hệ thống cần protocol framing.

------------------------------------------------------------------------

# 7.2. `ServerSocket`

`ServerSocket` đại diện cho listening socket phía Server.

Luồng cơ bản:

``` text
new ServerSocket(port)
        │
        ▼
Server đang LISTEN
        │
        ▼
accept()
        │
        ▼
Socket của Client
```

`ServerSocket` dùng để chờ connection mới.

`Socket` dùng để giao tiếp với một Client cụ thể.

------------------------------------------------------------------------

# 7.3. `accept()`

`accept()` là thao tác blocking:

``` text
accept()
   │
   ├── chưa có Client → chờ
   │
   └── có Client → trả Socket
```

Sau khi nhận Socket, Server có thể tiếp tục xử lý Client.

Đây là lý do Server phải có cơ chế concurrency/session để không biến một
Client thành điểm nghẽn cho toàn hệ thống.

------------------------------------------------------------------------

# 7.4. Thread per Client

Thiết kế của task:

``` text
accept()
   │
   ▼
Socket
   │
   ▼
new ClientSession(socket)
   │
   ▼
new Thread(...)
```

Mỗi Client có execution context riêng.

Lợi ích:

-   Client này blocking không trực tiếp chặn Client khác.
-   Dễ cô lập lỗi.
-   Dễ quản lý lifecycle của từng connection.

Trade-off:

-   Nhiều Client → nhiều Thread.
-   Cần quản lý tài nguyên.
-   Sau này có thể cân nhắc Executor/Thread Pool nếu yêu cầu mở rộng.

------------------------------------------------------------------------

# 7.5. JSON Lines

JSON Lines là cách framing đơn giản:

``` text
JSON Message 1\n
JSON Message 2\n
JSON Message 3\n
```

Ví dụ:

``` json
{"type":"CHAT","sender":"alice","target":"bob","content":"Hello"}
```

Mỗi dòng là một JSON object hoàn chỉnh.

Vì vậy phía nhận có thể:

``` java
reader.readLine();
```

để lấy từng Message.

------------------------------------------------------------------------

# 7.6. UTF-8

Protocol quy định dữ liệu dạng JSON Lines UTF-8.

Điều này đảm bảo nội dung tiếng Việt và các ký tự Unicode được
truyền/đọc nhất quán.

``` text
String
  ↓
UTF-8 encoding
  ↓
TCP bytes
  ↓
UTF-8 decoding
  ↓
String
```

Không được mặc định rằng byte encoding ở hai đầu luôn giống nhau.

------------------------------------------------------------------------

# 7.7. Resource Management

Các resource quan trọng:

``` text
ServerSocket
Socket
InputStream / Reader
OutputStream / Writer
```

Khi connection kết thúc phải đảm bảo resource được đóng.

Mục tiêu:

``` text
Connection closed
       ↓
Stream closed
       ↓
Socket closed
       ↓
Session released
```

Java nên ưu tiên `try-with-resources` ở những nơi phù hợp.

------------------------------------------------------------------------

# 7.8. Exception Isolation

Đây là yêu cầu quan trọng của task lớn.

Không nên để:

``` text
Client A Exception
       ↓
Server accept loop exception
       ↓
Server stops
```

Mà phải:

``` text
Client A Exception
       ↓
ClientSession A handles exception
       ↓
cleanup A
       ↓
Server continues
       ↓
accept next Client
```

Đây là một trong những điểm cần có khả năng giải thích khi vấn đáp.

------------------------------------------------------------------------

# 8. Workflow làm việc chuẩn cho từng Subtask

Mỗi ST được thực hiện như một đơn vị hoàn chỉnh.

``` text
GitHub Issue
     ↓
Create Branch
     ↓
Analysis / Requirement
     ↓
Design
     ↓
Implementation
     ↓
Local Verification
     ↓
Git Review
     ↓
Commit
     ↓
Push
     ↓
Pull Request
     ↓
CI / Code Review
     ↓
Merge
     ↓
Update Issue → Done
     ↓
Update Documentation / Evidence
```

------------------------------------------------------------------------

## 8.1. Branch convention

Branch sử dụng:

``` text
feature/ST-XX-short-description
```

Ví dụ:

``` text
feature/ST-03-chatserver-tcp-listener
```

Source branch:

``` text
main
```

------------------------------------------------------------------------

## 8.2. Commit convention

Commit nên mô tả đúng thay đổi.

Ví dụ:

``` text
feat(server): implement TCP listener
```

Không dùng commit message chung chung như:

``` text
update
fix
test
code
```

------------------------------------------------------------------------

## 8.3. Pull Request

Mỗi ST có PR riêng.

Cấu trúc:

``` text
feature/ST-XX-...
          │
          ▼
        main
```

PR cần có:

-   Summary.
-   Changes.
-   Testing.
-   Scope.
-   Related Issue.
-   Notes về baseline issue nếu có.

------------------------------------------------------------------------

## 8.4. Verification

Mỗi ST phải có bằng chứng phù hợp:

``` text
Implementation
      +
Unit Test / Integration Test
      +
Build / CI
      +
PR
```

Không chỉ dựa vào việc code compile.

------------------------------------------------------------------------

# 9. Quy tắc quản lý phạm vi

Để tránh các ST bị chồng chéo:

``` text
ST-01 → chuẩn bị
ST-02 → configuration
ST-03 → ServerSocket / accept
ST-04 → ClientSession
ST-05 → JSON Lines I/O
ST-06 → disconnect / resources
ST-07 → exception isolation / stability
ST-08 → integration
```

Nếu phát hiện lỗi thuộc một ST khác:

-   Không tự ý mở rộng scope.
-   Ghi nhận lỗi.
-   Chỉ sửa nếu lỗi đó là blocker trực tiếp của ST hiện tại.
-   Nếu phải sửa baseline để build/test được, ghi rõ trong PR.

------------------------------------------------------------------------

# 10. Tiến độ hiện tại

## Overall

``` text
Parent Issue #4
Xây dựng Server TCP và quản lý kết nối

ST-01  ████████████████████  DONE
ST-02  ████████████████████  DONE
ST-03  ░░░░░░░░░░░░░░░░░░░░  NEXT
ST-04  ░░░░░░░░░░░░░░░░░░░░  TODO
ST-05  ░░░░░░░░░░░░░░░░░░░░  TODO
ST-06  ░░░░░░░░░░░░░░░░░░░░  TODO
ST-07  ░░░░░░░░░░░░░░░░░░░░  TODO
ST-08  ░░░░░░░░░░░░░░░░░░░░  TODO
```

### Completed

#### ST-01 --- #6

-   Đã chuẩn bị môi trường.
-   Đã xác nhận cấu trúc Maven/Java project.
-   Đã chuẩn bị package Server.
-   Đã tạo `ChatServer`.
-   Đã tạo `ClientSession`.
-   Đã commit/push/PR/merge.
-   **Status: DONE**

#### ST-02 --- #7

-   Đã tạo `server.properties`.
-   Đã cấu hình `server.port`.
-   Đã tạo `ServerConfig`.
-   Đã validate Port.
-   Đã tích hợp Port vào `ChatServer`.
-   Đã tạo `ServerConfigTest`.
-   **6/6 tests passed.**
-   Đã xử lý lỗi baseline `Mainapp.java` → `MainApp.java` để unblock
    build/test.
-   Đã commit/push/PR/merge.
-   **Status: DONE**

------------------------------------------------------------------------

# 11. Next Milestone --- ST-03

## Mục tiêu

Xây dựng TCP Listener thực sự cho `ChatServer`.

Luồng cần đạt:

``` text
ServerConfig
     │
     ▼
ChatServer
     │
     ▼
ServerSocket(port)
     │
     ▼
accept()
     │
     ▼
Client Socket
```

### Expected result

Server có thể:

1.  Load Port từ configuration.
2.  Mở `ServerSocket`.
3.  Listen liên tục.
4.  Nhận connection từ Client.
5.  Không trộn nghiệp vụ Session/JSON/Routing vào ST-03.

### Branch dự kiến

``` text
feature/ST-03-chatserver-tcp-listener
```

------------------------------------------------------------------------

# 12. Checklist cuối Task lớn

Khi hoàn thành toàn bộ 8 ST:

``` text
[ ] Server đọc Port từ configuration
[ ] Server mở ServerSocket
[ ] Server accept Client
[ ] Mỗi Client có ClientSession
[ ] ClientSession chạy độc lập
[ ] TCP I/O sử dụng UTF-8
[ ] Protocol sử dụng JSON Lines
[ ] Socket/Streams được đóng đúng cách
[ ] Disconnect được xử lý
[ ] Exception của Client không làm Server chết
[ ] Server tích hợp Message Protocol
[ ] Client ↔ Server integration hoạt động
[ ] Unit/Integration tests pass
[ ] GitHub PR workflow hoàn tất
[ ] Documentation hoàn tất
[ ] Demo cuối kỳ hoàn chỉnh
```

------------------------------------------------------------------------

# 13. Mục tiêu Demo và Vấn đáp

Phần Server cần có khả năng demo theo một luồng liên tục:

``` text
Start Server
     ↓
Load server.properties
     ↓
ServerSocket LISTEN
     ↓
Client 1 connect
     ↓
ClientSession 1
     ↓
Client 2 connect
     ↓
ClientSession 2
     ↓
Messages exchanged
     ↓
Client disconnect
     ↓
Resources released
     ↓
Server continues
```

Các câu hỏi lý thuyết trọng tâm cần chuẩn bị:

1.  TCP khác UDP ở đâu và tại sao Chat dùng TCP?
2.  `ServerSocket` khác `Socket` như thế nào?
3.  `accept()` hoạt động ra sao?
4.  Vì sao phải tách `ClientSession` khỏi `ChatServer`?
5.  Vì sao một Client không được làm Server chết?
6.  Vì sao TCP cần JSON Lines để xác định Message boundary?
7.  Vì sao dùng UTF-8?
8.  Vì sao phải đóng Socket/Stream?
9.  Điều gì xảy ra khi Client mất mạng đột ngột?
10. Vì sao không hard-code Port?
11. Nếu có nhiều Client cùng lúc thì Server xử lý thế nào?
12. Exception nên được bắt ở đâu để cô lập lỗi?

------------------------------------------------------------------------

# 14. Reference Architecture

``` text
                           ┌─────────────────────┐
                           │   server.properties │
                           │   server.port=8080  │
                           └──────────┬──────────┘
                                      │
                                      ▼
                           ┌─────────────────────┐
                           │    ServerConfig     │
                           │ load + validate     │
                           └──────────┬──────────┘
                                      │
                                      ▼
                           ┌─────────────────────┐
                           │     ChatServer      │
                           │                     │
                           │  ServerSocket       │
                           │       │             │
                           │    accept()         │
                           └───────┬─────────────┘
                                   │
                     ┌─────────────┼─────────────┐
                     │             │             │
                     ▼             ▼             ▼
                ClientSession  ClientSession  ClientSession
                     │             │             │
                  Thread A      Thread B      Thread C
                     │             │             │
                     └─────────────┼─────────────┘
                                   │
                             JSON Lines UTF-8
                                   │
                                   ▼
                          ┌──────────────────┐
                          │ ProtocolMessage  │
                          │   + MessageType  │
                          └────────┬─────────┘
                                   │
                              ┌────┴────┐
                              ▼         ▼
                           Routing  Conversation
                              │         │
                              └────┬────┘
                                   ▼
                                Client
```

------------------------------------------------------------------------

## Document Status

**Owner:** Server module developer\
**Parent Issue:** #4\
**Subtasks:** #6 → #13\
**Current:** ST-01 ✅ / ST-02 ✅ / ST-03 ⏳\
**Last completed:** ST-02 --- Server Port Configuration\
**Next:** ST-03 --- ChatServer & TCP Listener
