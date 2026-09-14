package vn.edu.ut.udm08.server.auth;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SmtpOtpEmailSenderTest {
    @Test
    void missingConfigurationFailsInsteadOfAcceptingDemoOtp() {
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(new Properties());
        assertThrows(IllegalStateException.class, () -> sender.send("recipient@example.com", "123456"));
        EmailOtpService service = new EmailOtpService(sender);
        assertThrows(IllegalStateException.class, () -> service.sendOtp("register:test", "recipient@example.com"));
        assertFalse(service.verifyOtp("register:test", "123456"));
    }
    @Test
    void smtpConnectionFailureIsPropagated() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var closedConnection = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.getOutputStream().flush();
                }
                return null;
            });
            Properties configuration = new Properties();
            configuration.setProperty("smtp.host", server.getInetAddress().getHostAddress());
            configuration.setProperty("smtp.port", String.valueOf(server.getLocalPort()));
            configuration.setProperty("smtp.username", "sender@example.com");
            configuration.setProperty("smtp.password", "test-only-password");
            SmtpOtpEmailSender sender = new SmtpOtpEmailSender(configuration);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> sender.send("recipient@example.com", "123456"));
            assertNotNull(failure.getCause());
            closedConnection.get(5, TimeUnit.SECONDS);
        }
    }
}
