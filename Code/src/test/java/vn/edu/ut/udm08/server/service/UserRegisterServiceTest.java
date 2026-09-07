package vn.edu.ut.udm08.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.shared.dto.RegisterRequest;
import vn.edu.ut.udm08.shared.dto.RegisterResponse;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.security.IPasswordEncoder;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

public class UserRegisterServiceTest {
    private UserRegisterService service;
    private IPasswordEncoder passwordEncoder;

    @BeforeEach
    public void setUp() {
        passwordEncoder = new PasswordEncoder();
        service = new UserRegisterService(new TestUserRepository(), passwordEncoder);
    }

    @Test
    public void testRegisterSuccessHashesPassword() {
        service.getPhoneOtpService().sendOtp("0901234567");
        String otp = service.getPhoneOtpService().getLatestOtpForTesting("0901234567");
        RegisterRequest request = new RegisterRequest("ThanhUser", "0901234567", "pass123", otp, "PRESET", "01.png");
        RegisterResponse response = service.register(request);
        assertTrue(response.isSuccess());
        assertNotNull(response.getUser());
        assertEquals("ThanhUser", response.getUser().getUsername());
        assertNotEquals("pass123", response.getUser().getPasswordHash());
        assertTrue(passwordEncoder.matches("pass123", response.getUser().getPasswordHash()));
    }

    @Test
    public void testRegisterRejectsDuplicateUsername() {
        service.getPhoneOtpService().sendOtp("0901234567");
        String otp1 = service.getPhoneOtpService().getLatestOtpForTesting("0901234567");
        RegisterRequest request1 = new RegisterRequest("ThanhUser", "0901234567", "pass123", otp1, "PRESET", "01.png");
        service.register(request1);

        service.getPhoneOtpService().sendOtp("0909999999");
        String otp2 = service.getPhoneOtpService().getLatestOtpForTesting("0909999999");
        RegisterRequest request2 = new RegisterRequest("thanhuser", "0909999999", "pass123", otp2, "PRESET", "01.png");
        RegisterResponse response = service.register(request2);
        assertFalse(response.isSuccess());
        assertEquals("Tên đăng nhập đã được sử dụng", response.getMessage());
    }

    @Test
    public void testRegisterRejectsDuplicatePhone() {
        service.getPhoneOtpService().sendOtp("0901234567");
        String otp1 = service.getPhoneOtpService().getLatestOtpForTesting("0901234567");
        RegisterRequest request1 = new RegisterRequest("User1", "0901234567", "pass123", otp1, "PRESET", "01.png");
        service.register(request1);

        service.getPhoneOtpService().sendOtp("0901234567");
        String otp2 = service.getPhoneOtpService().getLatestOtpForTesting("0901234567");
        RegisterRequest request2 = new RegisterRequest("User2", "0901234567", "pass123", otp2, "PRESET", "01.png");
        RegisterResponse response = service.register(request2);
        assertFalse(response.isSuccess());
        assertEquals("Số điện thoại đã được đăng ký", response.getMessage());
    }

    private static class TestUserRepository implements IUserRepository {
        private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
        private final Map<String, User> usersByPhone = new ConcurrentHashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public boolean existsByUsername(String username) {
            return username != null && usersByUsername.containsKey(username.trim().toLowerCase());
        }

        @Override
        public boolean existsByPhoneNumber(String phoneNumber) {
            return phoneNumber != null && usersByPhone.containsKey(phoneNumber.trim());
        }

        @Override
        public User save(User user) {
            if (user == null) return null;
            user.setId(idGenerator.getAndIncrement());
            if (user.getUsername() != null) usersByUsername.put(user.getUsername().trim().toLowerCase(), user);
            if (user.getPhoneNumber() != null) usersByPhone.put(user.getPhoneNumber().trim(), user);
            return user;
        }

        @Override
        public Optional<User> findByPhoneNumber(String phoneNumber) {
            if (phoneNumber == null) return Optional.empty();
            return Optional.ofNullable(usersByPhone.get(phoneNumber.trim()));
        }

        @Override
        public boolean updatePassword(String phoneNumber, String newPasswordHash) {
            Optional<User> userOpt = findByPhoneNumber(phoneNumber);
            if (userOpt.isPresent()) {
                userOpt.get().setPasswordHash(newPasswordHash);
                return true;
            }
            return false;
        }
    }
}
