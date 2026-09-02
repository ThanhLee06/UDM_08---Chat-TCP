package vn.edu.ut.udm08.shared.dto;
import vn.edu.ut.udm08.shared.model.User;
public class LoginResponse {
    private boolean success;
    private String message;
    private User user;
    public LoginResponse() {}
    public LoginResponse(boolean success, String message, User user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }
    public static LoginResponse ok(String message, User user) {
        return new LoginResponse(true, message, user);
    }
    public static LoginResponse fail(String message) {
        return new LoginResponse(false, message, null);
    }
    public boolean isSuccess() {
        return success;
    }
    public void setSuccess(boolean success) {
        this.success = success;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
}
