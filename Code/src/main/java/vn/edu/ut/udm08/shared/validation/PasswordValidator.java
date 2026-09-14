package vn.edu.ut.udm08.shared.validation;
import java.util.regex.Pattern;
public class PasswordValidator implements IPasswordValidator {
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    private final ValidationConfig config;
    public PasswordValidator() {
        this(ValidationConfig.defaultConfig());
    }
    public PasswordValidator(ValidationConfig config) {
        this.config = config != null ? config : ValidationConfig.defaultConfig();
    }
    @Override
    public boolean isStrongPassword(String password) {
        return getValidationError(password) == null;
    }
    @Override
    public String getValidationError(String password) {
        if (password == null || password.trim().isEmpty()) {
            return "Vui lòng nhập mật khẩu";
        }
        if (password.length() < config.getMinPasswordLength()) {
            return "Mật khẩu phải có ít nhất " + config.getMinPasswordLength() + " ký tự";
        }
        if (config.isRequireUppercase() && !UPPERCASE_PATTERN.matcher(password).matches()) {
            return "Mật khẩu phải chứa ít nhất 1 chữ cái viết hoa";
        }
        if (config.isRequireLowercase() && !LOWERCASE_PATTERN.matcher(password).matches()) {
            return "Mật khẩu phải chứa ít nhất 1 chữ cái viết thường";
        }
        if (config.isRequireDigit() && !DIGIT_PATTERN.matcher(password).matches()) {
            return "Mật khẩu phải chứa ít nhất 1 chữ số";
        }
        if (config.isRequireSpecialChar() && !SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            return "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt";
        }
        return null;
    }
}
