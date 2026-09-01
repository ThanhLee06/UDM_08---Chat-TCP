package vn.edu.ut.udm08.shared.security;

public interface IPasswordEncoder {
    String encode(CharSequence rawPassword);
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
