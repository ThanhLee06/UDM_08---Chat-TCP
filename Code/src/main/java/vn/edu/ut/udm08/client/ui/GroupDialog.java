package vn.edu.ut.udm08.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.client.ui.components.Toast;
import vn.edu.ut.udm08.shared.dto.*;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GroupDialog {
    private final ChatClient client;
    private final Runnable changed;
    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox content = new VBox(14);
    private final Label status = new Label();
    private final Set<String> selectedUsernames = new HashSet<>();

    public GroupDialog(ChatClient client, Runnable changed) {
        this.client = client;
        this.changed = changed;
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/chat.css").toExternalForm());
        status.setWrapText(true);
        status.getStyleClass().add("profile-status");
        content.setPadding(new Insets(20));
    }

    public void create() {
        dialog.setTitle("Tạo nhóm mới");
        dialog.setHeaderText(null);

        Label nameLabel = new Label("Tên nhóm");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #172b4d;");
        TextField nameField = new TextField();
        nameField.setPromptText("Nhập tên nhóm (tối đa 60 ký tự)");
        nameField.setStyle("-fx-background-radius: 8; -fx-padding: 10 14; -fx-font-size: 13px;");

        Label membersLabel = new Label("Thành viên nhóm");
        membersLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #172b4d;");

        TextField userInputField = new TextField();
        userInputField.setPromptText("Nhập tên tài khoản hoặc SĐT rồi nhấn Enter");
        userInputField.setStyle("-fx-background-radius: 8; -fx-padding: 10 14; -fx-font-size: 13px;");

        FlowPane selectedChipPane = new FlowPane();
        selectedChipPane.setHgap(6);
        selectedChipPane.setVgap(6);
        selectedChipPane.setPadding(new Insets(4, 0, 4, 0));

        Runnable renderChips = () -> {
            selectedChipPane.getChildren().clear();
            for (String uname : selectedUsernames) {
                Label chipLabel = new Label(uname + "  ✕");
                chipLabel.setStyle("-fx-background-color: #e8f1ff; -fx-text-fill: #0068ff; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 10; -fx-background-radius: 12; -fx-cursor: hand;");
                chipLabel.setOnMouseClicked(e -> {
                    selectedUsernames.remove(uname);
                    selectedChipPane.getChildren().remove(chipLabel);
                });
                selectedChipPane.getChildren().add(chipLabel);
            }
        };

        userInputField.setOnAction(e -> {
            String val = userInputField.getText().trim();
            if (!val.isBlank()) {
                for (String part : val.split(",")) {
                    String clean = part.trim();
                    if (!clean.isEmpty()) {
                        selectedUsernames.add(clean);
                    }
                }
                userInputField.clear();
                renderChips.run();
            }
        });

        Label hint = new Label("Nhập tên tài khoản / SĐT và nhấn Enter hoặc dấu phẩy để thêm vào danh sách.");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        hint.setWrapText(true);

        Button createBtn = new Button("Tạo nhóm");
        createBtn.getStyleClass().add("profile-save-btn");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setDefaultButton(true);

        createBtn.setOnAction(event -> {
            String gName = nameField.getText().trim();
            if (gName.isBlank()) {
                status.setText("Vui lòng nhập tên nhóm");
                return;
            }
            // Add any remaining text in userInputField
            String remaining = userInputField.getText().trim();
            if (!remaining.isBlank()) {
                for (String part : remaining.split(",")) {
                    String clean = part.trim();
                    if (!clean.isEmpty()) selectedUsernames.add(clean);
                }
            }
            if (selectedUsernames.isEmpty()) {
                status.setText("Vui lòng chọn hoặc nhập ít nhất 1 thành viên");
                return;
            }

            GroupCommand command = new GroupCommand();
            command.action = "CREATE";
            command.name = gName;
            command.users = new ArrayList<>(selectedUsernames);
            send(command, true);
        });

        content.getChildren().setAll(nameLabel, nameField, new Separator(), membersLabel, userInputField, selectedChipPane, hint, status, createBtn);
        dialog.show();
    }

    public void show(String id) {
        dialog.setTitle("Thông tin nhóm");
        dialog.setHeaderText(null);
        content.getChildren().setAll(status);
        dialog.show();
        GroupCommand command = new GroupCommand();
        command.action = "GET";
        command.convId = id;
        send(command, false);
    }

    private void send(GroupCommand command, boolean close) {
        content.setDisable(true);
        status.setText("Đang xử lý...");
        client.requestFeature(MessageType.GROUP_REQUEST, JsonUtil.toJson(command)).whenComplete((response, error) -> Platform.runLater(() -> {
            content.setDisable(false);
            if (error != null) {
                status.setText(error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                return;
            }
            status.setText("");
            changed.run();
            if (close) {
                dialog.close();
                Toast.showSuccess(dialog.getDialogPane().getScene().getWindow(), "Thao tác thành công");
                return;
            }
            try { render(JsonUtil.fromJson(response.content, GroupDetails.class)); }
            catch (Exception e) { status.setText("Không đọc được thông tin nhóm"); }
        }));
    }

    private void render(GroupDetails details) {
        if (details == null) {
            status.setText("Không đọc được thông tin nhóm");
            return;
        }
        List<GroupDetails.Member> members = details.members != null ? details.members : new ArrayList<>();
        boolean owner = members.stream().anyMatch(m -> m.username != null && m.username.equals(client.getUsername()) && "owner".equals(m.role));

        Label title = new Label(details.name != null ? details.name : "Nhóm trò chuyện");
        title.getStyleClass().add("group-title");
        Label subtitle = new Label(members.size() + " thành viên");
        subtitle.getStyleClass().add("group-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        ListView<GroupDetails.Member> list = new ListView<>();
        list.getItems().setAll(members);
        list.setMinHeight(160);
        list.setPrefHeight(Math.max(160, Math.min(members.size() * 52 + 12, 260)));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(GroupDetails.Member m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setText(null); setGraphic(null); return; }
                var ava = AvatarImages.view(m.avatar, 34);
                Label nameLabel = new Label((m.displayName != null ? m.displayName : m.username) + " (@" + m.username + ")");
                nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                HBox row = new HBox(10, ava, nameLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 8, 6, 8));
                if ("owner".equals(m.role)) {
                    Label badge = new Label("Trưởng nhóm");
                    badge.setStyle("-fx-font-size: 10px; -fx-text-fill: #0068ff; -fx-background-color: #e8f1ff; -fx-padding: 2 8; -fx-background-radius: 10;");
                    row.getChildren().add(badge);
                }
                setGraphic(row);
                setText(null);
                getStyleClass().add("group-member-cell");
            }
        });

        content.getChildren().setAll(titleBox, new Separator(), list, status);

        if (owner) {
            HBox ownerActions = new HBox(8);
            ownerActions.setAlignment(Pos.CENTER_LEFT);

            Button rename = new Button("Đổi tên nhóm");
            rename.getStyleClass().add("group-action-btn");
            rename.setOnAction(e -> prompt(details, "RENAME", "Tên nhóm mới"));

            Button add = new Button("Thêm thành viên");
            add.getStyleClass().add("group-action-btn");
            add.setOnAction(e -> prompt(details, "ADD", "Tên tài khoản hoặc số điện thoại"));

            Button remove = new Button("Xóa thành viên");
            remove.getStyleClass().add("group-danger-btn");
            remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
            remove.setOnAction(e -> {
                var member = list.getSelectionModel().getSelectedItem();
                if (member != null && confirm("Xóa " + (member.displayName != null ? member.displayName : member.username) + " khỏi nhóm?")) {
                    GroupCommand command = new GroupCommand();
                    command.action = "REMOVE";
                    command.convId = details.convId;
                    command.users = java.util.List.of(member.username);
                    send(command, false);
                }
            });

            ownerActions.getChildren().addAll(rename, add, remove);
            content.getChildren().add(ownerActions);
        }

        Button leave = new Button("Rời nhóm");
        leave.getStyleClass().add("group-danger-btn");
        leave.setMaxWidth(Double.MAX_VALUE);
        leave.setOnAction(e -> {
            if (confirm(owner ? "Rời nhóm và tự chuyển quyền trưởng nhóm cho thành viên còn lại?" : "Rời khỏi nhóm này?")) {
                GroupCommand command = new GroupCommand();
                command.action = "LEAVE";
                command.convId = details.convId;
                send(command, true);
            }
        });
        content.getChildren().add(leave);
    }

    private boolean confirm(String text) {
        return vn.edu.ut.udm08.client.ui.components.AppDialog.confirm("Xác nhận", text);
    }

    private void prompt(GroupDetails details, String action, String title) {
        vn.edu.ut.udm08.client.ui.components.AppDialog.prompt("Quản lý nhóm", title, "Nhập thông tin tại đây...").ifPresent(value -> {
            if (!value.isBlank()) {
                GroupCommand command = new GroupCommand();
                command.action = action;
                command.convId = details.convId;
                command.name = value;
                command.users = java.util.List.of(value);
                send(command, false);
            }
        });
    }
}
