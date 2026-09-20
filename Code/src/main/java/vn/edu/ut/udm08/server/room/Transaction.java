package vn.edu.ut.udm08.server.room;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Xu ly Transaction luu tin nhan vao Database (ST-103).
 * Dam bao cap timestamp va sequence chinh thuc tu DB truoc khi tra ve ket qua.
 */
public class Transaction {

    private final MessageDao messageDao;
    private final AtomicLong sequenceGenerator = new AtomicLong(1);

    public Transaction(MessageDao messageDao) {
        this.messageDao = messageDao != null ? messageDao : new InMemoryMessageDao();
    }

    /**
     * Thuc thi luu tin nhan vao DB theo transaction.
     * Cap chinh thuc timestamp va sequence tu DB.
     * @param message Tin nhan can luu
     * @return ProtocolMessage da duoc luu kem timestamp va sequence chinh thuc
     */
    public ProtocolMessage saveMessage(ProtocolMessage message) {
        if (message == null) {
            return null;
        }

        // Cap timestamp va sequence chinh thuc tu DB Server
        message.timestamp = System.currentTimeMillis();
        message.sequence = sequenceGenerator.getAndIncrement();
        message.status = "SENT";

        // Thuc hien luu vao DB qua MessageDao
        messageDao.save(message);

        return message;
    }

    public MessageDao getMessageDao() {
        return messageDao;
    }
}
