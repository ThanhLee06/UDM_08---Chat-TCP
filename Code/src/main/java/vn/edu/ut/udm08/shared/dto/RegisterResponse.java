package vn.edu.ut.udm08.shared.dto;
import vn.edu.ut.udm08.shared.model.User;
public class RegisterResponse {
    private boolean success;
    private String message;
    private User user;
    public RegisterResponse() {}
    public RegisterResponse(boolean success, String message, User user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }
    public static RegisterResponse ok(String message, User user) {
        return new RegisterResponse(true, message, user);
    }
    public static RegisterResponse fail(String message) {
        return new RegisterResponse(false, message, null);
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
