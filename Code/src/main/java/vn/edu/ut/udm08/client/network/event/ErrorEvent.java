package vn.edu.ut.udm08.client.network.event;

/**
 * Sự kiện thông báo lỗi từ Server (ERROR).
 */
public class ErrorEvent implements ChatEvent {

    private final String convId;
    private final String errorCode;
    private final String errorMessage;
    private final long timestamp;

    public ErrorEvent(String errorCode, String errorMessage) {
        this(null, errorCode, errorMessage);
    }

    public ErrorEvent(String convId, String errorCode, String errorMessage) {
        this.convId = convId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getConvId() {
        return convId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
