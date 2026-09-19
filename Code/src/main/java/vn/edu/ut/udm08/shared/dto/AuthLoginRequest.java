package vn.edu.ut.udm08.shared.dto;

public class AuthLoginRequest {
    private String usernameOrPhone;
    private String password;

    public AuthLoginRequest() {}

    public AuthLoginRequest(String usernameOrPhone, String password) {
        this.usernameOrPhone = usernameOrPhone;
        this.password = password;
    }

    public String getUsernameOrPhone() {
        return usernameOrPhone;
    }

    public void setUsernameOrPhone(String usernameOrPhone) {
        this.usernameOrPhone = usernameOrPhone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
