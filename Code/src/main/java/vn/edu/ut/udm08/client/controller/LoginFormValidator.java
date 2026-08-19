package vn.edu.ut.udm08.client.controller;

public class LoginFormValidator {
    public String validate(String username, String host, String portText) {
        if (!isValidUsername(username)) {
            return "Username phải có 1-20 chữ cái hoặc chữ số!";
        }
        if (host == null || host.isBlank()) {
            return "Vui lòng nhập địa chỉ Server!";
        }
        if (portText == null || portText.isBlank()) {
            return "Vui lòng nhập Port!";
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            return "Port phải là số hợp lệ!";
        }

        if (port < 1 || port > 65535) {
            return "Port phải từ 1 đến 65535!";
        }
        return null;
    }

    private boolean isValidUsername(String username) {
        if (username == null || username.isEmpty() || username.length() > 20) {
            return false;
        }

        for (int i = 0; i < username.length(); i++) {
            if (!Character.isLetterOrDigit(username.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
