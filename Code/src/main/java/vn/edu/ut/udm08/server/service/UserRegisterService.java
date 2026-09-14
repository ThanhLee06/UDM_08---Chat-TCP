package vn.edu.ut.udm08.server.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import vn.edu.ut.udm08.server.auth.EmailOtpService;
import vn.edu.ut.udm08.server.auth.IOtpService;
import vn.edu.ut.udm08.server.auth.SmtpOtpEmailSender;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.session.UsernameValidator;
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
    private static final int MAX_PENDING_REGISTRATIONS = 1000;
    private static final Duration REGISTRATION_TTL = Duration.ofMinutes(5);
    private final IEmailValidator emailValidator;
    private final IPasswordValidator passwordValidator;
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;
    private final IOtpService otpService;
    private final Clock clock;
    private final Map<String, PendingRegistration> pending = new HashMap<>();

    public UserRegisterService(IUserRepository repository) {
        this(repository, new PasswordEncoder());
    }

    public UserRegisterService(IUserRepository repository, IPasswordEncoder encoder) {
        this(repository, encoder, new EmailOtpService(new SmtpOtpEmailSender()));
    }
    public UserRegisterService(IUserRepository repository, IPasswordEncoder encoder, IOtpService otpService) {
        this(repository, encoder, otpService, Clock.systemUTC());
    }

    public UserRegisterService(IUserRepository repository, IPasswordEncoder encoder,
                               IOtpService otpService, Clock clock) {
        this(repository, encoder, otpService, clock, new EmailValidator(), new PasswordValidator());
    }
    public UserRegisterService(IUserRepository repository, IPasswordEncoder encoder,
                               IOtpService otpService, Clock clock,
                               IEmailValidator emailValidator, IPasswordValidator passwordValidator) {
        this.emailValidator = emailValidator;
        this.passwordValidator = passwordValidator;
        this.userRepository = repository;
        this.passwordEncoder = encoder;
        this.otpService = otpService;
        this.clock = clock;
    }

    public synchronized RegisterResponse register(RegisterRequest request) {
        pending.entrySet().removeIf(e -> !clock.instant().isBefore(e.getValue().getExpiresAt()));
        if (request == null) {
            return RegisterResponse.fail("Thông tin đăng ký không hợp lệ");
        }
        if (request.getUsername() == null || !UsernameValidator.isValid(request.getUsername().trim())) {
            return RegisterResponse.fail("Tên tài khoản không hợp lệ (3-20 ký tự chữ và số)");
        }
        if (request.getPhoneNumber() == null || !request.getPhoneNumber().trim().matches("0[35789][0-9]{8}")) {
            return RegisterResponse.fail("Số điện thoại không hợp lệ");
        }
        if (!emailValidator.isValidEmail(request.getEmail())) {
            return RegisterResponse.fail("Email không hợp lệ");
        }

        String passwordError = passwordValidator.getValidationError(request.getPassword());
        if (passwordError != null) {
            return RegisterResponse.fail(passwordError);
        }

        User user = UserMapper.toEntity(request);
        String duplicate = duplicateError(user);
        if (duplicate != null) {
            return RegisterResponse.fail(duplicate);
        }
        if (pending.size() >= MAX_PENDING_REGISTRATIONS) {
            return RegisterResponse.fail("Có quá nhiều yêu cầu đăng ký vui lòng thử lại sau");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        try {
            otpService.sendOtp(key(user.getEmail()), user.getEmail());
        } catch (IllegalStateException e) {
            return RegisterResponse.fail(e.getMessage());
        }
        pending.entrySet().removeIf(e -> user.getEmail().equals(e.getValue().getUser().getEmail()));
        String id = UUID.randomUUID().toString();
        pending.put(id, new PendingRegistration(user, clock.instant().plus(REGISTRATION_TTL)));
        RegisterResponse response = RegisterResponse.ok("Đã gửi mã xác thực tới " + user.getEmail(), null);
        response.setRegistrationId(id);
        return response;
    }
    public synchronized RegisterResponse resendOtp(String registrationId) {
        PendingRegistration registration = pending.get(registrationId);
        if (registration == null || !clock.instant().isBefore(registration.getExpiresAt())) {
            pending.remove(registrationId);
            return restartRegistration("Phiên đăng ký đã hết hạn");
        }
        try {
            otpService.sendOtp(key(registration.getUser().getEmail()), registration.getUser().getEmail());
            pending.put(registrationId, new PendingRegistration(registration.getUser(), clock.instant().plus(REGISTRATION_TTL)));
            return RegisterResponse.ok("Đã gửi lại mã OTP", null);
        } catch (IllegalStateException e) {
            return RegisterResponse.fail(e.getMessage());
        }
    }
    public synchronized RegisterResponse verifyRegistration(String registrationId, String code) {
        PendingRegistration registration = pending.get(registrationId);
        if (registration == null || !clock.instant().isBefore(registration.getExpiresAt())) {
            pending.remove(registrationId);
            return restartRegistration("Phiên đăng ký đã hết hạn");
        }
        User user = registration.getUser();
        if (!otpService.verifyOtp(key(user.getEmail()), code)) {
            return RegisterResponse.fail("Mã OTP không đúng đã hết hạn hoặc vượt quá 5 lần thử");
        }
        pending.remove(registrationId);
        String duplicate = duplicateError(user);
        if (duplicate != null) {
            return restartRegistration(duplicate);
        }
        User saved = userRepository.save(user);
        if (saved == null || saved.getId() == null) {
            return restartRegistration("Không thể lưu tài khoản");
        }
        return RegisterResponse.ok("Xác thực email và đăng ký thành công", saved);
    }
    private RegisterResponse restartRegistration(String message) {
        RegisterResponse response = RegisterResponse.fail(message + ". Vui lòng quay lại đăng ký.");
        response.setRegistrationRestartRequired(true);
        return response;
    }
    private String duplicateError(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return "Tên đăng nhập đã được sử dụng";
        }
        if (userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            return "Số điện thoại đã được đăng ký";
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            return "Email đã được sử dụng";
        }
        return null;
    }
    private static String key(String email) {
        return "register:" + email.toLowerCase(Locale.ROOT);
    }
    private static final class PendingRegistration {
        private final User user;
        private final Instant expiresAt;
        private PendingRegistration(User user, Instant expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }
        private User getUser() {
            return user;
        }
        private Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
