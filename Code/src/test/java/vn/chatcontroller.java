package vn.edu.ut.udm08.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.util.List;
import java.util.UUID;

public class ChatController {

    @FXML private ListView<UserProfile> onlineUsersList;
    @FXML private Label chatPartnerName;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;

    private final ObservableList<UserProfile> onlineUsers = FXCollections.observableArrayList();
    private String currentUsername;
    private UserProfile selectedUser;

    // Controller khác (network layer) sẽ gán listener này để gửi message thật qua TCP
    private MessageSendListener sendListener;

    public interface MessageSendListener {
        void onSendMessage(ProtocolMessage message);
    }

    @FXML
    public void initialize() {
        onlineUsersList.setItems(onlineUsers);
        onlineUsersList.setCellFactory(list -> new UserListCell());
        onlineUsersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectUser(newVal);
            }
        });
        sendButton.setDisable(true);
        messageInput.setDisable(true);
        messageInput.setOnAction(e -> handleSend());
    }

    // Gọi hàm này từ tầng network khi nhận currentUsername sau khi đăng nhập
    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public void setSendListener(MessageSendListener listener) {
        this.sendListener = listener;
    }

    // Gọi hàm này khi nhận được USER_LIST từ server
    public void updateOnlineUsers(List<UserProfile> users) {
        Platform.runLater(() -> {
            onlineUsers.setAll(users);
        });
    }

    // Gọi hàm này khi nhận được CHAT message từ server (tin đến)
    public void receiveMessage(ProtocolMessage message) {
        Platform.runLater(() -> {
            boolean isMine = message.sender != null && message.sender.equals(currentUsername);
            boolean belongsToCurrentChat = selectedUser != null &&
                    (message.sender.equals(selectedUser.username) || isMine);
            if (belongsToCurrentChat) {
                addMessageBubble(message.content, isMine);
            }
        });
    }

    private void selectUser(UserProfile user) {
        this.selectedUser = user;
        chatPartnerName.setText(user.username);
        messageContainer.getChildren().clear();
        sendButton.setDisable(false);
        messageInput.setDisable(false);
    }

    @FXML
    private void handleSend() {
        String content = messageInput.getText().trim();
        if (content.isEmpty() || selectedUser == null) return;

        ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
        message.messageId = UUID.randomUUID().toString();
        message.sender = currentUsername;
        message.target = selectedUser.username;
        message.content = content;
        message.timestamp = System.currentTimeMillis();

        addMessageBubble(content, true);
        messageInput.clear();

        if (sendListener != null) {
            sendListener.onSendMessage(message);
        }
    }

    private void addMessageBubble(String content, boolean isMine) {
        Label bubble = new Label(content);
        bubble.getStyleClass().add(isMine ? "bubble-mine" : "bubble-other");
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);

        HBox row = new HBox(bubble);
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageContainer.getChildren().add(row);

        messageScrollPane.layout();
        messageScrollPane.setVvalue(1.0);
    }

    private static class UserListCell extends ListCell<UserProfile> {
        @Override
        protected void updateItem(UserProfile user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setText(null);
            } else {
                setText(user.username);
            }
        }
    }
}