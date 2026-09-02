package vn.edu.ut.udm08.server.service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.shared.dto.LoginRequest;
import vn.edu.ut.udm08.shared.dto.LoginResponse;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.security.IPasswordEncoder;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
public class UserLoginServiceTest {
    private UserLoginService loginService;
    private IPasswordEncoder passwordEncoder;
    private TestUserRepository testRepo;
    @BeforeEach
    public void setUp() {
        passwordEncoder = new PasswordEncoder();
        testRepo = new TestUserRepository();
        loginService = new UserLoginService(testRepo, passwordEncoder);
        User user = new User();
        user.setUsername("ThanhUser");
        user.setPhoneNumber("0901234567");
        user.setEmail("thanh@gmail.com");
        user.setPasswordHash(passwordEncoder.encode("pass123"));
        testRepo.save(user);
    }
    @Test
    public void testLoginSuccess() {
        LoginRequest request = new LoginRequest("0901234567", "pass123");
        LoginResponse response = loginService.login(request);
        assertTrue(response.isSuccess());
        assertEquals("Đăng nhập thành công", response.getMessage());
        assertNotNull(response.getUser());
        assertEquals("ThanhUser", response.getUser().getUsername());
    }
    @Test
    public void testLoginWrongPassword() {
        LoginRequest request = new LoginRequest("0901234567", "wrongpass");
        LoginResponse response = loginService.login(request);
        assertFalse(response.isSuccess());
        assertEquals("Mật khẩu không chính xác", response.getMessage());
    }
    @Test
    public void testLoginAccountNotFound() {
        LoginRequest request = new LoginRequest("0999999999", "pass123");
        LoginResponse response = loginService.login(request);
        assertFalse(response.isSuccess());
        assertEquals("Tài khoản không tồn tại", response.getMessage());
    }
    @Test
    public void testLoginNullRequest() {
        LoginResponse response = loginService.login(null);
        assertFalse(response.isSuccess());
        assertEquals("Thông tin đăng nhập không hợp lệ", response.getMessage());
    }
    @Test
    public void testLoginEmptyPhoneNumber() {
        LoginRequest request = new LoginRequest("", "pass123");
        LoginResponse response = loginService.login(request);
        assertFalse(response.isSuccess());
        assertEquals("Vui lòng nhập số điện thoại và mật khẩu", response.getMessage());
    }
    @Test
    public void testLoginEmptyPassword() {
        LoginRequest request = new LoginRequest("0901234567", "");
        LoginResponse response = loginService.login(request);
        assertFalse(response.isSuccess());
        assertEquals("Vui lòng nhập số điện thoại và mật khẩu", response.getMessage());
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
