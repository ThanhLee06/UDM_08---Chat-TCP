package vn.edu.ut.udm08.shared.validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class PasswordValidatorTest {
    private IPasswordValidator passwordValidator;
    @BeforeEach
    public void setUp() {
        passwordValidator = new PasswordValidator();
    }
    @Test
    public void testStrongPassword() {
        assertTrue(passwordValidator.isStrongPassword("Pass123@"));
        assertNull(passwordValidator.getValidationError("Pass123@"));
    }
    @Test
    public void testPasswordTooShort() {
        assertFalse(passwordValidator.isStrongPassword("P1@a"));
        assertEquals("Mật khẩu phải có ít nhất 8 ký tự", passwordValidator.getValidationError("P1@a"));
    }
    @Test
    public void testPasswordMissingUppercase() {
        assertFalse(passwordValidator.isStrongPassword("pass123@"));
        assertEquals("Mật khẩu phải chứa ít nhất 1 chữ cái viết hoa", passwordValidator.getValidationError("pass123@"));
    }
    @Test
    public void testPasswordMissingLowercase() {
        assertFalse(passwordValidator.isStrongPassword("PASS123@"));
        assertEquals("Mật khẩu phải chứa ít nhất 1 chữ cái viết thường", passwordValidator.getValidationError("PASS123@"));
    }
    @Test
    public void testPasswordMissingDigit() {
        assertFalse(passwordValidator.isStrongPassword("Password@"));
        assertEquals("Mật khẩu phải chứa ít nhất 1 chữ số", passwordValidator.getValidationError("Password@"));
    }
    @Test
    public void testPasswordMissingSpecialChar() {
        assertFalse(passwordValidator.isStrongPassword("Password123"));
        assertEquals("Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt", passwordValidator.getValidationError("Password123"));
    }
}
