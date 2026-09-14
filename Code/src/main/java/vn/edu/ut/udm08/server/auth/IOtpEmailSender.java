package vn.edu.ut.udm08.server.auth;
@FunctionalInterface
public interface IOtpEmailSender {
    void send(String email, String code) throws Exception;
}
