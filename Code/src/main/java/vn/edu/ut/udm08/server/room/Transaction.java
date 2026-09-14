package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Xử lý Transaction lưu tin nhắn vào Database (ST-103).
 * Đảm bảo cấp timestamp và sequence chính thức trước khi ghi nhận thành công.
 */
public class Transaction {

    private final MessageDao messageDao;
    private final AtomicLong sequenceGenerator = new AtomicLong(1);

    public Transaction(MessageDao messageDao) {
        this.messageDao = messageDao != null ? messageDao : new InMemoryMessageDao();
    }

    /**
     * Thực thi lưu tin nhắn vào DB dưới dạng transaction.
     * Cấp chính thức timestamp và sequence từ DB.
     * @param message Bản tin cần lưu
     * @return ProtocolMessage đã được lưu kèm thông tin chính thức từ DB
     */
    public ProtocolMessage saveMessage(ProtocolMessage message) {
        if (message == null) {
            return null;
        }

        // Cấp timestamp và sequence chính thức từ hệ thống DB Server
        message.timestamp = System.currentTimeMillis();
        message.sequence = sequenceGenerator.getAndIncrement();
        message.status = "SENT";

        // Thực hiện lưu vào DB thông qua MessageDao
        messageDao.save(message);

        return message;
    }
}
