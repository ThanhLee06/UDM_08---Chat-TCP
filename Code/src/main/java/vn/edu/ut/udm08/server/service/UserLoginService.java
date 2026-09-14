package vn.edu.ut.udm08.server.service;

import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.shared.dto.LoginRequest;
import vn.edu.ut.udm08.shared.dto.LoginResponse;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.security.IPasswordEncoder;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import java.util.Optional;
import vn.edu.ut.udm08.server.auth.IOtpService;
import vn.edu.ut.udm08.shared.validation.IPasswordValidator;
import vn.edu.ut.udm08.shared.validation.PasswordValidator;
public class UserLoginService {
    private final IPasswordValidator passwordValidator;
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;

    public UserLoginService(IUserRepository userRepository) {
        this(userRepository, new PasswordEncoder());
    }

    public UserLoginService(IUserRepository userRepository, IPasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, new PasswordValidator());
    }
    public UserLoginService(IUserRepository userRepository, IPasswordEncoder passwordEncoder,
                            IPasswordValidator passwordValidator) {
        this.passwordValidator = passwordValidator;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            return LoginResponse.fail("Thông tin đăng nhập không hợp lệ");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty() || request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return LoginResponse.fail("Vui lòng nhập email hoặc số điện thoại và mật khẩu");
        }
        String identifier = request.getPhoneNumber().trim();
        Optional<User> optionalUser = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByPhoneNumber(identifier);
        if (optionalUser.isEmpty()) {
            return LoginResponse.fail("Tài khoản không tồn tại");
        }
        User user = optionalUser.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return LoginResponse.fail("Mật khẩu không chính xác");
        }
        return LoginResponse.ok("Đăng nhập thành công", user);
    }

    public LoginResponse login(ClientSession session, LoginRequest request, LoginHandler loginHandler) {
        LoginResponse response = login(request);
        if (response.isSuccess() && session != null && loginHandler != null) {
            boolean promoted = loginHandler.handleLoginSuccess(session, response.getUser());
            if (!promoted) {
                return LoginResponse.fail("Tài khoản đã đăng nhập ở thiết bị khác");
            }
        }
        return response;
    }

    public void requestPasswordReset(String identifier, IOtpService otpService) {
        User user = findAccount(identifier).orElseThrow(() -> new IllegalStateException("Tài khoản không tồn tại"));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Tài khoản cũ chưa có email xác thực không thể đặt lại qua email");
        }
        otpService.sendOtp("reset:" + user.getEmail(), user.getEmail());
    }
    public boolean resetPassword(String identifier, String otpCode, String newPassword,
                                 IOtpService otpService) {
        if (identifier == null || otpCode == null || otpService == null || passwordValidator.getValidationError(newPassword) != null) {
            return false;
        }
        Optional<User> account = findAccount(identifier);
        if (account.isEmpty() || account.get().getEmail() == null) {
            return false;
        }
        User user = account.get();
        if (!otpService.verifyOtp("reset:" + user.getEmail(), otpCode)) {
            return false;
        }
        return userRepository.updatePassword(user.getPhoneNumber(), passwordEncoder.encode(newPassword));
    }
    private Optional<User> findAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return identifier.contains("@") ? userRepository.findByEmail(identifier.trim())
                : userRepository.findByPhoneNumber(identifier.trim());
    }
}
