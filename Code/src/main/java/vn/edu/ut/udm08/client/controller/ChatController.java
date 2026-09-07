package vn.edu.ut.udm08.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.UUID;

public class ChatController {

    @FXML private ListView<UserProfile> onlineUsersList;
    @FXML private Label chatPartnerName;
    @FXML private Label chatPartnerInitial;
    @FXML private StackPane chatPartnerAvatar;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;   
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private VBox emptyStatePane;    

    private final ObservableList<UserProfile> onlineUsers = FXCollections.observableArrayList();

    private String currentUsername;
    private UserProfile selectedUser; 
    //luu lai tin nhan duoc chon de reply
    private ProtocolMessage replyingToMessage;
    //thanh quote hien dang hien thi 
    private HBox replyBar;
    //luu tam lich su tin nhan
     private final java.util.Map<String, ProtocolMessage> messageHistory = new java.util.HashMap<>();
     private final java.util.Map<String, javafx.scene.Node> messageNodeIndex = new java.util.HashMap<>();

    private static final String[] AVATAR_COLORS = {
            "#0068ff", "#00c853", "#ff6d00", "#e91e63",
            "#9c27b0", "#00acc1", "#f4511e", "#5e35b1"
    };

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
        messageInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && replyingToMessage != null) {
                cancelReply();
            }
        });

        emptyStatePane.setVisible(true);
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public void setSendListener(MessageSendListener listener) {
        this.sendListener = listener;
    }

    public void updateOnlineUsers(List<UserProfile> users) {
        Platform.runLater(() -> {
            if (users == null) {
                onlineUsers.clear();
                return;
            }
            List<UserProfile> otherUsers = users.stream()
                    .filter(u -> u != null && u.username != null && !u.username.equalsIgnoreCase(currentUsername))
                    .toList();
            onlineUsers.setAll(otherUsers);
        });
    }

    public void receiveMessage(ProtocolMessage message) {
        Platform.runLater(() -> {
            if (message == null) {
                return;
            }

            boolean isMine = message.sender != null && message.sender.equals(currentUsername);
            boolean belongsToCurrentChat = selectedUser != null && message.sender != null && (message.sender.equals(selectedUser.username) || isMine);

            if (belongsToCurrentChat) {
                addMessageBubble(message, isMine);
            }
        });
    }

    private void selectUser(UserProfile user) {
        this.selectedUser = user;
         cancelReply();

        chatPartnerName.setText(user.username);
        chatPartnerInitial.setText(user.username.substring(0, 1).toUpperCase());
        chatPartnerAvatar.setStyle("-fx-background-color: " + avatarColorFor(user.username) + ";");

        messageContainer.getChildren().clear();
        messageNodeIndex.clear();

        sendButton.setDisable(false);
        messageInput.setDisable(false);
        emptyStatePane.setVisible(false);
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
        if (replyingToMessage != null) {
        message.replyToMessageId = replyingToMessage.messageId;
        message.replyToSender = replyingToMessage.sender;
        message.replyToContent = replyingToMessage.content;
        }       
        addMessageBubble(message, true);
        messageInput.clear(); 
        cancelReply();

        if (sendListener != null) {
            sendListener.onSendMessage(message);
        }
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Tạo 1 bubble tin nhắn (kèm avatar, tên, timestamp) và thêm vào khung chat
       private void addMessageBubble(ProtocolMessage message, boolean isMine) {
         if (message.messageId != null) {
            messageHistory.put(message.messageId, message);
        }
        Label contentLabel = new Label(message.content);
        contentLabel.getStyleClass().add("message-bubble-text");
        contentLabel.setWrapText(true);
         VBox bubble = new VBox(4);
        bubble.getStyleClass().add(isMine ? "message-bubble-sent" : "message-bubble-received");
        bubble.setMaxWidth(400);
        bubble.setUserData(message);
         if (message.replyToMessageId != null) {
            ProtocolMessage original = messageHistory.get(message.replyToMessageId);
            String quoteText;
           if (original != null)
             {
               quoteText = original.sender + ": " + original.content;
            } 
           else if (message.replyToSender != null && message.replyToContent != null) 
            {
               quoteText = message.replyToSender + ": " + message.replyToContent;
           } else 
            {
               quoteText = "Tin nhắn gốc không khả dụng";
         }
         Label quoteBlock = new Label(quoteText);
            quoteBlock.getStyleClass().add("reply-quote-block");
            quoteBlock.setWrapText(true);
            quoteBlock.setStyle("-fx-cursor: hand;");
            final String targetId = message.replyToMessageId;
        quoteBlock.setOnMouseClicked(e -> {
        boolean found = scrollToMessage(targetId);
        if (!found) {
            showMessageNotFoundHint(quoteBlock);
        }
    });
            bubble.getChildren().add(quoteBlock);
        }
        
        ContextMenu contextMenu = new ContextMenu();
        MenuItem replyItem = new MenuItem("Trả lời");
        replyItem.setOnAction(e -> startReply(message));
       MenuItem forwardItem = new MenuItem("Chuyển tiếp");
        forwardItem.setOnAction(e -> openForwardDialog(message));
        contextMenu.getItems().addAll(replyItem, forwardItem);
        bubble.setOnContextMenuRequested(e ->
                contextMenu.show(bubble, e.getScreenX(), e.getScreenY())
        );

        String timeText = Instant.ofEpochMilli(message.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMAT);
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("message-timestamp");

        VBox column = new VBox(3);
        column.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox row = new HBox(8);
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (isMine) {
            column.getChildren().addAll(bubble, timeLabel);
            row.getChildren().add(column);
        } else {
            Label initial = new Label(message.sender.substring(0, 1).toUpperCase());
            initial.getStyleClass().add("avatar-text-small");

            StackPane avatar = new StackPane(initial);
            avatar.getStyleClass().add("avatar-circle-small");
            avatar.setStyle("-fx-background-color: " + avatarColorFor(message.sender) + ";");

            Label nameLabel = new Label(message.sender);
            nameLabel.getStyleClass().add("message-sender-name");

            column.getChildren().addAll(nameLabel, bubble, timeLabel);
            row.getChildren().addAll(avatar, column);
        }

        messageContainer.getChildren().add(row);
         if (message.messageId != null) {
        messageNodeIndex.put(message.messageId, row);
    }

        messageScrollPane.layout();
        messageScrollPane.setVvalue(1.0);
    }
     private void startReply(ProtocolMessage message) {
        this.replyingToMessage = message;
         showReplyBar(message);
    }
private void openForwardDialog(ProtocolMessage message) {
    Dialog<UserProfile> dialog = new Dialog<>();
    dialog.setTitle("Chuyển tiếp tin nhắn");
    dialog.setHeaderText("Chọn người nhận để chuyển tiếp:");

    ButtonType forwardButtonType = new ButtonType("Chuyển tiếp", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(forwardButtonType, ButtonType.CANCEL);

    TextField searchField = new TextField();
    searchField.setPromptText("Tìm người nhận...");
    searchField.getStyleClass().add("forward-search-field");
    

    ObservableList<UserProfile> forwardTargets = FXCollections.observableArrayList(onlineUsers);
    ListView<UserProfile> targetListView = new ListView<>(forwardTargets);
    targetListView.setCellFactory(list -> new UserListCell());
    targetListView.setPrefHeight(220);
    targetListView.setPlaceholder(new Label("Không tìm thấy người dùng"));

    searchField.textProperty().addListener((obs, oldVal, newVal) -> {
        String keyword = newVal == null ? "" : newVal.trim().toLowerCase();
        forwardTargets.setAll(onlineUsers.stream()
                .filter(u -> u.username.toLowerCase().contains(keyword))
                .toList());
    });

    VBox content = new VBox(8, searchField, targetListView);
    dialog.getDialogPane().setContent(content);
    content.getStyleClass().add("forward-dialog-content");

    Node forwardButtonNode = dialog.getDialogPane().lookupButton(forwardButtonType);
    forwardButtonNode.setDisable(true);
    targetListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
            forwardButtonNode.setDisable(newVal == null));

    dialog.setResultConverter(buttonType -> {
        if (buttonType == forwardButtonType) {
            return targetListView.getSelectionModel().getSelectedItem();
        }
        return null;
    });
    dialog.setOnShown(e -> searchField.requestFocus());
    dialog.showAndWait().ifPresent(target -> forwardMessage(message, target));
    
}

private void forwardMessage(ProtocolMessage original, UserProfile target) {
    ProtocolMessage forwarded = new ProtocolMessage(MessageType.CHAT);
    forwarded.messageId = UUID.randomUUID().toString();
    forwarded.sender = currentUsername;
    forwarded.target = target.username;
    forwarded.content = original.content;
    forwarded.timestamp = System.currentTimeMillis();
    forwarded.forwardedFromSender = original.sender;
    forwarded.isForwarded = true;

    if (sendListener != null) {
        sendListener.onSendMessage(forwarded);
    }

    if (selectedUser != null && selectedUser.username.equals(target.username)) {
        addMessageBubble(forwarded, true);
    }
}

    private void showReplyBar(ProtocolMessage message) {
        removeReplyBar();

        Label replyingToLabel = new Label("Đang trả lời " + message.sender);
        replyingToLabel.getStyleClass().add("reply-bar-sender");

        Label quoteText = new Label(message.content);
        quoteText.getStyleClass().add("reply-bar-text");

        VBox quoteInfo = new VBox(2, replyingToLabel, quoteText);

        Button cancelButton = new Button("✕");
        cancelButton.getStyleClass().add("reply-cancel-button");
        cancelButton.setOnAction(e -> cancelReply());

        replyBar = new HBox(10, quoteInfo, cancelButton);
        replyBar.getStyleClass().add("reply-bar");
        HBox.setHgrow(quoteInfo, Priority.ALWAYS);

        Parent inputRow = messageInput.getParent();
        if (inputRow != null && inputRow.getParent() instanceof VBox rootBox) {
            int index = rootBox.getChildren().indexOf(inputRow);
            if (index >= 0) {
                rootBox.getChildren().add(index, replyBar);
            }
        }
    }

    // ST-082: cuon toi tin nhan goc va highlight tam thoi de nguoi dung de nhan biet
private boolean scrollToMessage(String messageId) {
    if (messageId == null) return false;

    javafx.scene.Node target = messageNodeIndex.get(messageId);
    if (target == null) {
        return false;
    }

    messageScrollPane.layout();
    messageContainer.layout();

    double contentHeight = messageContainer.getHeight() - messageScrollPane.getViewportBounds().getHeight();
    if (contentHeight > 0) {
        double targetY = target.getBoundsInParent().getMinY();
        double vValue = targetY / contentHeight;
        messageScrollPane.setVvalue(Math.max(0, Math.min(1, vValue)));
    }

    highlightNode(target);
    return true;
}


private void highlightNode(javafx.scene.Node target) {
    if (!(target instanceof HBox row)) return;

    row.getStyleClass().add("message-highlight");
    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.2));
    pause.setOnFinished(e -> row.getStyleClass().remove("message-highlight"));
    pause.play();
}
    private void cancelReply() {
        replyingToMessage = null;
        removeReplyBar();
    }
    private void removeReplyBar() {
        if (replyBar != null && replyBar.getParent() instanceof VBox parentBox) {
            parentBox.getChildren().remove(replyBar);
        }
        replyBar = null;
    }

    private static String avatarColorFor(String username) {
        int index = Math.abs(username.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    private static class UserListCell extends ListCell<UserProfile> {
        @Override
        protected void updateItem(UserProfile user, boolean empty) {
            super.updateItem(user, empty);

            if (empty || user == null) {
                setText(null);
                setGraphic(null);
            } else {
                Label initial = new Label(user.username.substring(0, 1).toUpperCase());
                initial.getStyleClass().add("avatar-text-small");

                StackPane avatar = new StackPane(initial);
                avatar.getStyleClass().add("avatar-circle-small");
                avatar.setStyle("-fx-background-color: " + avatarColorFor(user.username) + ";");

                Label name = new Label(user.username);
                name.getStyleClass().add("cell-name");

                HBox box = new HBox(10, avatar, name);
                box.setAlignment(Pos.CENTER_LEFT);

                setGraphic(box); 
                setText(null);
            }
        }
    }
    private void showMessageNotFoundHint(javafx.scene.Node anchor) {
    Tooltip hint = new Tooltip("Tin nhắn gốc không còn tồn tại hoặc đã bị xóa");
    hint.getStyleClass().add("reply-not-found-hint");
    hint.setAutoHide(true);

    javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
    if (bounds != null) {
        hint.show(anchor, bounds.getMinX(), bounds.getMaxY() + 4);
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> hint.hide());
        pause.play();
    }
}
}