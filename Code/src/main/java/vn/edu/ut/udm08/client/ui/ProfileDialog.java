package vn.edu.ut.udm08.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.ui.components.Toast;
import vn.edu.ut.udm08.shared.dto.AuthUserDto;
import vn.edu.ut.udm08.shared.dto.ProfileUpdate;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;

public final class ProfileDialog {

    private final ChatClient client;
    private String avatar;
    private AuthUserDto currentUser;
    private boolean isEditMode = false;

    public ProfileDialog(ChatClient client) {
        this.client = client;
    }

    public void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Hồ sơ cá nhân");
        dialog.setHeaderText(null);

        var preview = AvatarImages.view("avatar1", 84);
        var avatarWrap = new StackPane(preview);
        avatarWrap.setAlignment(Pos.CENTER);
        avatarWrap.setPadding(new Insets(10, 0, 10, 0));

        Label displayNameHeader = new Label("...");
        displayNameHeader.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #172b4d;");

        Label usernameHeader = new Label("@...");
        usernameHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        VBox headerBox = new VBox(4, avatarWrap, displayNameHeader, usernameHeader);
        headerBox.setAlignment(Pos.CENTER);

        // Edit avatar section
        ComboBox<String> presets = new ComboBox<>();
        presets.getItems().addAll("avatar1", "avatar2", "avatar3");
        presets.setPromptText("Ảnh có sẵn");
        presets.setMaxWidth(Double.MAX_VALUE);
        presets.setOnAction(event -> {
            avatar = presets.getValue();
            AvatarImages.apply(preview, avatar);
        });

        Button upload = new Button("Chọn từ máy");
        upload.setMaxWidth(Double.MAX_VALUE);
        upload.getStyleClass().add("group-action-btn");
        upload.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh PNG/JPG", "*.png", "*.jpg", "*.jpeg"));
            var file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file == null) return;
            try {
                if (file.length() > 5 * 1024 * 1024) throw new IllegalArgumentException("Ảnh tối đa 5 MB");
                var image = new Image(file.toURI().toString(), 128, 128, false, true);
                if (image.isError()) throw new IllegalArgumentException("Không đọc được ảnh");
                var png = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(png, "png", bytes);
                avatar = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
                preview.setImage(image);
            } catch (Exception ignored) {}
        });

        HBox avatarActions = new HBox(8, presets, upload);
        avatarActions.setAlignment(Pos.CENTER);
        HBox.setHgrow(presets, Priority.ALWAYS);
        HBox.setHgrow(upload, Priority.ALWAYS);
        avatarActions.setVisible(false);
        avatarActions.setManaged(false);

        // Name edit field
        Label nameInputLabel = new Label("Tên hiển thị");
        nameInputLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #172b4d;");
        TextField nameField = new TextField();
        nameField.setPromptText("Tên hiển thị (1-50 ký tự)");
        nameField.setStyle("-fx-background-radius: 8; -fx-padding: 10 14; -fx-font-size: 13px;");
        VBox nameEditBox = new VBox(6, nameInputLabel, nameField);
        nameEditBox.setVisible(false);
        nameEditBox.setManaged(false);

        // Info rows (Read-only view)
        Label usernameValue = new Label("...");
        Label emailValue = new Label("...");
        Label phoneValue = new Label("...");

        VBox infoSection = new VBox(4,
                infoRow("Tài khoản", usernameValue),
                infoRow("Email", emailValue),
                infoRow("Số điện thoại", phoneValue)
        );
        infoSection.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-padding: 14;");

        Label status = new Label("Đang tải hồ sơ...");
        status.getStyleClass().add("profile-status");
        status.setWrapText(true);

        // Buttons
        Button editToggleBtn = new Button("Chỉnh sửa hồ sơ");
        editToggleBtn.getStyleClass().add("profile-save-btn");
        editToggleBtn.setMaxWidth(Double.MAX_VALUE);

        Button saveBtn = new Button("Lưu thay đổi");
        saveBtn.getStyleClass().add("profile-save-btn");

        Button cancelBtn = new Button("Hủy");
        cancelBtn.getStyleClass().add("group-action-btn");

        HBox editActionBox = new HBox(8, cancelBtn, saveBtn);
        editActionBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);
        HBox.setHgrow(saveBtn, Priority.ALWAYS);
        editActionBox.setVisible(false);
        editActionBox.setManaged(false);

        Runnable applyMode = () -> {
            avatarActions.setVisible(isEditMode);
            avatarActions.setManaged(isEditMode);
            nameEditBox.setVisible(isEditMode);
            nameEditBox.setManaged(isEditMode);
            editActionBox.setVisible(isEditMode);
            editActionBox.setManaged(isEditMode);
            editToggleBtn.setVisible(!isEditMode);
            editToggleBtn.setManaged(!isEditMode);
        };

        editToggleBtn.setOnAction(e -> {
            isEditMode = true;
            applyMode.run();
        });

        cancelBtn.setOnAction(e -> {
            isEditMode = false;
            if (currentUser != null) {
                nameField.setText(currentUser.getDisplayName());
                avatar = currentUser.getAvatarPath();
                AvatarImages.apply(preview, avatar);
            }
            applyMode.run();
        });

        saveBtn.setOnAction(event -> {
            if (nameField.getText().isBlank() || nameField.getText().trim().length() > 50) {
                status.setText("Tên hiển thị phải từ 1-50 ký tự");
                return;
            }
            ProfileUpdate update = new ProfileUpdate();
            update.displayName = nameField.getText().trim();
            update.avatar = avatar;
            saveBtn.setDisable(true);
            status.setText("Đang lưu...");

            client.requestFeature(MessageType.PROFILE_UPDATE, JsonUtil.toJson(update)).whenComplete((response, error) -> Platform.runLater(() -> {
                saveBtn.setDisable(false);
                if (error == null) {
                    var user = JsonUtil.fromJson(response.content, AuthUserDto.class);
                    currentUser = user;
                    avatar = user.getAvatarPath();
                    AvatarImages.apply(preview, avatar);
                    displayNameHeader.setText(user.getDisplayName());
                    status.setText("");
                    isEditMode = false;
                    applyMode.run();
                    Toast.showSuccess(dialog.getDialogPane().getScene().getWindow(), "Đã cập nhật hồ sơ cá nhân");
                } else {
                    status.setText("Không lưu được hồ sơ. Vui lòng thử lại.");
                }
            }));
        });

        VBox form = new VBox(16, headerBox, avatarActions, nameEditBox, infoSection, status, editToggleBtn, editActionBox);
        form.getStyleClass().add("profile-card");
        form.setPrefWidth(380);
        form.setDisable(true);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/chat.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        client.requestFeature(MessageType.PROFILE_GET, "").whenComplete((response, error) -> Platform.runLater(() -> {
            if (error != null) {
                status.setText("Không tải được hồ sơ.");
                return;
            }
            AuthUserDto user = JsonUtil.fromJson(response.content, AuthUserDto.class);
            currentUser = user;
            displayNameHeader.setText(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
            usernameHeader.setText("@" + user.getUsername());
            nameField.setText(user.getDisplayName());
            usernameValue.setText(user.getUsername());
            emailValue.setText(user.getEmail() != null ? user.getEmail() : "Chưa cập nhật");
            phoneValue.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "Chưa cập nhật");
            avatar = user.getAvatarPath();
            AvatarImages.apply(preview, avatar);
            status.setText("");
            form.setDisable(false);
        }));

        dialog.show();
    }

    private HBox infoRow(String label, Label value) {
        Label key = new Label(label);
        key.getStyleClass().add("profile-info-label");
        key.setMinWidth(100);
        value.getStyleClass().add("profile-info-value");
        HBox row = new HBox(12, key, value);
        row.getStyleClass().add("profile-info-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
