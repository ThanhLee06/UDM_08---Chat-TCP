package vn.edu.ut.udm08.client.network.event;

/**
 * Interface cơ sở cho tất cả các sự kiện mạng trong hệ thống Client (ST-053).
 *
 * @author UDM_08 Team
 */
public interface ChatEvent {

    /**
     * Lấy mã định danh cuộc hội thoại phát sinh sự kiện (hoặc null nếu là sự kiện toàn cục).
     *
     * @return convId hoặc null.
     */
    String getConvId();

    /**
     * Lấy thời điểm phát sinh sự kiện (Unix timestamp millis).
     *
     * @return Thời gian tính theo milliseconds.
     */
    long getTimestamp();
}