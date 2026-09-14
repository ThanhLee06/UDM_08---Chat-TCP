package vn.edu.ut.udm08.server.auth;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
public final class EmailOtpService implements IOtpService {
    public static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_PENDING_CODES = 1000;
    private final IOtpEmailSender sender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> entries = new HashMap<>();
    public EmailOtpService(IOtpEmailSender sender) {
        this(sender, Clock.systemUTC());
    }
    public EmailOtpService(IOtpEmailSender sender, Clock clock) {
        this.sender = sender;
        this.clock = clock;
    }
    @Override
    public synchronized void sendOtp(String key, String email) {
        Instant now = clock.instant();
        entries.entrySet().removeIf(e -> !now.isBefore(e.getValue().expires));
        Entry previous = entries.get(key);
        if (previous != null && now.isBefore(previous.sent.plus(COOLDOWN))) {
            throw new IllegalStateException("Vui lòng chờ 60 giây trước khi gửi lại mã OTP");
        }
        if (entries.size() >= MAX_PENDING_CODES && previous == null) {
            throw new IllegalStateException("Có quá nhiều yêu cầu OTP vui lòng thử lại sau");
        }
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        try {
            sender.send(email, code);
        } catch (Exception e) {
            throw new IllegalStateException("Không gửi được email OTP vui lòng kiểm tra cấu hình SMTP và thử lại", e);
        }
        Instant sent = clock.instant();
        entries.put(key, new Entry(code, sent, sent.plus(TTL)));
    }
    @Override
    public synchronized boolean verifyOtp(String key, String code) {
        Entry entry = entries.get(key);
        if (entry == null || !clock.instant().isBefore(entry.expires) || entry.attempts >= MAX_ATTEMPTS || entry.used) {
            return false;
        }
        entry.attempts++;
        if (code == null || !entry.code.equals(code.trim())) {
            return false;
        }
        entry.used = true;
        return true;
    }
    private static final class Entry {
        final String code;
        final Instant sent;
        final Instant expires;
        int attempts;
        boolean used;
        Entry(String code, Instant sent, Instant expires) {
            this.code = code;
            this.sent = sent;
            this.expires = expires;
        }
    }
}
