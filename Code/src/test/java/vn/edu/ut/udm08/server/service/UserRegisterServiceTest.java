package vn.edu.ut.udm08.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import vn.edu.ut.udm08.server.auth.EmailOtpService;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.shared.dto.*;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
class UserRegisterServiceTest {
    @TempDir Path temp;
    UserRepository repository;
    UserRegisterService service;
    Map<String, String> mailbox;
    TestClock clock;

    @BeforeEach void setup() {
        repository = vn.edu.ut.udm08.support.TestDatabase.repository("jdbc:sqlite:" + temp.resolve("accounts.db"));
        mailbox = new HashMap<>();
        clock = new TestClock();
        service = new UserRegisterService(repository, new PasswordEncoder(),
                new EmailOtpService(mailbox::put, clock), clock);
    }
    RegisterRequest request() {
        return new RegisterRequest("ThanhUser", "0901234567", "thanh@gmail.com", "Pass123@", "PRESET", "01.png");
    }
    RegisterResponse confirm(RegisterResponse started) {
        return service.verifyRegistration(started.getRegistrationId(), mailbox.get("thanh@gmail.com"));
    }
    @Test void accountIsInsertedOnlyAfterValidEmailOtp() {
        RegisterResponse started = service.register(request());
        assertTrue(started.isSuccess());
        assertNull(started.getUser());
        assertNotNull(started.getRegistrationId());
        assertTrue(repository.findAll().isEmpty());
        assertTrue(mailbox.get("thanh@gmail.com").matches("[0-9]{6}"));
        assertFalse(new UserLoginService(repository).login(new LoginRequest("0901234567", "Pass123@")).isSuccess());
        RegisterResponse verified = confirm(started);
        assertTrue(verified.isSuccess(), verified.getMessage());
        assertEquals(1, repository.findAll().size());
        assertEquals("thanh@gmail.com", verified.getUser().getEmail());
        assertNotEquals("Pass123@", verified.getUser().getPasswordHash());
        assertTrue(new PasswordEncoder().matches("Pass123@", verified.getUser().getPasswordHash()));
        assertFalse(confirm(started).isSuccess());
        assertEquals(1, repository.findAll().size());
    }

    @Test void wrongOrMissingCodeNeverCreatesAccount() {
        var started = service.register(request());
        assertFalse(service.verifyRegistration(started.getRegistrationId(), "wrong").isSuccess());
        assertFalse(service.verifyRegistration(started.getRegistrationId(), null).isSuccess());
        assertTrue(repository.findAll().isEmpty());
    }
    @Test void expiredRegistrationCannotCreateAccount() {
        var started = service.register(request());
        clock.advance(301);
        assertFalse(confirm(started).isSuccess());
        assertTrue(repository.findAll().isEmpty());
    }
    @Test void tooManyWrongAttemptsRejectEvenCorrectCode() {
        var started = service.register(request());
        for (int i = 0; i < 5; i++) service.verifyRegistration(started.getRegistrationId(), "wrong");
        assertFalse(confirm(started).isSuccess());
        assertTrue(repository.findAll().isEmpty());
    }
    @Test void resendHasCooldownAndOldCodeIsInvalidated() {
        var started = service.register(request());
        String old = mailbox.get("thanh@gmail.com");
        assertFalse(service.resendOtp(started.getRegistrationId()).isSuccess());
        clock.advance(61);
        assertTrue(service.resendOtp(started.getRegistrationId()).isSuccess());
        String fresh = mailbox.get("thanh@gmail.com");
        if (!old.equals(fresh)) assertFalse(service.verifyRegistration(started.getRegistrationId(), old).isSuccess());
        assertTrue(confirm(started).isSuccess());
    }
    @Test void submittedDetailsCannotBeChangedAfterEmailWasSent() {
        var request = request();
        var started = service.register(request);
        request.setEmail("attacker@gmail.com");
        request.setPhoneNumber("0909999999");
        request.setPassword("Other123@");
        assertTrue(confirm(started).isSuccess());
        assertTrue(repository.findByEmail("attacker@gmail.com").isEmpty());
        assertTrue(repository.findByPhoneNumber("0901234567").isPresent());
        assertTrue(new UserLoginService(repository).login(new LoginRequest("thanh@gmail.com", "Pass123@")).isSuccess());
    }
    @Test void smtpFailureDoesNotCreateAccountOrChallenge() {
        service = new UserRegisterService(repository, new PasswordEncoder(), new EmailOtpService((email, code) -> {
            throw new java.io.IOException("SMTP unavailable");
        }));
        var response = service.register(request());
        assertFalse(response.isSuccess());
        assertNull(response.getRegistrationId());
        assertTrue(repository.findAll().isEmpty());
    }
    @Test void bothLoginIdentifiersWorkAfterRestart() {
        assertTrue(confirm(service.register(request())).isSuccess());
        var reopened = vn.edu.ut.udm08.support.TestDatabase.repository("jdbc:sqlite:" + temp.resolve("accounts.db"));
        var login = new UserLoginService(reopened);
        assertTrue(login.login(new LoginRequest("0901234567", "Pass123@")).isSuccess());
        assertTrue(login.login(new LoginRequest("+84901234567", "Pass123@")).isSuccess());
        assertTrue(login.login(new LoginRequest("  THANH@gmail.com  ", "Pass123@")).isSuccess());
        assertFalse(login.login(new LoginRequest("thanh@gmail.com", "wrong")).isSuccess());
    }
    @Test void rejectsDuplicateEmailPhoneAndUsername() {
        assertTrue(confirm(service.register(request())).isSuccess());
        var duplicate = request();
        assertEquals("Tên đăng nhập đã được sử dụng", service.register(duplicate).getMessage());
        duplicate.setUsername("AnotherUser");
        assertEquals("Số điện thoại đã được đăng ký", service.register(duplicate).getMessage());
        duplicate.setPhoneNumber("0909999999");
        duplicate.setEmail("THANH@gmail.com");
        assertEquals("Email đã được sử dụng", service.register(duplicate).getMessage());
    }

    @Test void checksDuplicatesAgainAtConfirmation() {
        var started = service.register(request());
        var other = request();
        other.setEmail("other@gmail.com");
        var otherStarted = service.register(other);
        assertTrue(service.verifyRegistration(otherStarted.getRegistrationId(), mailbox.get("other@gmail.com")).isSuccess());
        assertFalse(confirm(started).isSuccess());
        assertEquals(1, repository.findAll().size());
    }
    @Test void validatesEmailAndPasswordBeforeSending() {
        var request = request();
        request.setEmail("invalid");
        assertEquals("Email không hợp lệ", service.register(request).getMessage());
        request.setEmail("thanh@gmail.com"); request.setPassword("P1@");
        assertEquals("Mật khẩu phải có ít nhất 8 ký tự", service.register(request).getMessage());
        request.setPassword("Password123");
        assertEquals("Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt", service.register(request).getMessage());
        assertTrue(mailbox.isEmpty());
        assertTrue(repository.findAll().isEmpty());
        }

        @Test void invalidChallengeCannotCreateAccount() {
        assertFalse(service.verifyRegistration("unknown", "123456").isSuccess());
        assertFalse(service.verifyRegistration(null, "123456").isSuccess());
        assertFalse(service.register(null).isSuccess());
        }

        @Test void resetUsesEmailAndCodeCannotBeReused() {
        assertTrue(confirm(service.register(request())).isSuccess());
        var resetOtp = new EmailOtpService(mailbox::put, clock);
        var login = new UserLoginService(repository);
        login.requestPasswordReset("0901234567", resetOtp);
        String code = mailbox.get("thanh@gmail.com");
        assertTrue(login.resetPassword("THANH@gmail.com", code, "NewPass123@", resetOtp));
        assertFalse(login.resetPassword("0901234567", code, "Again123@", resetOtp));
        assertTrue(login.login(new LoginRequest("0901234567", "NewPass123@")).isSuccess());
        assertFalse(login.login(new LoginRequest("thanh@gmail.com", "Pass123@")).isSuccess());
        }
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        public Clock withZone(ZoneId zone) {
            return this;
        }
        public Instant instant() {
            return now;
        }
    }
}
