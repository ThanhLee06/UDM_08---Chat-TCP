package vn.edu.ut.udm08.shared.validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class EmailValidatorTest {
    private IEmailValidator emailValidator;
    @BeforeEach
    public void setUp() {
        emailValidator = new EmailValidator();
    }
    @Test
    public void testValidEmails() {
        assertTrue(emailValidator.isValidEmail("user@gmail.com"));
        assertTrue(emailValidator.isValidEmail("user.name+tag@domain.co.uk"));
        assertTrue(emailValidator.isValidEmail("user_123@sub.domain.org"));
    }
    @Test
    public void testInvalidEmails() {
        assertFalse(emailValidator.isValidEmail(null));
        assertFalse(emailValidator.isValidEmail(""));
        assertFalse(emailValidator.isValidEmail("   "));
        assertFalse(emailValidator.isValidEmail("usergmail.com"));
        assertFalse(emailValidator.isValidEmail("user@"));
        assertFalse(emailValidator.isValidEmail("@domain.com"));
        assertFalse(emailValidator.isValidEmail("user@domain"));
    }
}
