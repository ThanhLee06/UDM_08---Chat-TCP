# Code
Maven project dùng Java 21 và JavaFX thuần cho đề tài UDM_08 Chat TCP Client–Server.
## Phân chia package
- `client/app`: khởi động JavaFX và quản lý vòng đời ứng dụng.
- `client/controller`: controller cho các màn hình FXML.
- `client/network`: kết nối TCP phía client.
- `server/core`: khởi động và điều phối server.
- `server/session`: quản lý phiên kết nối.
- `server/routing`: định tuyến tin nhắn.
- `server/conversation`: nghiệp vụ cuộc trò chuyện.
- `shared/model`: model dùng chung.
- `shared/protocol`: protocol dùng chung giữa client và server.
- `resources/fxml`, `resources/css`, `resources/images`: tài nguyên JavaFX thuần.
Các thư mục rỗng chứa `.gitkeep` để Git lưu được cấu trúc. Xóa `.gitkeep` khi thêm file thật.
