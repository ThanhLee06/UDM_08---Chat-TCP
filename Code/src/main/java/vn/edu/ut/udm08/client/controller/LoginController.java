package vn.edu.ut.udm08.client.controller;

import java.net.URL;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class LoginController {
    @FXML
    private ImageView avatarImage;

    @FXML
    private ComboBox<String> avatarChoiceBox;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private Button connectButton;

    @FXML
    private Label statusLabel;

    private LoginFormValidator validator = new LoginFormValidator();

    @FXML
    private void initialize() {
        avatarChoiceBox.getItems().add("avatar1");
        avatarChoiceBox.getItems().add("avatar2");
        avatarChoiceBox.getItems().add("avatar3");
        avatarChoiceBox.setValue("avatar1");
        updateAvatarImage();
    }

    @FXML
    private void onAvatarChanged() {
        updateAvatarImage();
    }

    @FXML
    private void onConnectClick() {
        String username = usernameField.getText().trim();
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();

        String errorMessage = validator.validate(username, host, portText);
        if (errorMessage != null) {
            showError(errorMessage);
            return;
        }

        String avatarId = avatarChoiceBox.getValue();
        int port = Integer.parseInt(portText);

        connectButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #166534;");
        statusLabel.setText("Thông tin hợp lệ. Sẵn sàng kết nối!");

        // TODO: Gọi ChatClient.connect(host, port, username, avatarId, listener)
        // sau khi nhánh network client được ghép vào project.

        connectButton.setDisable(false);
    }

    private void updateAvatarImage() {
        String avatarId = avatarChoiceBox.getValue();
        if (avatarId == null) {
            return;
        }

        URL imageUrl = LoginController.class.getResource(
                "/images/" + avatarId + ".jpg");
        if (imageUrl != null) {
            avatarImage.setImage(new Image(imageUrl.toExternalForm()));
        }
    }

    private void showError(String message) {
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText(message);
    }
}
