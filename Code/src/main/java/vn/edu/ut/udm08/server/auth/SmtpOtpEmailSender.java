package vn.edu.ut.udm08.server.auth;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;
public final class SmtpOtpEmailSender implements IOtpEmailSender {
    private final Properties config;
    public SmtpOtpEmailSender() {
        this(loadConfiguration());
    }
    SmtpOtpEmailSender(Properties configuration) {
        config = new Properties();
        config.putAll(configuration);
    }
    private static Properties loadConfiguration() {
        Properties configuration = new Properties();
        Path path = Path.of(System.getProperty("udm08.smtp.config", "smtp.properties"));
        if (Files.exists(path)) {
            try (var input = Files.newInputStream(path)) {
                configuration.load(input);
            } catch (Exception e) {
                throw new IllegalStateException("Không đọc được cấu hình SMTP", e);
            }
        }
        applyEnvironmentSetting(configuration, "UDM08_SMTP_HOST", "smtp.host");
        applyEnvironmentSetting(configuration, "UDM08_SMTP_PORT", "smtp.port");
        applyEnvironmentSetting(configuration, "UDM08_SMTP_USERNAME", "smtp.username");
        applyEnvironmentSetting(configuration, "UDM08_SMTP_PASSWORD", "smtp.password");
        return configuration;
    }
    private static void applyEnvironmentSetting(Properties configuration, String variable, String key) {
        String value = System.getenv(variable);
        if (value != null && !value.isBlank()) {
            configuration.setProperty(key, value);
        }
    }
    @Override
    public void send(String email, String code) throws Exception {
        String username = config.getProperty("smtp.username", "").trim();
        String password = config.getProperty("smtp.password", "");
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình tài khoản gửi email SMTP");
        }
        String host = config.getProperty("smtp.host", "smtp.gmail.com").trim();
        String port = config.getProperty("smtp.port", "587").trim();
        Properties props = new Properties();
        props.setProperty("mail.smtp.host", host);
        props.setProperty("mail.smtp.port", port);
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.starttls.required", "true");
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.connectiontimeout", "10000");
        props.setProperty("mail.smtp.timeout", "15000");
        props.setProperty("mail.smtp.writetimeout", "15000");
        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(email, true));
        message.setSubject("UDM Chat - Mã xác thực email", "UTF-8");
        message.setSentDate(new Date());
        message.setText("Mã xác thực của bạn là: " + code, "UTF-8");
        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(host, Integer.parseInt(port), username, password);
            transport.sendMessage(message, message.getAllRecipients());
        } catch (Exception e) {
            throw new IllegalStateException("Không gửi được email OTP. Kiểm tra cấu hình SMTP và thử lại", e);
        }
    }
}
