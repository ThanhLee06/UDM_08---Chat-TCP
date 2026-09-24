package vn.edu.ut.udm08.client.ui;

import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.network.OpenDmCallback;
import vn.edu.ut.udm08.client.network.OpenDmResult;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.UserProfile;

public final class PhoneLookupDialog {

    public static void show(ChatClient client, Consumer<ConversationSummary> opened) {
        showWithQuery(client, "", opened);
    }

    public static void showWithQuery(ChatClient client, String initialQuery, Consumer<ConversationSummary> opened) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Tìm bạn qua số điện thoại");
        if (PhoneLookupDialog.class.getResource("/css/chat.css") != null) {
            dialog.getDialogPane().getStylesheets().add(PhoneLookupDialog.class.getResource("/css/chat.css").toExternalForm());
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label titleLabel = new Label("Tìm bạn bằng số điện thoại");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #172b4d;");

        TextField phoneField = new TextField(initialQuery != null ? initialQuery.trim() : "");
        phoneField.setPromptText("Nhập số điện thoại (ví dụ: 0901234567)");
        phoneField.getStyleClass().add("sidebar-search-input");
        phoneField.setStyle("-fx-padding: 10 14; -fx-font-size: 13px; -fx-background-radius: 10; -fx-background-color: #f1f5f9;");

        Button findButton = new Button("Tìm kiếm");
        findButton.getStyleClass().add("profile-save-btn");
        findButton.setStyle("-fx-padding: 8 18; -fx-font-size: 13px;");

        HBox inputRow = new HBox(10, phoneField, findButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        phoneField.setPrefWidth(260);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        Button chatButton = new Button("Nhắn tin");
        chatButton.getStyleClass().add("profile-save-btn");
        chatButton.setVisible(false);
        chatButton.setManaged(false);

        VBox resultBox = new VBox(10, statusLabel, chatButton);
        resultBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(14, titleLabel, inputRow, resultBox);
        root.setStyle("-fx-padding: 20; -fx-background-color: white;");
        dialog.getDialogPane().setContent(root);

        Runnable searchTask = () -> {
            String phoneText = phoneField.getText() == null ? "" : phoneField.getText().trim();
            if (phoneText.isBlank()) {
                statusLabel.setText("Vui lòng nhập số điện thoại.");
                return;
            }
            findButton.setDisable(true);
            chatButton.setVisible(false);
            chatButton.setManaged(false);
            statusLabel.setGraphic(null);
            statusLabel.setText("Đang tìm kiếm…");

            client.requestFeature(MessageType.PHONE_LOOKUP, phoneText).whenComplete((response, error) -> Platform.runLater(() -> {
                findButton.setDisable(false);
                if (error != null) {
                    statusLabel.setText(error.getMessage());
                    return;
                }
                if (response.users == null || response.users.isEmpty()) {
                    statusLabel.setText("Không tìm thấy tài khoản tương ứng với số điện thoại này.");
                    return;
                }
                UserProfile user = response.users.get(0);
                statusLabel.setText(user.displayName + " (@" + user.username + ")");
                statusLabel.setGraphic(AvatarImages.view(user.avatarId, 44));
                chatButton.setVisible(true);
                chatButton.setManaged(true);
                chatButton.setOnAction(event -> {
                    chatButton.setDisable(true);
                    try {
                        client.openDirectMessage(user.username, new OpenDmCallback() {
                            @Override
                            public void onSuccess(OpenDmResult result) {
                                Platform.runLater(() -> {
                                    opened.accept(result.getConversation());
                                    dialog.close();
                                });
                            }

                            @Override
                            public void onFailure(String requestId, String code, String message) {
                                Platform.runLater(() -> {
                                    chatButton.setDisable(false);
                                    statusLabel.setText(message);
                                });
                            }
                        });
                    } catch (Exception e) {
                        chatButton.setDisable(false);
                        statusLabel.setText("Không thể mở cuộc trò chuyện. Vui lòng kết nối lại.");
                    }
                });
            }));
        };

        findButton.setOnAction(e -> searchTask.run());
        phoneField.setOnAction(e -> {
            if (!findButton.isDisabled()) searchTask.run();
        });
        dialog.show();
        if (!phoneField.getText().isBlank()) {
            searchTask.run();
        }
    }
}
