package vn.edu.ut.udm08.server.exception;
public class MessageConflictException extends RuntimeException {
    public MessageConflictException(String message) {
        super(message);
    }
    public MessageConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}