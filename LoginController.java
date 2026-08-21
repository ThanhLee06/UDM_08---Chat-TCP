package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private Label lblError;

    @FXML
    private void handleLogin() {
        String username = txtUsername.getText().trim();

        if (username.isEmpty()) {
            lblError.setText("Vui lòng nhập tên!");
            return;
        }

        if (!username.matches("^[A-Za-z0-9_]{3,20}$")) {
            lblError.setText("Tên 3–20 ký tự, chỉ gồm chữ, số và _");
            return;
        }

        lblError.setText("");

        ChatClient client = new ChatClient();
        client.connect(username);
    }
}