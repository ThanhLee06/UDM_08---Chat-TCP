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
        RegisterRequest request = new RegisterRequest("ThanhUser", "0901234567", "thanh@gmail.com", "Pass123@", "PRESET", "01.png");
        RegisterResponse response = service.register(request);
        assertTrue(response.isSuccess());
        assertNotNull(response.getUser());
        assertEquals("ThanhUser", response.getUser().getUsername());
        assertNotEquals("Pass123@", response.getUser().getPasswordHash());
        assertTrue(passwordEncoder.matches("Pass123@", response.getUser().getPasswordHash()));
    }
    @Test
    public void testRegisterRejectsInvalidEmail() {
        RegisterRequest request = new RegisterRequest("ThanhUser", "0901234567", "invalid-email-format", "Pass123@", "PRESET", "01.png");
        RegisterResponse response = service.register(request);
        assertFalse(response.isSuccess());
        assertEquals("Email không hợp lệ", response.getMessage());
    }
    @Test
    public void testRegisterRejectsWeakPasswordShort() {
        RegisterRequest request = new RegisterRequest("ThanhUser", "0901234567", "thanh@gmail.com", "P1@", "PRESET", "01.png");
        RegisterResponse response = service.register(request);
        assertFalse(response.isSuccess());
        assertEquals("Mật khẩu phải có ít nhất 8 ký tự", response.getMessage());
    }
    @Test
    public void testRegisterRejectsWeakPasswordNoSpecialChar() {
        RegisterRequest request = new RegisterRequest("ThanhUser", "0901234567", "thanh@gmail.com", "Password123", "PRESET", "01.png");
        RegisterResponse response = service.register(request);
        assertFalse(response.isSuccess());
        assertEquals("Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt", response.getMessage());
    }
    @Test
    public void testRegisterRejectsDuplicateUsername() {
        RegisterRequest request1 = new RegisterRequest("ThanhUser", "0901234567", "thanh1@gmail.com", "Pass123@", "PRESET", "01.png");
        service.register(request1);
        RegisterRequest request2 = new RegisterRequest("thanhuser", "0909999999", "thanh2@gmail.com", "Pass123@", "PRESET", "01.png");
        RegisterResponse response = service.register(request2);
        assertFalse(response.isSuccess());
        assertEquals("Tên đăng nhập đã được sử dụng", response.getMessage());
    }
    @Test
    public void testRegisterRejectsDuplicatePhone() {
        RegisterRequest request1 = new RegisterRequest("User1", "0901234567", "thanh1@gmail.com", "Pass123@", "PRESET", "01.png");
        service.register(request1);
        RegisterRequest request2 = new RegisterRequest("User2", "0901234567", "thanh2@gmail.com", "Pass123@", "PRESET", "01.png");
        RegisterResponse response = service.register(request2);
        assertFalse(response.isSuccess());
        assertEquals("Số điện thoại đã được đăng ký", response.getMessage());
    }
    @Test
    public void testRegisterRejectsDuplicateEmail() {
        RegisterRequest request1 = new RegisterRequest("User1", "0901234567", "thanh@gmail.com", "Pass123@", "PRESET", "01.png");
        service.register(request1);
        RegisterRequest request2 = new RegisterRequest("User2", "0909999999", "THANH@gmail.com", "Pass123@", "PRESET", "01.png");
        RegisterResponse response = service.register(request2);
        assertFalse(response.isSuccess());
        assertEquals("Email đã được sử dụng", response.getMessage());
    }
    private static class TestUserRepository implements IUserRepository {
        private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
        private final Map<String, User> usersByPhone = new ConcurrentHashMap<>();
        private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
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
        public boolean existsByEmail(String email) {
            return email != null && usersByEmail.containsKey(email.trim().toLowerCase());
        }
        @Override
        public User save(User user) {
            if (user == null) return null;
            user.setId(idGenerator.getAndIncrement());
            if (user.getUsername() != null) usersByUsername.put(user.getUsername().trim().toLowerCase(), user);
            if (user.getPhoneNumber() != null) usersByPhone.put(user.getPhoneNumber().trim(), user);
            if (user.getEmail() != null) usersByEmail.put(user.getEmail().trim().toLowerCase(), user);
            return user;
        }
        @Override
        public Optional<User> findByPhoneNumber(String phoneNumber) {
            if (phoneNumber == null) return Optional.empty();
            return Optional.ofNullable(usersByPhone.get(phoneNumber.trim()));
        }
    }
}
