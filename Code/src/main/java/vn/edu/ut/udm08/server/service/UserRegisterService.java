package vn.edu.ut.udm08.server.service;

import vn.edu.ut.udm08.server.auth.PhoneOtpService;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.shared.dto.RegisterRequest;
import vn.edu.ut.udm08.shared.dto.RegisterResponse;
import vn.edu.ut.udm08.shared.mapper.UserMapper;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.security.IPasswordEncoder;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;

public class UserRegisterService {
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;
    private final PhoneOtpService phoneOtpService;

    public UserRegisterService(IUserRepository userRepository) {
        this(userRepository, new PasswordEncoder(), new PhoneOtpService());
    }

    public UserRegisterService(IUserRepository userRepository, IPasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, new PhoneOtpService());
    }

    public UserRegisterService(IUserRepository userRepository, IPasswordEncoder passwordEncoder, PhoneOtpService phoneOtpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneOtpService = phoneOtpService;
    }

    public PhoneOtpService getPhoneOtpService() {
        return phoneOtpService;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            return RegisterResponse.fail("Thông tin đăng ký không hợp lệ");
        }
        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty() ||
            request.getOtpCode() == null || request.getOtpCode().trim().isEmpty()) {
            return RegisterResponse.fail("Vui lòng nhập đầy đủ thông tin và mã OTP");
        }

        if (!phoneOtpService.verifyOtp(request.getPhoneNumber(), request.getOtpCode())) {
            return RegisterResponse.fail("Mã OTP số điện thoại không chính xác hoặc đã hết hạn");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return RegisterResponse.fail("Tên đăng nhập đã được sử dụng");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            return RegisterResponse.fail("Số điện thoại đã được đăng ký");
        }

        User newUser = UserMapper.toEntity(request);
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        newUser.setPasswordHash(hashedPassword);

        User savedUser = userRepository.save(newUser);
        if (savedUser == null || savedUser.getId() == null) {
            return RegisterResponse.fail("Không thể lưu tài khoản vào cơ sở dữ liệu");
        }
        return RegisterResponse.ok("Đăng ký tài khoản thành công", savedUser);
    }
}
