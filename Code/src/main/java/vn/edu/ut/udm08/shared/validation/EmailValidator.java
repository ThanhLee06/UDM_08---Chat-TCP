package vn.edu.ut.udm08.shared.validation;
import java.util.regex.Pattern;
public class EmailValidator implements IEmailValidator {
    private final Pattern pattern;
    public EmailValidator() {
        this(ValidationConfig.defaultConfig());
    }
    public EmailValidator(ValidationConfig config) {
        String regex = (config != null && config.getEmailRegex() != null) ? config.getEmailRegex() : "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        this.pattern = Pattern.compile(regex);
    }
    @Override
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return pattern.matcher(email.trim()).matches();
    }
}
