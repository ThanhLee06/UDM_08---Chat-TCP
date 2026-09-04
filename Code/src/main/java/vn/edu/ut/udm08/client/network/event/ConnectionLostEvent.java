package vn.edu.ut.udm08.client.network.event;

/**
 * Sự kiện thông báo mất kết nối mạng Socket đột ngột.
 */
public class ConnectionLostEvent implements ChatEvent {

    private final Throwable cause;
    private final long timestamp;

    public ConnectionLostEvent(Throwable cause) {
        this.cause = cause;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getConvId() {
        return null;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public Throwable getCause() {
        return cause;
    }
}
