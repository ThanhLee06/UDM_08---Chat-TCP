package vn.edu.ut.udm08.shared.dto;

public class ForgotResetRequest {
    private String resetId;
    private String otpCode;
    private String newPassword;

    public ForgotResetRequest() {}

    public ForgotResetRequest(String resetId, String otpCode, String newPassword) {
        this.resetId = resetId;
        this.otpCode = otpCode;
        this.newPassword = newPassword;
    }

    public String getResetId() {
        return resetId;
    }

    public void setResetId(String resetId) {
        this.resetId = resetId;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
