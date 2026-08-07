package vn.edu.ut.udm08.shared.model;
public class UserProfile {
    public String username;
    public String avatarId;
    public UserProfile() {}
    public UserProfile(String username, String avatarId) {
        this.username = username;
        this.avatarId = avatarId;
    }
}