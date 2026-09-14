package vn.edu.ut.udm08.shared.validation;
import java.util.Properties;
public class ValidationConfig {
    private final int minPasswordLength;
    private final boolean requireUppercase;
    private final boolean requireLowercase;
    private final boolean requireDigit;
    private final boolean requireSpecialChar;
    private final String emailRegex;
    public ValidationConfig() {
        this(8, true, true, true, true, "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    public ValidationConfig(int minPasswordLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigit, boolean requireSpecialChar, String emailRegex) {
        this.minPasswordLength = minPasswordLength;
        this.requireUppercase = requireUppercase;
        this.requireLowercase = requireLowercase;
        this.requireDigit = requireDigit;
        this.requireSpecialChar = requireSpecialChar;
        this.emailRegex = emailRegex;
    }
    public static ValidationConfig defaultConfig() {
        return new ValidationConfig();
    }
    public static ValidationConfig fromProperties(Properties properties) {
        if (properties == null) {
            return defaultConfig();
        }
        int minLength = parseInteger(properties.getProperty("validation.password.min_length"), 8);
        boolean uppercase = parseBoolean(properties.getProperty("validation.password.require_uppercase"), true);
        boolean lowercase = parseBoolean(properties.getProperty("validation.password.require_lowercase"), true);
        boolean digit = parseBoolean(properties.getProperty("validation.password.require_digit"), true);
        boolean special = parseBoolean(properties.getProperty("validation.password.require_special"), true);
        String regex = properties.getProperty("validation.email.regex", "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        return new ValidationConfig(minLength, uppercase, lowercase, digit, special, regex);
    }
    private static int parseInteger(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }
    public int getMinPasswordLength() {
        return minPasswordLength;
    }
    public boolean isRequireUppercase() {
        return requireUppercase;
    }
    public boolean isRequireLowercase() {
        return requireLowercase;
    }
    public boolean isRequireDigit() {
        return requireDigit;
    }
    public boolean isRequireSpecialChar() {
        return requireSpecialChar;
    }
    public String getEmailRegex() {
        return emailRegex;
    }
}
