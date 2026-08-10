package vn.edu.ut.udm08.server.session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class UsernameValidatorTest {
    @Test
    void acceptsValidUsernames() {
        assertTrue(UsernameValidator.isValid("ThanhLee06"));
        assertTrue(UsernameValidator.isValid("THành123"));
        assertTrue(UsernameValidator.isValid("a".repeat(20)));
    }
    @Test
    void rejectsInvalidUsernames() {
        assertFalse(UsernameValidator.isValid(null));
        assertFalse(UsernameValidator.isValid(""));
        assertFalse(UsernameValidator.isValid("Thanh Lee"));
        assertFalse(UsernameValidator.isValid("thanh@06"));
        assertFalse(UsernameValidator.isValid("a".repeat(21)));
    }
}
