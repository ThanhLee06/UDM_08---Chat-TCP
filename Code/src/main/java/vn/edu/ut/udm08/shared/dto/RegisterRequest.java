package vn.edu.ut.udm08.shared.dto;
public class RegisterRequest {
    private String username;
    private String phoneNumber;
    private String email;
    private String password;
    private String avatarType;
    private String avatarPath;
    public RegisterRequest() {}
    public RegisterRequest(String username, String phoneNumber, String email, String password, String avatarType, String avatarPath) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.password = password;
        this.avatarType = avatarType;
        this.avatarPath = avatarPath;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getAvatarType() {
        return avatarType;
    }
    public void setAvatarType(String avatarType) {
        this.avatarType = avatarType;
    }
    public String getAvatarPath() {
        return avatarPath;
    }
    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }
}
