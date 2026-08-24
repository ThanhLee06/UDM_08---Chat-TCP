package vn.edu.ut.udm08.client.controller;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ChatListener;
import vn.edu.ut.udm08.integration.ClientLoginService;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

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
    private ClientLoginService clientLoginService;
    private ChatController activeChatController;
    private List<UserProfile> pendingUserList;

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
        statusLabel.setText("Dang ket noi toi Server...");

        clientLoginService = new ClientLoginService();
        try {
            clientLoginService.connectAndLogin(host, port, username, avatarId, new ChatListener() {
                @Override
                public void onLoginSuccess(ProtocolMessage message) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            openChatWindow(clientLoginService.getChatClient(), username);
                        }
                    });
                }

                @Override
                public void onUserListUpdated(List<UserProfile> users) {
                    pendingUserList = users;
                    if (activeChatController != null) {
                        activeChatController.updateOnlineUsers(users);
                    }
                }

                @Override
                public void onMessageReceived(ProtocolMessage message) {
                    if (activeChatController != null) {
                        activeChatController.receiveMessage(message);
                    }
                }

                @Override
                public void onMessageSentSuccess(String messageId) {
                }

                @Override
                public void onErrorReceived(String errorCode, String errorMessage) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            connectButton.setDisable(false);
                            showError("Dang nhap that bai: " + errorMessage);
                        }
                    });
                }

                @Override
                public void onConnectionLost(Throwable cause) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            connectButton.setDisable(false);
                            showError("Mat ket noi toi Server");
                        }
                    });
                }
            });
        } catch (Exception e) {
            connectButton.setDisable(false);
            showError("Khong the ket noi den Server: " + e.getMessage());
        }
    }

    private void openChatWindow(ChatClient client, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Scene chatScene = new Scene(loader.load());
            ChatController chatController = loader.getController();
            chatController.setCurrentUsername(username);
            chatController.setSendListener(new ChatController.MessageSendListener() {
                @Override
                public void onSendMessage(ProtocolMessage message) {
                    try {
                        client.sendMessage(message.target, message.content);
                    } catch (Exception e) {
                    }
                }
            });
            this.activeChatController = chatController;
            if (pendingUserList != null) {
                chatController.updateOnlineUsers(pendingUserList);
            }
            Stage stage = (Stage) connectButton.getScene().getWindow();
            stage.setTitle("UDM08 Chat - " + username);
            stage.setScene(chatScene);
            stage.setResizable(true);
        } catch (IOException e) {
            showError("Khong the tai giao dien Chat: " + e.getMessage());
        }
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
