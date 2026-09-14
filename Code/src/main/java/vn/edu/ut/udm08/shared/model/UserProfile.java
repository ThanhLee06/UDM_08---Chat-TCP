package vn.edu.ut.udm08.shared.model;

public class UserProfile {
    public String userId;
    public String username;
    public String displayName;
    public String avatarId;
    public String avatarPath;

    public UserProfile() {}

    public UserProfile(String username, String avatarId) {
        this.username = username;
        this.avatarId = avatarId;
    }
}
