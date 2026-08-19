package vn.edu.ut.udm08.client.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LoginFormValidatorTest {
    private LoginFormValidator validator = new LoginFormValidator();

    @Test
    void acceptsValidLoginForm() {
        assertNull(validator.validate("Thành01", "127.0.0.1", "8080"));
    }

    @Test
    void rejectsInvalidUsername() {
        assertNotNull(validator.validate("user 1", "127.0.0.1", "8080"));
    }

    @Test
    void rejectsBlankHost() {
        assertNotNull(validator.validate("user1", "", "8080"));
    }

    @Test
    void rejectsNonNumericPort() {
        assertNotNull(validator.validate("user1", "127.0.0.1", "abc"));
    }

    @Test
    void rejectsPortOutsideValidRange() {
        assertNotNull(validator.validate("user1", "127.0.0.1", "65536"));
    }
}
