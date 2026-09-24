package vn.edu.ut.udm08.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import vn.edu.ut.udm08.client.network.AttachmentTransfer;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.ui.components.AppIcon;
import vn.edu.ut.udm08.shared.dto.Attachment;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CompletionException;

public final class AttachmentView {

    private AttachmentView() {}

    public static VBox create(ChatClient client, Attachment file) {
        VBox container = new VBox(6);
        container.setMaxWidth(300);

        if (file == null) return container;

        String filename = file.name != null ? file.name : "attachment";
        String lowerName = filename.toLowerCase(Locale.ROOT);
        boolean isImage = lowerName.endsWith(".png") || lowerName.endsWith(".jpg") 
                       || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") 
                       || lowerName.endsWith(".webp");

        if (isImage) {
            renderImageThumbnail(container, client, file, filename);
        } else {
            renderFileCard(container, client, file, filename);
        }

        return container;
    }

    private static void renderImageThumbnail(VBox container, ChatClient client, Attachment file, String filename) {
        Label status = new Label("Đang tải ảnh...");
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-padding: 4;");

        ImageView imageView = new ImageView();
        imageView.setFitWidth(240);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCursor(javafx.scene.Cursor.HAND);

        // Clip rounded corners for image thumbnail
        Rectangle clip = new Rectangle();
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        clip.widthProperty().bind(imageView.fitWidthProperty());
        clip.heightProperty().bind(imageView.fitHeightProperty());
        imageView.setClip(clip);

        container.getChildren().addAll(status);

        if (client != null) {
            new AttachmentTransfer(client).download(file, progress -> {
                Platform.runLater(() -> status.setText("Đang tải " + Math.round(progress * 100) + "%..."));
            }).whenComplete((bytes, error) -> Platform.runLater(() -> {
                if (error != null || bytes == null) {
                    status.setText("Không tải được ảnh · Nhấn để thử lại");
                    status.setCursor(javafx.scene.Cursor.HAND);
                    status.setOnMouseClicked(e -> renderImageThumbnail(container, client, file, filename));
                    return;
                }
                Image img = new Image(new ByteArrayInputStream(bytes), 600, 600, true, true);
                if (img.isError()) {
                    status.setText("Tệp không phải ảnh hợp lệ");
                    return;
                }
                imageView.setImage(img);
                container.getChildren().setAll(imageView);

                // Click to view full image in a clean dialog
                imageView.setOnMouseClicked(e -> showFullImageDialog(filename, img));
            }));
        } else {
            status.setText("Chưa kết nối máy chủ");
        }
    }

    private static void renderFileCard(VBox container, ChatClient client, Attachment file, String filename) {
        String ext = getExtension(filename).toUpperCase(Locale.ROOT);
        if (ext.length() > 5) ext = "FILE";

        Label extLabel = new Label(ext);
        extLabel.setStyle("-fx-background-color: #0068ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 8; -fx-background-radius: 6;");

        Label nameLabel = new Label(filename);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        nameLabel.setMaxWidth(170);

        String sizeText = formatSize(file.size);
        Label sizeLabel = new Label(sizeText);
        sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        VBox textStack = new VBox(2, nameLabel, sizeLabel);
        HBox.setHgrow(textStack, Priority.ALWAYS);

        Button downloadBtn = new Button();
        downloadBtn.setGraphic(AppIcon.create(AppIcon.PATH_DOWNLOAD, 16, javafx.scene.paint.Color.web("#0068ff")));
        downloadBtn.setStyle("-fx-background-color: #e8f1ff; -fx-background-radius: 50%; -fx-min-width: 32px; -fx-max-width: 32px; -fx-min-height: 32px; -fx-max-height: 32px; -fx-padding: 0; -fx-cursor: hand;");

        HBox card = new HBox(10, extLabel, textStack, downloadBtn);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;");

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        statusLabel.setWrapText(true);

        Runnable downloadAction = () -> {
            if (client == null) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Lưu tệp đính kèm");
            chooser.setInitialFileName(filename);
            File target = chooser.showSaveDialog(container.getScene().getWindow());
            if (target == null) return;

            card.setDisable(true);
            statusLabel.setText("Đang tải...");

            new AttachmentTransfer(client).download(file, progress -> {
                Platform.runLater(() -> statusLabel.setText("Đang tải " + Math.round(progress * 100) + "%"));
            }).thenAcceptAsync(bytes -> {
                try {
                    Files.write(target.toPath(), bytes);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }).whenComplete((val, err) -> Platform.runLater(() -> {
                card.setDisable(false);
                if (err == null) {
                    statusLabel.setText("✓ Đã lưu tệp");
                } else {
                    statusLabel.setText("Tải thất bại. Nhấn để thử lại.");
                }
            }));
        };

        card.setOnMouseClicked(e -> downloadAction.run());
        downloadBtn.setOnAction(e -> { e.consume(); downloadAction.run(); });

        container.getChildren().addAll(card, statusLabel);
    }

    private static void showFullImageDialog(String title, Image image) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        ImageView view = new ImageView(image);
        view.setFitWidth(720);
        view.setFitHeight(520);
        view.setPreserveRatio(true);
        dialog.getDialogPane().setContent(view);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
    }

    private static String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx > 0 && idx < filename.length() - 1) {
            return filename.substring(idx + 1);
        }
        return "FILE";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
