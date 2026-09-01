package vn.edu.ut.udm08.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordEncoderTest {
    private IPasswordEncoder encoder;

    @BeforeEach
    public void setUp() {
        encoder = new PasswordEncoder();
    }

    @Test
    public void testEncodePasswordNotPlainText() {
        String rawPassword = "mySecretPassword123";
        String encoded = encoder.encode(rawPassword);
        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded);
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$"));
    }

    @Test
    public void testMatchesCorrectPassword() {
        String rawPassword = "mySecretPassword123";
        String encoded = encoder.encode(rawPassword);
        assertTrue(encoder.matches(rawPassword, encoded));
    }

    @Test
    public void testMatchesWrongPassword() {
        String rawPassword = "mySecretPassword123";
        String encoded = encoder.encode(rawPassword);
        assertFalse(encoder.matches("wrongPassword", encoded));
    }

    @Test
    public void testNullHandling() {
        assertNull(encoder.encode(null));
        assertFalse(encoder.matches(null, "hash"));
        assertFalse(encoder.matches("pass", null));
    }
}
