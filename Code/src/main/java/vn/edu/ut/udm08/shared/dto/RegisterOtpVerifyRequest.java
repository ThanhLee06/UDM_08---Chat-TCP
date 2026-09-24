package vn.edu.ut.udm08.shared.dto;

public class RegisterOtpVerifyRequest {
    private String registrationId;
    private String otpCode;

    public RegisterOtpVerifyRequest() {}

    public RegisterOtpVerifyRequest(String registrationId, String otpCode) {
        this.registrationId = registrationId;
        this.otpCode = otpCode;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
