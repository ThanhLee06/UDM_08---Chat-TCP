package vn.edu.ut.udm08.server.auth;
public interface IOtpService {
    void sendOtp(String key, String email);
    boolean verifyOtp(String key, String code);
}
