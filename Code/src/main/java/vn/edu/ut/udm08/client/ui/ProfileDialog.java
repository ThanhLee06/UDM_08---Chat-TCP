package vn.edu.ut.udm08.client.ui;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.shared.dto.AuthUserDto;
import vn.edu.ut.udm08.shared.dto.ProfileUpdate;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public final class ProfileDialog {
    private final ChatClient client;
    private String avatar;
    public ProfileDialog(ChatClient client) { this.client = client; }
    public void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông tin cá nhân");
        TextField name = new TextField();
        name.setPromptText("Tên hiển thị");
        Label contact = new Label();
        Label status = new Label("Đang tải hồ sơ...");
        var preview = AvatarImages.view("avatar1", 80);
        ComboBox<String> presets = new ComboBox<>();
        presets.getItems().addAll("avatar1", "avatar2", "avatar3");
        presets.setPromptText("Chọn ảnh có sẵn");
        presets.setOnAction(event -> { avatar = presets.getValue(); AvatarImages.apply(preview, avatar); });
        Button upload = new Button("Chọn ảnh từ máy");
        upload.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh PNG/JPG", "*.png", "*.jpg", "*.jpeg"));
            var file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file == null) return;
            try {
                if (file.length() > 5 * 1024 * 1024) throw new IllegalArgumentException("Ảnh tối đa 5 MB");
                var image = new javafx.scene.image.Image(file.toURI().toString(), 128, 128, false, true);
                if (image.isError()) throw new IllegalArgumentException("Không đọc được ảnh");
                var png = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(png, "png", bytes);
                avatar = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
                preview.setImage(image);
                status.setText("Ảnh sẽ được tải lên khi lưu");
            } catch (Exception error) { status.setText("Không đọc được ảnh. Chọn PNG/JPG tối đa 5 MB."); }
        });
        VBox form = new VBox(12, preview, new Label("Tên hiển thị"), name, contact, presets, upload, status);
        form.setStyle("-fx-padding: 20; -fx-pref-width: 370;");
        form.setDisable(true);
        dialog.getDialogPane().setContent(form);
        ButtonType saveType = new ButtonType("Lưu thay đổi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CLOSE);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.setDisable(true);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (name.getText().isBlank() || name.getText().trim().length() > 50) { status.setText("Tên hiển thị phải có 1–50 ký tự"); return; }
            ProfileUpdate update = new ProfileUpdate();
            update.displayName = name.getText().trim();
            update.avatar = avatar;
            form.setDisable(true);
            save.setDisable(true);
            client.requestFeature(MessageType.PROFILE_UPDATE, JsonUtil.toJson(update)).whenComplete((response, error) -> Platform.runLater(() -> {
                form.setDisable(false);
                save.setDisable(false);
                if (error == null) {
                    var user = JsonUtil.fromJson(response.content, AuthUserDto.class);
                    avatar = user.getAvatarPath();
                    AvatarImages.apply(preview, avatar);
                    status.setText("Đã lưu hồ sơ");
                } else status.setText("Không lưu được hồ sơ. Vui lòng thử lại.");
            }));
        });
        client.requestFeature(MessageType.PROFILE_GET, "").whenComplete((response, error) -> Platform.runLater(() -> {
            if (error != null) { status.setText("Không tải được hồ sơ. Đóng và mở lại để thử lại."); return; }
            AuthUserDto user = JsonUtil.fromJson(response.content, AuthUserDto.class);
            name.setText(user.getDisplayName());
            contact.setText("Tài khoản: " + user.getUsername() + "\nEmail: " + user.getEmail() + "\nSố điện thoại: " + user.getPhoneNumber());
            avatar = user.getAvatarPath();
            AvatarImages.apply(preview, avatar);
            status.setText("Email và số điện thoại chỉ hiển thị, chưa hỗ trợ thay đổi.");
            form.setDisable(false);
            save.setDisable(false);
        }));
        dialog.show();
    }
}
