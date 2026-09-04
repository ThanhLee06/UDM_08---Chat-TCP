package vn.edu.ut.udm08.client.network.event;

import vn.edu.ut.udm08.shared.model.UserProfile;
import java.util.Collections;
import java.util.List;

/**
 * Sự kiện cập nhật danh sách người dùng trực tuyến (USER_LIST).
 */
public class UserListUpdatedEvent implements ChatEvent {

    private final List<UserProfile> users;
    private final long timestamp;

    public UserListUpdatedEvent(List<UserProfile> users) {
        this.users = users != null ? Collections.unmodifiableList(users) : Collections.emptyList();
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

    public List<UserProfile> getUsers() {
        return users;
    }
}
