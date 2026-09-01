package vn.edu.ut.udm08.shared.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordEncoder implements IPasswordEncoder {
    private final int logRounds;

    public PasswordEncoder() {
        this(10);
    }

    public PasswordEncoder(int logRounds) {
        this.logRounds = logRounds;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt(logRounds));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}
