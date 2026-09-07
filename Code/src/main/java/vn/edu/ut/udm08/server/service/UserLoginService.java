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

public class UserLoginService {
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;

    public UserLoginService(IUserRepository userRepository) {
        this(userRepository, new PasswordEncoder());
    }

    public UserLoginService(IUserRepository userRepository, IPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            return LoginResponse.fail("Thông tin đăng nhập không hợp lệ");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty() || request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return LoginResponse.fail("Vui lòng nhập số điện thoại và mật khẩu");
        }
        Optional<User> optionalUser = userRepository.findByPhoneNumber(request.getPhoneNumber());
        if (optionalUser.isEmpty()) {
            optionalUser = userRepository.findAll().stream()
                    .filter(u -> u != null && u.getUsername() != null && request.getPhoneNumber().equalsIgnoreCase(u.getUsername()))
                    .findFirst();
        }
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

    public boolean resetPassword(String phoneNumber, String otpCode, String newPassword, vn.edu.ut.udm08.server.auth.PhoneOtpService otpService) {
        if (phoneNumber == null || otpCode == null || newPassword == null || otpService == null) {
            return false;
        }
        if (!otpService.verifyOtp(phoneNumber, otpCode)) {
            return false;
        }
        if (!userRepository.existsByPhoneNumber(phoneNumber)) {
            return false;
        }
        String newHash = passwordEncoder.encode(newPassword);
        return userRepository.updatePassword(phoneNumber, newHash);
    }
}
