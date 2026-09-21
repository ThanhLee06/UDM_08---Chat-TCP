package vn.edu.ut.udm08.shared.dto;

public class ForgotInitRequest {
    private String phoneOrEmail;

    public ForgotInitRequest() {}

    public ForgotInitRequest(String phoneOrEmail) {
        this.phoneOrEmail = phoneOrEmail;
    }

    public String getPhoneOrEmail() {
        return phoneOrEmail;
    }

    public void setPhoneOrEmail(String phoneOrEmail) {
        this.phoneOrEmail = phoneOrEmail;
    }
}
