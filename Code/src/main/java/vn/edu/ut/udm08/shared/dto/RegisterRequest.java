package vn.edu.ut.udm08.shared.dto;

public class RegisterRequest {
    private String username;
    private String phoneNumber;
    private String password;
    private String otpCode;
    private String avatarType;
    private String avatarPath;

    public RegisterRequest() {}

    public RegisterRequest(String username, String phoneNumber, String password, String otpCode, String avatarType, String avatarPath) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.otpCode = otpCode;
        this.avatarType = avatarType;
        this.avatarPath = avatarPath;
    }

    public RegisterRequest(String username, String phoneNumber, String password, String avatarType, String avatarPath) {
        this(username, phoneNumber, password, null, avatarType, avatarPath);
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
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
