package vn.edu.ut.udm08.client.controller;

import java.util.regex.Pattern;
import vn.edu.ut.udm08.server.session.UsernameValidator;

public class RegisterFormValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0(3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}$");

    public String validate(String username, String phoneNumber, String password, String confirmPassword) {
        if (username == null || username.trim().isEmpty()) {
            return "Vui lòng nhập tên tài khoản (Username)";
        }
        if (!UsernameValidator.isValid(username.trim())) {
            return "Tên tài khoản không hợp lệ (3-20 ký tự chữ và số)";
        }

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "Vui lòng nhập số điện thoại";
        }
        String cleanPhone = phoneNumber.trim();
        if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
            return "Số điện thoại không hợp lệ (Phải đúng 10 số đầu 03/05/07/08/09)";
        }
        if (isDummyPhoneNumber(cleanPhone)) {
            return "Số điện thoại bịp/ảo không hợp lệ (Vui lòng nhập SĐT thật)";
        }

        if (password == null || password.trim().isEmpty()) {
            return "Vui lòng nhập mật khẩu";
        }
        if (password.length() < 8) {
            return "Mật khẩu phải có ít nhất 8 ký tự";
        }
        if (!containsUpperLowerDigit(password)) {
            return "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số";
        }

        if (confirmPassword == null || !confirmPassword.equals(password)) {
            return "Mật khẩu xác nhận không trùng khớp";
        }

        return null;
    }

    private boolean isDummyPhoneNumber(String phone) {
        if (phone == null || phone.length() != 10) {
            return false;
        }
        char first = phone.charAt(1);
        boolean allSame = true;
        for (int i = 2; i < phone.length(); i++) {
            if (phone.charAt(i) != first) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            return true;
        }
        return phone.equals("0123456789") || phone.equals("0987654321");
    }

    private boolean containsUpperLowerDigit(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasUpper && hasLower && hasDigit;
    }
}
