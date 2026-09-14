package vn.edu.ut.udm08.client.network;

import java.util.ArrayList;
import java.util.List;
import vn.edu.ut.udm08.shared.model.UserProfile;

public class UserSearchResult {
    private final String requestId;
    private final String keyword;
    private final List<UserProfile> users;

    public UserSearchResult(String requestId, String keyword, List<UserProfile> users) {
        this.requestId = requestId;
        this.keyword = keyword;
        this.users = users == null ? new ArrayList<>() : new ArrayList<>(users);
    }

    public String getRequestId() {
        return requestId;
    }

    public String getKeyword() {
        return keyword;
    }

    public List<UserProfile> getUsers() {
        return new ArrayList<>(users);
    }
}
