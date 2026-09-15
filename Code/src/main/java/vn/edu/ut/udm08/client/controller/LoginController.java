package vn.edu.ut.udm08.client.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import vn.edu.ut.udm08.client.cache.ConversationCache;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.ChatListener;
import vn.edu.ut.udm08.integration.ClientLoginService;
import vn.edu.ut.udm08.server.auth.EmailOtpService;
import vn.edu.ut.udm08.server.auth.SmtpOtpEmailSender;
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
    private HBox topTabBox;
    @FXML private Button tabLoginBtn;
    @FXML
    private Button tabRegisterBtn;

    @FXML
    private VBox loginPane;
    @FXML
    private VBox registerPane;
    @FXML
    private VBox forgotPane;
    @FXML private VBox otpPane;

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
    private TextField regEmailField;
    @FXML
    private PasswordField regPasswordField;
    @FXML
    private PasswordField regConfirmPasswordField;
    @FXML
    private Label regStatusLabel;
    @FXML
    private Button registerBtn;
    @FXML private Label otpTargetLabel;
    @FXML private TextField otp1;
    @FXML private TextField otp2;
    @FXML private TextField otp3;
    @FXML private TextField otp4;
    @FXML private TextField otp5;
    @FXML private TextField otp6;
    @FXML private Label otpStatusLabel;
    @FXML private Button otpResendBtn;
    @FXML private Button otpBackBtn;

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
    private final ConversationCache conversationCache = new ConversationCache();
    private EmailOtpService sharedOtpService;
    private String currentRegistrationId;
    private String currentRegistrationEmail;
    private String currentRegistrationPhone;
    private OtpInputController otpInputController;
    private int registrationVersion;

    @FXML
    private void initialize() {
        try {
            sharedOtpService = new EmailOtpService(new SmtpOtpEmailSender());
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
        otpInputController = new OtpInputController(
                List.of(otp1, otp2, otp3, otp4, otp5, otp6), otpResendBtn, otpBackBtn,
                this::verifyOtpCode, this::resendOtp);
        otpPane.visibleProperty().addListener((observable, oldValue, visible) -> {
            if (!visible) {
                endOtpSession();
            }
        });
    }
    private void endOtpSession() {
        registrationVersion++;
        currentRegistrationId = null;
        otpInputController.stop();
    }
    private void verifyOtpCode(String code) {
        final String registrationId = currentRegistrationId;
        final String registeredPhone = currentRegistrationPhone;
        final int version = registrationVersion;
        if (registrationId == null || userRegisterService == null) {
            otpInputController.stop();
            showError(otpStatusLabel, "Phiên đăng ký không còn hiệu lực. Vui lòng quay lại đăng ký.");
            return;
        }
        otpStatusLabel.setStyle("-fx-text-fill: #0068ff;");
        otpStatusLabel.setText("Đang xác thực mã OTP...");
        AccountTaskRunner.run(() -> userRegisterService.verifyRegistration(registrationId, code), response -> {
            if (version != registrationVersion) {
                return;
            }
            otpInputController.setBusy(false);
            if (response.isSuccess()) {
                otpInputController.stop();
                showInfoAlert("Đăng ký thành công", "Tài khoản của bạn đã được đăng ký thành công");
                phoneField.setText(registeredPhone);
                passwordField.clear();
                showLoginTab();
            } else {
                showOtpFailure(response);
            }
        }, error -> {
            if (version == registrationVersion) {
                otpInputController.setBusy(false);
                showError(otpStatusLabel, error);
                otpInputController.clear();
            }
        });
    }
    private void showOtpFailure(RegisterResponse response) {
        showError(otpStatusLabel, response.getMessage());
        otpInputController.clear();
        if (response.isRegistrationRestartRequired()) {
            endOtpSession();
        }
    }
    @FXML
    private void onOtpResendClick() {
        otpInputController.requestResend();
    }
    private void resendOtp() {
        final String registrationId = currentRegistrationId;
        final int version = registrationVersion;
        otpStatusLabel.setStyle("-fx-text-fill: #0068ff;");
        otpStatusLabel.setText("Đang gửi lại mã OTP...");
        AccountTaskRunner.run(() -> userRegisterService.resendOtp(registrationId), response -> {
            if (version != registrationVersion) {
                return;
            }
            otpInputController.setBusy(false);
            if (response.isSuccess()) {
                otpStatusLabel.setStyle("-fx-text-fill: #16a34a;");
                otpStatusLabel.setText("Đã gửi lại mã OTP mới tới " + currentRegistrationEmail);
                otpInputController.clear();
                otpInputController.restartCooldown();
            } else {
                showOtpFailure(response);
            }
        }, error -> {
            if (version == registrationVersion) {
                otpInputController.setBusy(false);
                showError(otpStatusLabel, error);
            }
        });
    }
    @FXML
    private void onOtpBackClick() {
        showRegisterTab();
    }

    @FXML
    private void showLoginTab() {
        topTabBox.setVisible(true);
        loginPane.setVisible(true);
        registerPane.setVisible(false);
        forgotPane.setVisible(false);
        otpPane.setVisible(false);
        tabLoginBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 4, 0, 0, 1);");
        tabRegisterBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand;");
        clearStatusLabels();
    }

    @FXML
    private void showRegisterTab() {
        topTabBox.setVisible(true);
        loginPane.setVisible(false);
        registerPane.setVisible(true);
        forgotPane.setVisible(false);
        otpPane.setVisible(false);
        tabRegisterBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 4, 0, 0, 1);");
        tabLoginBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand;");
        clearStatusLabels();
    }

    @FXML
    private void showForgotTab() {
        topTabBox.setVisible(true);
        loginPane.setVisible(false);
        registerPane.setVisible(false);
        forgotPane.setVisible(true);
        otpPane.setVisible(false);
        tabLoginBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand;");
        tabRegisterBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand;");
        clearStatusLabels();
    }
    private void showOtpPane() {
        topTabBox.setVisible(false);
        loginPane.setVisible(false);
        registerPane.setVisible(false);
        forgotPane.setVisible(false);
        otpPane.setVisible(true);
        otpStatusLabel.setText("");
        otpInputController.start();
    }

    private void clearStatusLabels() {
        if (statusLabel != null) statusLabel.setText("");
        if (regStatusLabel != null) regStatusLabel.setText("");
        if (forgotStatusLabel != null) forgotStatusLabel.setText("");
        if (otpStatusLabel != null) otpStatusLabel.setText("");
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
                setCroppedAvatarImage(regAvatarImage, img);
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
        String password = passwordField.getText() != null ? passwordField.getText() : "";
        final String host = "127.0.0.1";
        final int port = 8080;

        String errorMessage = loginValidator.validate(phoneOrUsername);
        if (errorMessage != null) {
            showError(statusLabel, errorMessage);
            return;
        }

        if (password.isEmpty()) {
            showError(statusLabel, "Vui lòng nhập mật khẩu");
            return;
        }

        String avatarId = "avatar1";
        String loginSenderName = phoneOrUsername;

        if (userLoginService == null) {
            showError(statusLabel, "Không thể kết nối cơ sở dữ liệu tài khoản");
            return;
        }
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
        final String finalSenderName = loginSenderName;
        final String finalAvatarId = avatarId;

        loginBtn.setDisable(true);
        if (statusLabel != null) statusLabel.setText(""); // Don't show status text on success
        clientLoginService = new ClientLoginService();

        Thread connectThread = new Thread(() -> {
            try {
                clientLoginService.connectAndLogin(host, port, finalSenderName, finalAvatarId, new ChatListener() {
                    @Override
                    public void onLoginSuccess(ProtocolMessage message) {
                        conversationCache.setCurrentUser(finalSenderName);
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
                        conversationCache.addRealtimeMessage(message);
                        if (activeChatController != null) {
                            activeChatController.receiveMessage(message);
                        }
                    }

                    @Override
                    public void onMessageSentSuccess(String messageId) {
                    }

                    @Override
                    public void onMessageStatusUpdated(ProtocolMessage message) {
                        conversationCache.updateMessageStatus(message);
                    }

                    @Override
                    public void onErrorReceived(String errorCode, String errorMessage) {
                        Platform.runLater(() -> {
                            if ("FORCE_LOGOUT".equalsIgnoreCase(errorCode)) {
                                handleKickedSession(errorMessage);
                            } else {
                                if (loginBtn != null) loginBtn.setDisable(false);
                                if (statusLabel != null) showError(statusLabel, "Đăng nhập thất bại " + errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onConnectionLost(Throwable cause) {
                        conversationCache.clear();
                        Platform.runLater(() -> {
                            if (activeChatController != null) {
                                handleKickedSession("Tài khoản của bạn vừa đăng nhập ở một thiết bị khác");
                            } else {
                                if (loginBtn != null) loginBtn.setDisable(false);
                                if (statusLabel != null) showError(statusLabel, "Không thể kết nối đến Server (" + host + ":" + port + ") vui lòng bật ServerApp");
                            }
                        });
                    }

                    @Override
                    public void onSessionExpired(String errorCode, String errorMessage) {
                        conversationCache.clear();
                        Platform.runLater(() -> handleKickedSession("Phiên đăng nhập hết hạn: " + errorMessage));
                    }

                    @Override
                    public void onLogoutSuccess() {
                        conversationCache.clear();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (loginBtn != null) loginBtn.setDisable(false);
                    if (statusLabel != null) showError(statusLabel, "Không thể kết nối đến Server (" + host + ":" + port + ") vui lòng bật ServerApp");
                });
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onForgotSendOtpClick() {
        if (userLoginService == null || sharedOtpService == null) {
            showError(forgotStatusLabel, "Không thể kết nối dịch vụ tài khoản");
            return;
        }
        String identifier = forgotPhoneField.getText().trim();
        forgotSendOtpBtn.setDisable(true);
        forgotStatusLabel.setText("loading...");
        AccountTaskRunner.run(() -> {
            userLoginService.requestPasswordReset(identifier, sharedOtpService);
            return true;
        }, result -> {
            forgotStatusLabel.setText("Đã gửi mã xác thực tới email mã có hiệu lực 5 phút");

        startOtpResendCountdown(forgotSendOtpBtn, 60);
        }, error -> {
            forgotSendOtpBtn.setDisable(false);
            showError(forgotStatusLabel, error);
        });
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
        if (userLoginService != null) {
            boolean success = userLoginService.resetPassword(phone, otpCode, newPassword, sharedOtpService);
            if (success) {
                Platform.runLater(() -> {
                    phoneField.setText(phone);
                    passwordField.setText(newPassword);
                    clearStatusLabels();
                    showInfoAlert("Đổi mật khẩu thành công", "Mật khẩu đã được cập nhật thành công vui lòng đăng nhập");
                    showLoginTab();
                });
            } else {
                showError(forgotStatusLabel, "Đặt lại mật khẩu thất bại mã OTP không đúng hết hạn hoặc tài khoản không hợp lệ");
            }
        } else {
            showError(forgotStatusLabel, "Lỗi kết nối cơ sở dữ liệu vui lòng thử lại sau");
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
        String username = regUsernameField.getText().trim();
        String phone = regPhoneField.getText().trim();
        String email = regEmailField.getText().trim();
        String password = regPasswordField.getText();
        String confirm = regConfirmPasswordField.getText();
        String error = registerValidator.validate(username, phone, email, password, confirm);
        if (error != null) {
            showError(regStatusLabel, error);
            return;
        }

        if (userRegisterService == null) {
            showError(regStatusLabel, "Không thể kết nối dịch vụ tài khoản");
            return;
        }
        String avatarType = customAvatarPath != null ? "custom" : regAvatarChoiceBox.getValue();
        String avatarPath = customAvatarPath != null ? customAvatarPath : avatarType + ".jpg";
        RegisterRequest request = new RegisterRequest(username, phone, email, password, avatarType, avatarPath);
        setRegistrationBusy(true);
        regStatusLabel.setStyle("-fx-text-fill: #0068ff;");
        regStatusLabel.setText("loading...");
        AccountTaskRunner.run(() -> userRegisterService.register(request), response -> {
            setRegistrationBusy(false);

            if (!response.isSuccess()) {
                showError(regStatusLabel, response.getMessage());
                return;
            }
            currentRegistrationId = response.getRegistrationId();
            currentRegistrationEmail = email;
            currentRegistrationPhone = phone;
            regPasswordField.clear();
            regConfirmPasswordField.clear();
            regStatusLabel.setText("");
            if (otpTargetLabel != null) {
                otpTargetLabel.setText("Nhập mã OTP gồm 6 chữ số đã được gửi tới " + email);
            }
            showOtpPane();
                }, message -> {
            setRegistrationBusy(false);
                showError(regStatusLabel, message);
            });
    }
    private void setRegistrationBusy(boolean busy) {
        registerPane.setDisable(busy);
        tabLoginBtn.setDisable(busy);
        tabRegisterBtn.setDisable(busy);
    }

    private void openChatWindow(ChatClient client, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Scene chatScene = new Scene(loader.load());
            ChatController chatController = loader.getController();
            chatController.setCurrentUsername(username);
            chatController.setSendListener(message -> {
                try {
                    conversationCache.addRealtimeMessage(message);
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
            if (statusLabel != null) showError(statusLabel, "Không thể tải giao diện Chat " + e.getMessage());
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
        String msg = (reason != null && !reason.isBlank()) ? reason : "Tài khoản của bạn vừa đăng nhập ở một thiết bị khác";
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
            setCroppedAvatarImage(regAvatarImage, new Image(imageUrl.toExternalForm()));
        }
    }
    private void setCroppedAvatarImage(ImageView imageView, Image image) {
        if (imageView == null || image == null) return;
        imageView.setImage(image);
        Runnable applyViewport = () -> {
            double w = image.getWidth();
            double h = image.getHeight();
            if (w > 0 && h > 0) {
                double minDim = Math.min(w, h);
                double x = (w - minDim) / 2.0;
                double y = (h - minDim) / 2.0;
                imageView.setViewport(new Rectangle2D(x, y, minDim, minDim));
            }
        };
        if (image.getWidth() > 0 && image.getHeight() > 0) {
            applyViewport.run();
        } else {
            image.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() > 0) {
                    applyViewport.run();
                }
            });
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
