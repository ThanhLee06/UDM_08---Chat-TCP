package vn.edu.ut.udm08.client.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ChatListener;
import vn.edu.ut.udm08.integration.ClientLoginService;
import vn.edu.ut.udm08.server.auth.PhoneOtpService;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.server.service.UserLoginService;
import vn.edu.ut.udm08.server.service.UserRegisterService;
import vn.edu.ut.udm08.shared.dto.RegisterRequest;
import vn.edu.ut.udm08.shared.dto.RegisterResponse;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.model.UserProfile;

public class LoginController {

    @FXML
    private Button tabLoginBtn;
    @FXML
    private Button tabRegisterBtn;

    @FXML
    private VBox loginPane;
    @FXML
    private VBox registerPane;
    @FXML
    private VBox forgotPane;

    @FXML
    private TextField phoneField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisibleField;
    @FXML
    private Button toggleEyeBtn;
    @FXML
    private Button forgotPassLinkBtn;
    @FXML
    private Label statusLabel;
    @FXML
    private Button loginBtn;

    @FXML
    private ImageView regAvatarImage;
    @FXML
    private ComboBox<String> regAvatarChoiceBox;
    @FXML
    private Button uploadAvatarBtn;
    private String customAvatarPath = null;
    @FXML
    private TextField regUsernameField;
    @FXML
    private TextField regPhoneField;
    @FXML
    private TextField regOtpField;
    @FXML
    private Button sendOtpBtn;
    @FXML
    private PasswordField regPasswordField;
    @FXML
    private PasswordField regConfirmPasswordField;
    @FXML
    private Label regStatusLabel;
    @FXML
    private Button registerBtn;

    @FXML
    private TextField forgotPhoneField;
    @FXML
    private Button forgotSendOtpBtn;
    @FXML
    private TextField forgotOtpField;
    @FXML
    private PasswordField forgotNewPasswordField;
    @FXML
    private PasswordField forgotConfirmPasswordField;
    @FXML
    private Label forgotStatusLabel;
    @FXML
    private Button resetPasswordBtn;

    private boolean isPasswordVisible = false;
    private final LoginFormValidator loginValidator = new LoginFormValidator();
    private final RegisterFormValidator registerValidator = new RegisterFormValidator();
    private ClientLoginService clientLoginService;
    private UserRegisterService userRegisterService;
    private UserLoginService userLoginService;
    private UserRepository userRepository;
    private ChatController activeChatController;
    private volatile List<UserProfile> pendingUserList;
    private final PhoneOtpService sharedOtpService = new PhoneOtpService();

    @FXML
    private void initialize() {
        try {
            userRepository = new UserRepository();
            userRegisterService = new UserRegisterService(userRepository, new vn.edu.ut.udm08.shared.security.PasswordEncoder(), sharedOtpService);
            userLoginService = new UserLoginService(userRepository);
        } catch (Exception e) {
            userRepository = null;
            userRegisterService = null;
            userLoginService = null;
        }

        regAvatarChoiceBox.getItems().addAll("avatar1", "avatar2", "avatar3");
        regAvatarChoiceBox.setValue("avatar1");
        updateRegAvatarImage();

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    @FXML
    private void showLoginTab() {
        loginPane.setVisible(true);
        registerPane.setVisible(false);
        forgotPane.setVisible(false);
        tabLoginBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        tabRegisterBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        clearStatusLabels();
    }

    @FXML
    private void showRegisterTab() {
        loginPane.setVisible(false);
        registerPane.setVisible(true);
        forgotPane.setVisible(false);
        tabRegisterBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        tabLoginBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        clearStatusLabels();
    }

    @FXML
    private void showForgotTab() {
        loginPane.setVisible(false);
        registerPane.setVisible(false);
        forgotPane.setVisible(true);
        tabLoginBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        tabRegisterBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        clearStatusLabels();
    }

    private void clearStatusLabels() {
        if (statusLabel != null) statusLabel.setText("");
        if (regStatusLabel != null) regStatusLabel.setText("");
        if (forgotStatusLabel != null) forgotStatusLabel.setText("");
    }

    @FXML
    private void onTogglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            passwordField.setVisible(false);
            passwordVisibleField.setVisible(true);
            toggleEyeBtn.setText("🙈");
        } else {
            passwordVisibleField.setVisible(false);
            passwordField.setVisible(true);
            toggleEyeBtn.setText("👁");
        }
    }

    @FXML
    private void onRegAvatarChanged() {
        String selected = regAvatarChoiceBox.getValue();
        if ("Tự chọn từ máy".equals(selected)) {
            return;
        }
        customAvatarPath = null;
        updateRegAvatarImage();
    }

    @FXML
    private void onUploadAvatarClick() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn ảnh đại diện từ máy tính");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            Stage stage = (Stage) regAvatarImage.getScene().getWindow();
            File selectedFile = fileChooser.showOpenDialog(stage);
            if (selectedFile != null) {
                Image img = new Image(selectedFile.toURI().toString());
                regAvatarImage.setImage(img);
                customAvatarPath = selectedFile.getAbsolutePath();
                if (!regAvatarChoiceBox.getItems().contains("Tự chọn từ máy")) {
                    regAvatarChoiceBox.getItems().add("Tự chọn từ máy");
                }
                regAvatarChoiceBox.setValue("Tự chọn từ máy");
                if (regStatusLabel != null) regStatusLabel.setText("");
            }
        } catch (Exception e) {
            showError(regStatusLabel, "Không thể mở file ảnh: " + e.getMessage());
        }
    }

    @FXML
    private void onLoginClick() {
        String phoneOrUsername = phoneField.getText() != null ? phoneField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText().trim() : "";
        final String host = "127.0.0.1";
        final int port = 8080;

        String errorMessage = loginValidator.validate(phoneOrUsername);
        if (errorMessage != null) {
            showError(statusLabel, errorMessage);
            return;
        }

        if (password.isEmpty()) {
            showError(statusLabel, "Vui lòng nhập mật khẩu!");
            return;
        }

        String avatarId = "avatar1";
        String loginSenderName = phoneOrUsername;

        if (userLoginService != null) {
            vn.edu.ut.udm08.shared.dto.LoginResponse loginRes = userLoginService.login(new vn.edu.ut.udm08.shared.dto.LoginRequest(phoneOrUsername, password));
            if (!loginRes.isSuccess()) {
                showError(statusLabel, loginRes.getMessage());
                return;
            }
            User authenticatedUser = loginRes.getUser();
            if (authenticatedUser != null) {
                if (authenticatedUser.getUsername() != null && !authenticatedUser.getUsername().isBlank()) {
                    loginSenderName = authenticatedUser.getUsername();
                }
                if (authenticatedUser.getAvatarType() != null && !authenticatedUser.getAvatarType().isBlank()) {
                    avatarId = ("custom".equalsIgnoreCase(authenticatedUser.getAvatarType()) && authenticatedUser.getAvatarPath() != null)
                            ? authenticatedUser.getAvatarPath()
                            : authenticatedUser.getAvatarType();
                }
            }
        }

        final String finalSenderName = loginSenderName;
        final String finalAvatarId = avatarId;

        loginBtn.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #0068ff;");
        statusLabel.setText("Đang kết nối tới Server Chat TCP...");

        clientLoginService = new ClientLoginService();

        Thread connectThread = new Thread(() -> {
            try {
                clientLoginService.connectAndLogin(host, port, finalSenderName, finalAvatarId, new ChatListener() {
                    @Override
                    public void onLoginSuccess(ProtocolMessage message) {
                        Platform.runLater(() -> openChatWindow(clientLoginService.getChatClient(), finalSenderName));
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
                        Platform.runLater(() -> {
                            if ("FORCE_LOGOUT".equalsIgnoreCase(errorCode)) {
                                handleKickedSession(errorMessage);
                            } else {
                                if (loginBtn != null) loginBtn.setDisable(false);
                                if (statusLabel != null) showError(statusLabel, "Đăng nhập thất bại: " + errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onConnectionLost(Throwable cause) {
                        Platform.runLater(() -> {
                            if (activeChatController != null) {
                                handleKickedSession("Tài khoản của bạn vừa đăng nhập ở một thiết bị khác.");
                            } else {
                                if (loginBtn != null) loginBtn.setDisable(false);
                                if (statusLabel != null) showError(statusLabel, "Không thể kết nối đến Server (" + host + ":" + port + "). Vui lòng bật ServerApp!");
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (loginBtn != null) loginBtn.setDisable(false);
                    if (statusLabel != null) showError(statusLabel, "Không thể kết nối đến Server (" + host + ":" + port + "). Vui lòng bật ServerApp!");
                });
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onSendOtpClick() {
        String phone = regPhoneField.getText() != null ? regPhoneField.getText().trim() : "";
        if (phone.isEmpty() || !phone.matches("^0(3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}$")) {
            showError(regStatusLabel, "Vui lòng nhập SĐT hợp lệ (10 số đầu 03/05/07/08/09) trước khi lấy OTP");
            return;
        }

        PhoneOtpService otpSvc = (userRegisterService != null && userRegisterService.getPhoneOtpService() != null)
                ? userRegisterService.getPhoneOtpService()
                : sharedOtpService;

        otpSvc.generateOtp(phone);
        regStatusLabel.setText("");

        startOtpResendCountdown(sendOtpBtn, 30);
    }

    @FXML
    private void onForgotSendOtpClick() {
        String phone = forgotPhoneField.getText() != null ? forgotPhoneField.getText().trim() : "";
        if (phone.isEmpty() || !phone.matches("^0(3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}$")) {
            showError(forgotStatusLabel, "Vui lòng nhập SĐT hợp lệ trước khi lấy OTP");
            return;
        }

        PhoneOtpService otpSvc = (userRegisterService != null && userRegisterService.getPhoneOtpService() != null)
                ? userRegisterService.getPhoneOtpService()
                : sharedOtpService;

        otpSvc.generateOtp(phone);
        forgotStatusLabel.setText("");

        startOtpResendCountdown(forgotSendOtpBtn, 30);
    }

    @FXML
    private void onResetPasswordClick() {
        String phone = forgotPhoneField.getText() != null ? forgotPhoneField.getText().trim() : "";
        String otpCode = forgotOtpField.getText() != null ? forgotOtpField.getText().trim() : "";
        String newPassword = forgotNewPasswordField.getText() != null ? forgotNewPasswordField.getText() : "";
        String confirmPassword = forgotConfirmPasswordField.getText() != null ? forgotConfirmPasswordField.getText() : "";

        if (phone.isEmpty() || otpCode.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showError(forgotStatusLabel, "Vui lòng nhập đầy đủ thông tin");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            showError(forgotStatusLabel, "Mật khẩu mới xác nhận không trùng khớp");
            return;
        }
        if (newPassword.length() < 8) {
            showError(forgotStatusLabel, "Mật khẩu mới phải từ 8 ký tự");
            return;
        }

        PhoneOtpService otpSvc = (userRegisterService != null && userRegisterService.getPhoneOtpService() != null)
                ? userRegisterService.getPhoneOtpService()
                : sharedOtpService;

        if (userLoginService != null) {
            boolean success = userLoginService.resetPassword(phone, otpCode, newPassword, otpSvc);
            if (success) {
                Platform.runLater(() -> {
                    phoneField.setText(phone);
                    passwordField.setText(newPassword);
                    clearStatusLabels();
                    showInfoAlert("Đổi mật khẩu thành công", "🎉 Mật khẩu đã được cập nhật thành công! Vui lòng đăng nhập.");
                    showLoginTab();
                });
            } else {
                showError(forgotStatusLabel, "Đặt lại mật khẩu thất bại. Mã OTP không đúng hoặc SĐT chưa đăng ký!");
            }
        } else {
            showError(forgotStatusLabel, "Lỗi kết nối cơ sở dữ liệu. Vui lòng thử lại sau.");
        }
    }

    private void startOtpResendCountdown(Button button, int seconds) {
        button.setDisable(true);
        Thread thread = new Thread(() -> {
            for (int i = seconds; i > 0; i--) {
                final int remaining = i;
                Platform.runLater(() -> button.setText("Gửi lại (" + remaining + "s)"));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Platform.runLater(() -> {
                button.setText("Gửi lại OTP");
                button.setDisable(false);
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onRegisterClick() {
        String username = regUsernameField.getText() != null ? regUsernameField.getText().trim() : "";
        String phone = regPhoneField.getText() != null ? regPhoneField.getText().trim() : "";
        String password = regPasswordField.getText() != null ? regPasswordField.getText() : "";
        String confirmPassword = regConfirmPasswordField.getText() != null ? regConfirmPasswordField.getText() : "";
        String otpCode = regOtpField.getText() != null ? regOtpField.getText().trim() : "";

        String avatarType;
        String avatarPath;
        if (customAvatarPath != null && !customAvatarPath.isBlank()) {
            avatarType = "custom";
            avatarPath = customAvatarPath;
        } else {
            avatarType = regAvatarChoiceBox.getValue() != null ? regAvatarChoiceBox.getValue() : "avatar1";
            avatarPath = avatarType + ".jpg";
        }

        String validationError = registerValidator.validate(username, phone, password, confirmPassword);
        if (validationError != null) {
            showError(regStatusLabel, validationError);
            return;
        }

        if (otpCode.isEmpty()) {
            showError(regStatusLabel, "Vui lòng nhập mã OTP (6 chữ số)");
            return;
        }

        registerBtn.setDisable(true);
        regStatusLabel.setStyle("-fx-text-fill: #0068ff;");
        regStatusLabel.setText("Đang đăng ký tài khoản...");

        if (userRegisterService != null) {
            RegisterRequest request = new RegisterRequest(username, phone, password, avatarType, avatarPath, otpCode);
            RegisterResponse response = userRegisterService.register(request);

            if (response.isSuccess()) {
                Platform.runLater(() -> {
                    registerBtn.setDisable(false);
                    phoneField.setText(phone);
                    passwordField.setText(password);
                    clearStatusLabels();
                    showInfoAlert("Đăng ký thành công", "🎉 Chúc mừng bạn đã đăng ký tài khoản thành công! Hãy đăng nhập ngay.");
                    showLoginTab();
                });
            } else {
                registerBtn.setDisable(false);
                showError(regStatusLabel, "Đăng ký thất bại: " + response.getMessage());
            }
        } else {
            registerBtn.setDisable(false);
            showError(regStatusLabel, "Lỗi kết nối cơ sở dữ liệu. Vui lòng thử lại sau.");
        }
    }

    private void openChatWindow(ChatClient client, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Scene chatScene = new Scene(loader.load());
            ChatController chatController = loader.getController();
            chatController.setCurrentUsername(username);
            chatController.setSendListener(message -> {
                try {
                    client.sendMessage(message.target, message.content);
                } catch (Exception ignored) {
                }
            });
            this.activeChatController = chatController;
            if (pendingUserList != null) {
                chatController.updateOnlineUsers(pendingUserList);
            }
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            stage.setTitle("Chat TCP - " + username);
            stage.setScene(chatScene);
            stage.setResizable(true);
        } catch (Exception e) {
            if (loginBtn != null) loginBtn.setDisable(false);
            if (statusLabel != null) showError(statusLabel, "Không thể tải giao diện Chat: " + e.getMessage());
        }
    }

    private void handleKickedSession(String reason) {
        if (clientLoginService != null) {
            clientLoginService.disconnect();
        }

        Stage stage = null;
        if (loginBtn != null && loginBtn.getScene() != null) {
            stage = (Stage) loginBtn.getScene().getWindow();
        }

        activeChatController = null;

        if (stage != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
                Scene loginScene = new Scene(loader.load());
                LoginController newLoginController = loader.getController();
                stage.setTitle("UDM08 Chat - Đăng nhập");
                stage.setScene(loginScene);
                stage.setResizable(false);

                newLoginController.showKickedWarning(reason);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void showKickedWarning(String reason) {
        String msg = (reason != null && !reason.isBlank()) ? reason : "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác.";
        if (statusLabel != null) {
            showError(statusLabel, msg);
        }
        try {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Đăng xuất thiết bị");
            alert.setHeaderText("Tài khoản đã đăng nhập ở nơi khác");
            alert.setContentText(msg);
            alert.show();
        } catch (Exception ignored) {
        }
    }

    private void updateRegAvatarImage() {
        String avatarId = regAvatarChoiceBox.getValue();
        if (avatarId == null) {
            return;
        }
        URL imageUrl = LoginController.class.getResource("/images/" + avatarId + ".jpg");
        if (imageUrl != null) {
            regAvatarImage.setImage(new Image(imageUrl.toExternalForm()));
        }
    }

    private void showError(Label label, String message) {
        label.setStyle("-fx-text-fill: #dc2626;");
        label.setText(message);
    }

    private void showInfoAlert(String title, String message) {
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception ignored) {
        }
    }
}
