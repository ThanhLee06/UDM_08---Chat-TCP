package vn.edu.ut.udm08.server.service;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.shared.dto.RegisterRequest;
import vn.edu.ut.udm08.shared.dto.RegisterResponse;
import vn.edu.ut.udm08.shared.mapper.UserMapper;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.security.IPasswordEncoder;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import vn.edu.ut.udm08.shared.validation.EmailValidator;
import vn.edu.ut.udm08.shared.validation.IEmailValidator;
import vn.edu.ut.udm08.shared.validation.IPasswordValidator;
import vn.edu.ut.udm08.shared.validation.PasswordValidator;
public class UserRegisterService {
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;
    private final IEmailValidator emailValidator;
    private final IPasswordValidator passwordValidator;
    public UserRegisterService(IUserRepository userRepository) {
        this(userRepository, new PasswordEncoder());
    }
    public UserRegisterService(IUserRepository userRepository, IPasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, new EmailValidator(), new PasswordValidator());
    }
    public UserRegisterService(IUserRepository userRepository, IPasswordEncoder passwordEncoder, IEmailValidator emailValidator, IPasswordValidator passwordValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailValidator = emailValidator;
        this.passwordValidator = passwordValidator;
    }
    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            return RegisterResponse.fail("Thông tin đăng ký không hợp lệ");
        }
        if (request.getUsername() == null || request.getUsername().trim().isEmpty() || request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty() || request.getEmail() == null || request.getEmail().trim().isEmpty() || request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return RegisterResponse.fail("Vui lòng nhập đầy đủ thông tin");
        }
        if (!emailValidator.isValidEmail(request.getEmail())) {
            return RegisterResponse.fail("Email không hợp lệ");
        }
        String passwordError = passwordValidator.getValidationError(request.getPassword());
        if (passwordError != null) {
            return RegisterResponse.fail(passwordError);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            return RegisterResponse.fail("Tên đăng nhập đã được sử dụng");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            return RegisterResponse.fail("Số điện thoại đã được đăng ký");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return RegisterResponse.fail("Email đã được sử dụng");
        }
        User newUser = UserMapper.toEntity(request);
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        newUser.setPasswordHash(hashedPassword);
        User savedUser = userRepository.save(newUser);
        return RegisterResponse.ok("Đăng ký tài khoản thành công", savedUser);
    }
}
