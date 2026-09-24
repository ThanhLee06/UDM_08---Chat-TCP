package vn.edu.ut.udm08.server.validation;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.core.ServerConfig;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class InputValidatorTest {
    @Test
    void shouldValidateMessageContentLength() {
        InputValidator validator = new InputValidator();
        assertTrue(validator.validateMessageContent("Xin chào"));
        assertFalse(validator.validateMessageContent(null));
        assertFalse(validator.validateMessageContent("   "));
        String overlong = "a".repeat(5001);
        assertFalse(validator.validateMessageContent(overlong));
    }
    @Test
    void shouldNormalizeHistoryLimit() {
        InputValidator validator = new InputValidator();
        assertEquals(30, validator.normalizeHistoryLimit(0));
        assertEquals(30, validator.normalizeHistoryLimit(-5));
        assertEquals(50, validator.normalizeHistoryLimit(50));
        assertEquals(100, validator.normalizeHistoryLimit(200));
    }
    @Test
    void shouldValidateSearchKeywordLength() {
        InputValidator validator = new InputValidator();
        assertTrue(validator.validateSearchKeyword("hieu"));
        assertFalse(validator.validateSearchKeyword(null));
        assertFalse(validator.validateSearchKeyword(""));
        String overlongSearch = "s".repeat(101);
        assertFalse(validator.validateSearchKeyword(overlongSearch));
    }
    @Test
    void shouldCustomConfigValuesInInputValidator() {
        Properties props = new Properties();
        props.setProperty("server.port", "8080");
        props.setProperty("message.maxLength", "10");
        props.setProperty("history.maxLimit", "50");
        props.setProperty("search.maxLength", "5");
        ServerConfig customConfig = ServerConfig.fromProperties(props);
        InputValidator validator = new InputValidator(customConfig);
        assertFalse(validator.validateMessageContent("12345678901"));
        assertEquals(50, validator.normalizeHistoryLimit(100));
        assertFalse(validator.validateSearchKeyword("123456"));
    }
}
