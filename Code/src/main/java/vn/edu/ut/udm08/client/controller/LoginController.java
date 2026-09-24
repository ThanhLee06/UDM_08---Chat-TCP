package vn.edu.ut.udm08.client.controller;
import vn.edu.ut.udm08.client.ui.sidebar.ClientConversationSource;
import javafx.stage.WindowEvent;

import java.io.File;
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
import vn.edu.ut.udm08.shared.dto.RegisterResponse;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

public class LoginController {
    public void attachToStage(Stage stage) { stage.setOnHidden(event -> dispose()); }
    public void dispose() {
        if (activeChatController != null) activeChatController.disposeSidebar();
        if (otpInputController != null) endOtpSession();
        if (forgotTimer != null) forgotTimer.stop();
        if (clientLoginService != null) clientLoginService.getChatClient().close();
    }
    private final vn.edu.ut.udm08.client.config.ClientConfigStore configStore = new vn.edu.ut.udm08.client.config.ClientConfigStore();
    private vn.edu.ut.udm08.client.network.ClientConfig networkConfig = new vn.edu.ut.udm08.client.network.ClientConfig("127.0.0.1", 8080);
    @FXML private Label connectionLabel;

    @FXML
    private void onConnectionSettings() {
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Cấu hình kết nối");
        TextField host = new TextField(networkConfig.getHost());
        TextField port = new TextField(Integer.toString(networkConfig.getPort()));
        TextField connectTimeout = new TextField(Integer.toString(networkConfig.getConnectTimeoutMs()));
        TextField requestTimeout = new TextField(Integer.toString(networkConfig.getRequestTimeoutMs()));
        Label error = new Label(); error.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, new Label("Host / IP Server"), host,
            new Label("Port"), port, new Label("Timeout kết nối (ms)"), connectTimeout,
            new Label("Timeout phản hồi (ms)"), requestTimeout, error));
        javafx.scene.control.ButtonType save = new javafx.scene.control.ButtonType("Lưu", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                var next = new vn.edu.ut.udm08.client.network.ClientConfig(host.getText(), Integer.parseInt(port.getText().trim()),
                    Integer.parseInt(connectTimeout.getText().trim()), Integer.parseInt(requestTimeout.getText().trim()));
                configStore.save(next);
                if (clientLoginService != null) clientLoginService.disconnect();
                endOtpSession();
                networkConfig = next;
                connectionLabel.setText("Server: " + next + " · Đã lưu");
            } catch (Exception e) { event.consume(); error.setText("Không thể lưu: " + e.getMessage()); }
        });
        dialog.showAndWait();
    }

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
    private ChatController activeChatController;
    private Stage activeStage;
    private volatile List<UserProfile> pendingUserList;
    private final ConversationCache conversationCache = new ConversationCache();
    private String currentRegistrationId;
    private String currentRegistrationEmail;
    private String currentRegistrationPhone;
    private OtpInputController otpInputController;
    private int registrationVersion;
    private boolean forgotBusy;
    private long forgotResendAt;
    private javafx.animation.Timeline forgotTimer;

    private void refreshForgotControls() {
        long seconds = Math.max(0, (forgotResendAt - System.currentTimeMillis() + 999) / 1000);
        forgotSendOtpBtn.setDisable(forgotBusy || seconds > 0);
        forgotSendOtpBtn.setText(seconds > 0 ? "Gửi lại (" + seconds + "s)" : "Gửi mã OTP");
        resetPasswordBtn.setDisable(forgotBusy);
    }

    @FXML
    private void initialize() {
        try {
            networkConfig = configStore.load();
            if (connectionLabel != null) connectionLabel.setText("Server: " + networkConfig + " · Chưa kết nối");
        } catch (java.io.IOException e) {
            if (connectionLabel != null) connectionLabel.setText("Cấu hình lỗi — mở Cấu hình kết nối để sửa");
        }
        if (uploadAvatarBtn != null) { uploadAvatarBtn.setVisible(false); uploadAvatarBtn.setManaged(false); }

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
        if (clientLoginService != null) clientLoginService.getChatClient().cancelAuthRequest();
        registrationVersion++;
        currentRegistrationId = null;
        otpInputController.stop();
    }
    private void verifyOtpCode(String code) {
        if (currentRegistrationId == null || currentRegistrationId.isBlank()) {
            showError(otpStatusLabel, "Phiên đăng ký không hợp lệ, vui lòng thử lại");
            return;
        }
        otpStatusLabel.setStyle("-fx-text-fill: #0068ff;");
        otpStatusLabel.setText("Đang xác thực mã OTP...");
        if (clientLoginService != null && clientLoginService.getChatClient() != null) {
            clientLoginService.getChatClient().sendVerifyOtp(currentRegistrationId, code);
        }
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
        if (currentRegistrationId != null && clientLoginService != null && clientLoginService.getChatClient() != null) {
            otpStatusLabel.setStyle("-fx-text-fill: #0068ff;");
            otpStatusLabel.setText("Đang gửi lại mã OTP...");
            clientLoginService.getChatClient().sendResendOtp(currentRegistrationId);
        }
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

    private ChatListener createAuthListener() {
        return new ChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                String authenticatedUsername = phoneField.getText() != null ? phoneField.getText().trim() : "";
                if (message != null && message.content != null && !message.content.isBlank()) {
                    try {
                        vn.edu.ut.udm08.shared.dto.AuthUserDto userDto = vn.edu.ut.udm08.shared.protocol.JsonUtil.fromJson(message.content, vn.edu.ut.udm08.shared.dto.AuthUserDto.class);
                        if (userDto != null && userDto.getUsername() != null) {
                            authenticatedUsername = userDto.getUsername();
                        }
                    } catch (Exception ignored) {
                    }
                }
                final String finalUsername = authenticatedUsername;
                conversationCache.setCurrentUser(finalUsername);
                Platform.runLater(() -> openChatWindow(clientLoginService.getChatClient(), finalUsername));
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
                if (message == null || message.type == null) return;
                switch (message.type) {
                    case AUTH_REGISTER_OTP_REQUIRED -> Platform.runLater(() -> {
                        setRegistrationBusy(false);
                        try {
                            RegisterResponse res = vn.edu.ut.udm08.shared.protocol.JsonUtil.fromJson(message.content, RegisterResponse.class);
                            if (res != null) {
                                if (res.getRegistrationId() != null && !res.getRegistrationId().isBlank()) {
                                    currentRegistrationId = res.getRegistrationId();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        showOtpPane();
                    });
                    case AUTH_REGISTER_OK -> Platform.runLater(() -> {
                        setRegistrationBusy(false);
                        showInfoAlert("Đăng ký thành công", "Tài khoản của bạn đã được tạo thành công! Vui lòng đăng nhập.");
                        showLoginTab();
                    });
                    case AUTH_FORGOT_OTP_REQUIRED -> Platform.runLater(() -> {
                        forgotBusy = false;
                        forgotResendAt = System.currentTimeMillis() + 60000;
                        if (forgotTimer != null) forgotTimer.stop();
                        forgotTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), tick -> refreshForgotControls()));
                        forgotTimer.setCycleCount(60); forgotTimer.play(); refreshForgotControls();
                        if (forgotStatusLabel != null) {
                            forgotStatusLabel.setStyle("-fx-text-fill: #16a34a;");
                            forgotStatusLabel.setText(message.content != null ? message.content : "Đã gửi mã OTP.");
                        }
                    });
                    case AUTH_FORGOT_OK -> Platform.runLater(() -> {
                        forgotBusy = false; refreshForgotControls();
                        showInfoAlert("Đặt lại mật khẩu thành công", "Mật khẩu của bạn đã được cập nhật. Vui lòng đăng nhập lại.");
                        showLoginTab();
                    });
                    default -> {
                        conversationCache.addRealtimeMessage(message);
                        if (activeChatController != null) {
                            activeChatController.receiveMessage(message);
                        }
                    }
                }
            }

            @Override
            public void onMessageSentSuccess(String messageId) {}

            @Override
            public void onMessageStatusUpdated(ProtocolMessage message) {
                conversationCache.updateMessageStatus(message);
                if (activeChatController != null) activeChatController.updateDeliveryStatus(message);
            }

            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                Platform.runLater(() -> {
                    otpInputController.setBusy(false);
                    if (otpPane.isVisible()) otpInputController.clear();
                    forgotBusy = false;
                    refreshForgotControls();
                    setRegistrationBusy(false);
                    if ("FORCE_LOGOUT".equalsIgnoreCase(errorCode)) {
                        handleKickedSession(errorMessage);
                    } else {
                        if (loginBtn != null) loginBtn.setDisable(false);
                        if (otpStatusLabel != null && otpPane.isVisible()) {
                            showError(otpStatusLabel, errorMessage);
                        } else if (regStatusLabel != null && registerPane.isVisible()) {
                            showError(regStatusLabel, errorMessage);
                        } else if (forgotStatusLabel != null && forgotPane.isVisible()) {
                            showError(forgotStatusLabel, errorMessage);
                        } else if (statusLabel != null) {
                            showError(statusLabel, "Đăng nhập thất bại: " + errorMessage);
                        }
                    }
                });
            }

            @Override
            public void onConnectionLost(Throwable cause) {
                conversationCache.clear();
                Platform.runLater(() -> {
                    setRegistrationBusy(false);
                    if (activeChatController != null) {
                        handleKickedSession("Mất kết nối tới Server. Vui lòng kết nối lại.");
                    } else {
                        if (loginBtn != null) loginBtn.setDisable(false);
                        if (statusLabel != null) showError(statusLabel, "Không thể kết nối đến Server vui lòng bật ServerApp");
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
                if (clientLoginService != null) clientLoginService.getChatClient().close();
            }
        };
    }

    @FXML
    private void onLoginClick() {
        String phoneOrUsername = phoneField.getText() != null ? phoneField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText() : "";
        final String host = networkConfig.getHost();
        final int port = networkConfig.getPort();

        String errorMessage = loginValidator.validate(phoneOrUsername);
        if (errorMessage != null) {
            showError(statusLabel, errorMessage);
            return;
        }

        if (password.isEmpty()) {
            showError(statusLabel, "Vui lòng nhập mật khẩu");
            return;
        }

        loginBtn.setDisable(true);
        if (statusLabel != null) statusLabel.setText("");
        if (clientLoginService != null) clientLoginService.getChatClient().close();
        clientLoginService = new ClientLoginService();

        Thread connectThread = new Thread(() -> {
            try {
                clientLoginService.connectAndAuthLogin(host, port, phoneOrUsername, password, new vn.edu.ut.udm08.client.network.JavaFXChatListenerWrapper(createAuthListener()));
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
        String identifier = forgotPhoneField.getText() != null ? forgotPhoneField.getText().trim() : "";
        if (identifier.isBlank()) {
            showError(forgotStatusLabel, "Vui lòng nhập Số điện thoại hoặc Email");
            return;
        }
        forgotBusy = true; refreshForgotControls();
        final String host = networkConfig.getHost();
        final int port = networkConfig.getPort();
        if (clientLoginService == null) clientLoginService = new ClientLoginService();
        Thread thread = new Thread(() -> {
            try {
                if (!clientLoginService.getChatClient().isConnected()) {
                    clientLoginService.getChatClient().connectWithoutHello(host, port, new vn.edu.ut.udm08.client.network.JavaFXChatListenerWrapper(createAuthListener()));
                }
                clientLoginService.getChatClient().sendForgotInit(identifier);
            } catch (Exception e) {
                Platform.runLater(() -> { forgotBusy = false; refreshForgotControls(); showError(forgotStatusLabel, "Không thể kết nối Server: " + e.getMessage()); });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onResetPasswordClick() {
        String otp = forgotOtpField.getText() != null ? forgotOtpField.getText().trim() : "";
        String newPass = forgotNewPasswordField.getText();
        String confirmPass = forgotConfirmPasswordField.getText();
        String identifier = forgotPhoneField.getText() != null ? forgotPhoneField.getText().trim() : "";
        if (otp.isBlank()) {
            showError(forgotStatusLabel, "Vui lòng nhập mã OTP");
            return;
        }
        if (newPass == null || newPass.isBlank() || !newPass.equals(confirmPass)) {
            showError(forgotStatusLabel, "Mật khẩu mới không khớp hoặc bị trống");
            return;
        }
        if (clientLoginService != null && clientLoginService.getChatClient() != null) {
            forgotBusy = true; refreshForgotControls();
            clientLoginService.getChatClient().sendForgotReset(identifier, otp, newPass);
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

        final String host = networkConfig.getHost();
        final int port = networkConfig.getPort();
        setRegistrationBusy(true);
        if (regStatusLabel != null) regStatusLabel.setText("");

        if (clientLoginService == null || clientLoginService.getChatClient() == null) {
            clientLoginService = new ClientLoginService();
        }

        Thread thread = new Thread(() -> {
            try {
                if (!clientLoginService.getChatClient().isConnected()) {
                    clientLoginService.getChatClient().connectWithoutHello(host, port, new vn.edu.ut.udm08.client.network.JavaFXChatListenerWrapper(createAuthListener()));
                }
                vn.edu.ut.udm08.shared.dto.RegisterInitRequest req = new vn.edu.ut.udm08.shared.dto.RegisterInitRequest(
                    username, phone, email, password, "PRESET", regAvatarChoiceBox.getValue()
                );
                clientLoginService.getChatClient().sendRegisterInit(req);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setRegistrationBusy(false);
                    showError(regStatusLabel, "Không thể kết nối Server: " + e.getMessage());
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
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
            vn.edu.ut.udm08.client.ui.AvatarImages.configure(client);
            chatController.setCurrentUsername(username);
            chatController.loadSidebar(new ClientConversationSource(client));
            chatController.setSendListener(message -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    conversationCache.addRealtimeMessage(message);
                    if (message.convId != null && !message.convId.isBlank()) {
                        client.sendMessage(message);
                    } else {
                        client.sendMessage(message.target, message.content);
                    }
                } catch (Exception error) {
                    message.sendStatus = vn.edu.ut.udm08.shared.model.MessageSendStatus.FAILED;
                    message.errorMessage = error.getMessage();
                    Platform.runLater(() -> chatController.updateDeliveryStatus(message));
                }
            }));
            this.activeChatController = chatController;
            if (pendingUserList != null) {
                chatController.updateOnlineUsers(pendingUserList);
            }
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            this.activeStage = stage;
            stage.setTitle("UDM08 Chat - " + username);
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> chatController.disposeSidebar());
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

        Stage stage = activeStage;
        if (stage == null && loginBtn != null && loginBtn.getScene() != null) {
            stage = (Stage) loginBtn.getScene().getWindow();
        }

        activeChatController = null;

        if (stage != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
                Scene loginScene = new Scene(loader.load());
                LoginController newLoginController = loader.getController();
                newLoginController.attachToStage(stage);
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
