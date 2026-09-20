package vn.edu.ut.udm08.client.controller;
import vn.edu.ut.udm08.client.ui.sidebar.SidebarController;
import vn.edu.ut.udm08.client.ui.sidebar.SidebarConversation;
import vn.edu.ut.udm08.client.ui.sidebar.IConversationSource;
import vn.edu.ut.udm08.shared.protocol.ConvId;
import vn.edu.ut.udm08.shared.protocol.ConvType;

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
import javafx.scene.layout.FlowPane;
import javafx.stage.Popup;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.UUID;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ChatController {

    @FXML private SidebarController sidebarController;
    @FXML private Label chatPartnerName;
    @FXML private Label chatPartnerInitial;
    @FXML private StackPane chatPartnerAvatar;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;   
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private VBox emptyStatePane;    
    @FXML private Label emojiIcon;

    private final ObservableList<UserProfile> onlineUsers = FXCollections.observableArrayList();

    private String currentUsername;
    private UserProfile selectedUser; 
    private ProtocolMessage replyingToMessage;
    private HBox replyBar;
    private final java.util.Map<String, ProtocolMessage> messageHistory = new java.util.HashMap<>();
    private final java.util.Map<String, javafx.scene.Node> messageNodeIndex = new java.util.HashMap<>();

    private Long nextCursor;
    private boolean hasMoreHistory;
    private boolean isLoadingHistory;

    private static final String[] AVATAR_COLORS = {
            "#0068ff", "#00c853", "#ff6d00", "#e91e63",
            "#9c27b0", "#00acc1", "#f4511e", "#5e35b1"
    };

    private MessageSendListener sendListener;

    public interface MessageSendListener {
        void onSendMessage(ProtocolMessage message);
    }

    private String selectedConvId;

    @FXML
    public void initialize() {
        sidebarController.setLogoutListener(this::handleLogout);
        sidebarController.setSelectionListener(conversation -> {
            if (conversation.getType() == ConvType.PUBLIC) {
                selectPublicRoom();
            } else if (conversation.getType() == ConvType.GROUP) {
                selectGroupRoom(conversation);
            } else {
                String other = ConvId.getOtherUser(conversation.getId(), currentUsername);
                if (other == null) {
                    other = conversation.getName();
                }
                this.selectedConvId = conversation.getId();
                selectUser(new UserProfile(other, conversation.getAvatar()));
                chatPartnerName.setText(conversation.getName());
            }
        });

        messageInput.setOnAction(e -> handleSend());
        messageInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && replyingToMessage != null) {
                cancelReply();
            }
        });

        messageScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.doubleValue() <= 0.05 && hasMoreHistory && !isLoadingHistory) {
                loadMoreHistory();
            }
        });

        showEmptyState();
    }
    public void handleLogout() {
        if (sidebarController != null && sidebarController.getClient() != null) {
            try {
                sidebarController.getClient().logout();
            } catch (Exception ignored) {
            }
        }
        if (sidebarController != null) {
            sidebarController.dispose();
        }
        Platform.runLater(() -> {
            try {
                Stage stage = (Stage) messageInput.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
                Scene loginScene = new Scene(loader.load());
                stage.setTitle("UDM08 Chat - Đăng nhập");
                stage.setScene(loginScene);
                stage.setResizable(false);
            } catch (Exception ignored) {
            }
        });
    }

    public void showEmptyState() {
        this.selectedUser = null;
        this.selectedConvId = null;
        sendButton.setDisable(true);
        messageInput.setDisable(true);
        messageContainer.getChildren().clear();
        messageInput.clear();
        emptyStatePane.setVisible(true);
    }

    public void selectPublicRoom() {
        this.selectedUser = null;
        this.selectedConvId = ConvId.PUBLIC_ROOM_ID;
        if (sidebarController != null) {
            sidebarController.markAsRead(selectedConvId);
        }
        chatPartnerName.setText("Phòng chung");
        chatPartnerInitial.setText("#");
        chatPartnerAvatar.setStyle("-fx-background-color: #0068ff;");
        messageContainer.getChildren().clear();
        messageNodeIndex.clear();
        messageInput.clear();
        messageInput.setDisable(false);
        sendButton.setDisable(false);
        emptyStatePane.setVisible(false);
        loadInitialHistory();
    }

    public void selectGroupRoom(SidebarConversation group) {
        this.selectedUser = null;
        this.selectedConvId = group.getId();
        cancelReply();
        if (sidebarController != null) {
            sidebarController.markAsRead(selectedConvId);
        }
        chatPartnerName.setText(group.getName());
        chatPartnerInitial.setText(group.getName() != null && !group.getName().isBlank() ? group.getName().substring(0, 1).toUpperCase() : "#");
        chatPartnerAvatar.setStyle("-fx-background-color: #5e35b1;");
        messageContainer.getChildren().clear();
        messageNodeIndex.clear();
        messageInput.setDisable(false);
        sendButton.setDisable(false);
        emptyStatePane.setVisible(false);
        loadInitialHistory();
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public void loadSidebar(IConversationSource source) {
        sidebarController.configure(source, currentUsername);
        showEmptyState();
    }
    public void disposeSidebar() {
        sidebarController.dispose();
    }
    public void setSendListener(MessageSendListener listener) {
        this.sendListener = listener;
    }

    private void loadInitialHistory() {
        String convId = selectedConvId;
        if (convId == null || sidebarController == null) {
            return;
        }
        vn.edu.ut.udm08.client.network.ChatClient client = sidebarController.getClient();
        if (client == null || !client.isConnected()) {
            return;
        }
        nextCursor = null;
        hasMoreHistory = false;
        isLoadingHistory = true;
        try {
            client.requestMessageHistory(convId, 30, null, new vn.edu.ut.udm08.client.network.MessageHistoryCallback() {
                @Override
                public void onSuccess(vn.edu.ut.udm08.client.network.MessageHistoryPage page) {
                    Platform.runLater(() -> {
                        isLoadingHistory = false;
                        if (!convId.equals(selectedConvId)) {
                            return;
                        }
                        if (page != null && page.getMessages() != null) {
                            for (ProtocolMessage message : page.getMessages()) {
                                boolean isMine = currentUsername != null && currentUsername.equals(message.sender);
                                addMessageBubble(message, isMine);
                            }
                            if (page.getNextCursor() != null && !page.getNextCursor().isBlank()) {
                                try {
                                    nextCursor = Long.parseLong(page.getNextCursor());
                                } catch (Exception e) {
                                    nextCursor = null;
                                }
                            } else {
                                nextCursor = null;
                            }
                            hasMoreHistory = page.hasMore();
                        }
                    });
                }

                @Override
                public void onFailure(String requestId, String errorCode, String errorMessage) {
                    Platform.runLater(() -> isLoadingHistory = false);
                }
            });
        } catch (Exception ignored) {
            isLoadingHistory = false;
        }
    }

    private void loadMoreHistory() {
        String convId = selectedConvId;
        if (convId == null || !hasMoreHistory || isLoadingHistory || nextCursor == null || sidebarController == null) {
            return;
        }
        vn.edu.ut.udm08.client.network.ChatClient client = sidebarController.getClient();
        if (client == null || !client.isConnected()) {
            return;
        }
        isLoadingHistory = true;
        try {
            client.requestMessageHistory(convId, 30, String.valueOf(nextCursor), new vn.edu.ut.udm08.client.network.MessageHistoryCallback() {
                @Override
                public void onSuccess(vn.edu.ut.udm08.client.network.MessageHistoryPage page) {
                    Platform.runLater(() -> {
                        isLoadingHistory = false;
                        if (!convId.equals(selectedConvId)) {
                            return;
                        }
                        if (page != null && page.getMessages() != null) {
                            double oldHeight = messageContainer.getHeight();
                            List<ProtocolMessage> olderMessages = page.getMessages();
                            for (int i = olderMessages.size() - 1; i >= 0; i--) {
                                ProtocolMessage msg = olderMessages.get(i);
                                boolean isMine = currentUsername != null && currentUsername.equals(msg.sender);
                                prependMessageBubble(msg, isMine);
                            }
                            if (page.getNextCursor() != null && !page.getNextCursor().isBlank()) {
                                try {
                                    nextCursor = Long.parseLong(page.getNextCursor());
                                } catch (Exception e) {
                                    nextCursor = null;
                                }
                            } else {
                                nextCursor = null;
                            }
                            hasMoreHistory = page.hasMore();
                            messageScrollPane.layout();
                            messageContainer.layout();
                            double newHeight = messageContainer.getHeight();
                            if (newHeight > oldHeight && newHeight > 0) {
                                messageScrollPane.setVvalue((newHeight - oldHeight) / newHeight);
                            }
                        }
                    });
                }

                @Override
                public void onFailure(String requestId, String errorCode, String errorMessage) {
                    Platform.runLater(() -> isLoadingHistory = false);
                }
            });
        } catch (Exception ignored) {
            isLoadingHistory = false;
        }
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
            if (sidebarController != null) {
                sidebarController.updateOnlineUsers(users);
            }
        });
    }

    public static String formatMessagePreview(ProtocolMessage message, boolean isMine) {
        if (message == null || message.content == null || message.content.isBlank()) {
            return "";
        }
        String content = message.content.trim();
        String prefix = isMine ? "Bạn: " : "";
        String lower = content.toLowerCase();

        if (message.isForwarded) {
            return prefix + "[Chuyển tiếp] " + content;
        }
        if (content.startsWith("[IMAGE]") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            return prefix + "[Hình ảnh]";
        }
        if (content.startsWith("[VIDEO]") || lower.endsWith(".mp4") || lower.endsWith(".mkv")) {
            return prefix + "[Video]";
        }
        if (content.startsWith("[FILE]")) {
            String name = content.length() > 6 ? content.substring(6).trim() : "Tập tin";
            return prefix + "[File] " + name;
        }
        if (content.startsWith("[STICKER]")) {
            return prefix + "[Sticker]";
        }
        if (content.startsWith("[CALL]")) {
            return "Cuộc gọi thoại";
        }
        if (content.startsWith("[SYSTEM]") || content.startsWith("[REVOKED]")) {
            return "Tin nhắn đã được thu hồi";
        }
        return prefix + content;
    }

    public void receiveMessage(ProtocolMessage message) {
        Platform.runLater(() -> {
            if (message == null) {
                return;
            }

            boolean isMine = message.sender != null && message.sender.equals(currentUsername);
            String convId = message.convId;
            if (convId == null || convId.isBlank()) {
                if (message.target != null && message.target.equalsIgnoreCase("PUBLIC")) {
                    convId = ConvId.PUBLIC_ROOM_ID;
                } else if (message.sender != null) {
                    convId = ConvId.forDm(currentUsername, message.sender);
                }
            }

            boolean belongsToCurrentChat = false;
            if (ConvId.isPublicRoom(selectedConvId)) {
                belongsToCurrentChat = ConvId.isPublicRoom(convId) || "PUBLIC".equalsIgnoreCase(message.target);
            } else if (selectedConvId != null && !selectedConvId.isBlank()) {
                belongsToCurrentChat = selectedConvId.equalsIgnoreCase(convId);
            }

            if (convId != null && sidebarController != null) {
                if (!ConvId.isPublicRoom(convId)) {
                    String other = isMine ? message.target : message.sender;
                    if (other != null && !other.isBlank()) {
                        sidebarController.ensureConversation(convId, other, "avatar1");
                    }
                }
                long ts = message.timestamp != 0 ? message.timestamp : System.currentTimeMillis();
                String snippet = formatMessagePreview(message, isMine);
                boolean incrementUnread = !isMine && !belongsToCurrentChat;
                sidebarController.updateLastMessage(convId, snippet, ts, incrementUnread);
            }

            if (belongsToCurrentChat) {
                addMessageBubble(message, isMine);
            }
        });
    }

    private void selectUser(UserProfile user) {
        this.selectedUser = user;
        if (currentUsername != null && user != null && user.username != null) {
            this.selectedConvId = ConvId.forDm(currentUsername, user.username);
        }
        cancelReply();
        if (sidebarController != null && selectedConvId != null) {
            sidebarController.markAsRead(selectedConvId);
        }

        chatPartnerName.setText(user.username);
        chatPartnerInitial.setText(user.username.substring(0, 1).toUpperCase());
        chatPartnerAvatar.setStyle("-fx-background-color: " + avatarColorFor(user.username) + ";");

        messageContainer.getChildren().clear();
        messageNodeIndex.clear();

        sendButton.setDisable(false);
        messageInput.setDisable(false);
        emptyStatePane.setVisible(false);
        loadInitialHistory();
    }

    @FXML
    private void handleSend() {
        String content = messageInput.getText().trim();
        if (content.isEmpty() || (selectedUser == null && (selectedConvId == null || selectedConvId.isBlank()))) return; 

        ProtocolMessage message = new ProtocolMessage(MessageType.CHAT);
        message.messageId = UUID.randomUUID().toString();   
        message.sender = currentUsername;                    
        if (selectedConvId != null && !selectedConvId.isBlank()) {
            message.convId = selectedConvId;
            if (ConvId.isPublicRoom(selectedConvId)) {
                message.target = "PUBLIC";
            } else if (ConvId.isDm(selectedConvId)) {
                String other = ConvId.getOtherUser(selectedConvId, currentUsername);
                if (other != null) {
                    message.target = other;
                } else if (selectedUser != null) {
                    message.target = selectedUser.username;
                }
            } else {
                message.target = selectedConvId;
            }
        } else if (selectedUser != null) {
            message.convId = ConvId.forDm(currentUsername, selectedUser.username);
            message.target = selectedUser.username;
        }
        message.content = content;                           
        message.timestamp = System.currentTimeMillis(); 
        if (replyingToMessage != null) {
            message.replyToMessageId = replyingToMessage.messageId;
            message.replyToSender = replyingToMessage.sender;
            message.replyToContent = replyingToMessage.content;
        }       
        addMessageBubble(message, true);
        if (sidebarController != null && message.convId != null) {
            sidebarController.updateLastMessage(message.convId, formatMessagePreview(message, true), message.timestamp, false);
        }
        messageInput.clear(); 
        cancelReply();

        if (sendListener != null) {
            sendListener.onSendMessage(message);
        }
    }
    private static final Map<String, String[]> EMOJI_GROUPS = new LinkedHashMap<>();
static {
    EMOJI_GROUPS.put("Mặt cười", new String[]{
        "😀","😁","😂","🤣","😊","😍","😘","😜","🤔","😎","😢","😭","😡","😱","🥳","🙄"
    });
    EMOJI_GROUPS.put("Cử chỉ", new String[]{
        "👍","👎","👏","🙏","💪","👌","✌️","🤝","👋","🤟","🫶","🖐️"
    });
    EMOJI_GROUPS.put("Động vật", new String[]{
        "🐶","🐱","🐭","🐰","🦊","🐻","🐼","🐸","🐵","🦁","🐷","🐔"
    });
    EMOJI_GROUPS.put("Đồ ăn", new String[]{
        "🍎","🍕","🍔","🍟","🍩","🍰","☕","🍺","🍜","🍣","🍫","🥗"
    });
    EMOJI_GROUPS.put("Trái tim", new String[]{
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","💕","💖","💔","❤️‍🔥"
    });
}

    @FXML
    private void handleEmojiButtonClick() {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    TabPane tabPane = new TabPane();
    tabPane.setPrefSize(320, 260);  
    tabPane.getStyleClass().add("emoji-tab-pane");
    tabPane.getStylesheets().addAll(emojiIcon.getScene().getStylesheets());
    tabPane.getStylesheets().addAll(emojiIcon.getScene().getRoot().getStylesheets());

    for (Map.Entry<String, String[]> group : EMOJI_GROUPS.entrySet()) {
        FlowPane flowPane = new FlowPane(6, 6);
        flowPane.setPrefWrapLength(300);
        flowPane.getStyleClass().add("emoji-flow-pane");
        flowPane.setStyle("-fx-padding: 8; -fx-background-color: white;");

        for (String emoji : group.getValue()) {
           flowPane.getChildren().add(createEmojiButton(emoji));
        }

        ScrollPane scrollPane = new ScrollPane(flowPane);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("emoji-scroll-pane");
        scrollPane.setStyle("-fx-background: white; -fx-background-color: white;");


        Tab tab = new Tab(group.getKey(), scrollPane);
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
    }

    popup.getContent().add(tabPane);

    javafx.geometry.Bounds bounds = emojiIcon.localToScreen(emojiIcon.getBoundsInLocal());
    popup.show(emojiIcon, bounds.getMinX(), bounds.getMinY() - 270);
}

private Button createEmojiButton(String emoji) {
    Button btn = new Button();
    btn.getStyleClass().add("emoji-item-button");
    btn.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");

    String full = emoji.codePoints()
            .mapToObj(Integer::toHexString)
            .collect(java.util.stream.Collectors.joining("-"));
    String noFe0f = full.replace("-fe0f", "");

    java.io.InputStream stream = getClass().getResourceAsStream("/emoji/" + full + ".png");
    if (stream == null) {
        stream = getClass().getResourceAsStream("/emoji/" + noFe0f + ".png");
    }

    if (stream != null) {
        btn.setGraphic(new javafx.scene.image.ImageView(
                new javafx.scene.image.Image(stream, 24, 24, true, true)));
    } else {
        btn.setText(emoji);
        btn.setStyle(btn.getStyle() + " -fx-font-size: 18px;"); 
    }

    btn.setOnAction(e -> insertEmojiAtCaret(emoji));
    return btn;
}


private void insertEmojiAtCaret(String emoji) {
    int caretPos = messageInput.getCaretPosition();
    messageInput.insertText(caretPos, emoji);
    messageInput.positionCaret(caretPos + emoji.length());
    messageInput.requestFocus();
}


    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private HBox createMessageRow(ProtocolMessage message, boolean isMine) {
        if (message.messageId != null) {
            messageHistory.put(message.messageId, message);
        }
        Label contentLabel = new Label(message.content != null ? message.content : "");
        contentLabel.getStyleClass().add("message-bubble-text");
        contentLabel.setWrapText(true);
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add(isMine ? "message-bubble-sent" : "message-bubble-received");
        bubble.setMaxWidth(400);
        bubble.setUserData(message);
        if ((message.isForwarded || "forward".equalsIgnoreCase(message.kind)) && message.forwardedFromSender != null) {
            Label forwardedLabel = new Label("↪ Đã chuyển tiếp từ " + message.forwardedFromSender);
            forwardedLabel.getStyleClass().add("forwarded-label");
            bubble.getChildren().add(forwardedLabel);
        }
        if (message.replyToMessageId != null) {
            ProtocolMessage original = messageHistory.get(message.replyToMessageId);
            String quoteText;
            if (original != null) {
                quoteText = original.sender + ": " + original.content;
            } else if (message.replyToSender != null && message.replyToContent != null) {
                quoteText = message.replyToSender + ": " + message.replyToContent;
            } else {
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

        bubble.getChildren().add(contentLabel);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem replyItem = new MenuItem("Trả lời");
        replyItem.setOnAction(e -> startReply(message));
        MenuItem forwardItem = new MenuItem("Chuyển tiếp");
        forwardItem.setOnAction(e -> openForwardDialog(message));
        contextMenu.getItems().addAll(replyItem, forwardItem);
        bubble.setOnContextMenuRequested(e ->
                contextMenu.show(bubble, e.getScreenX(), e.getScreenY())
        );

        String timeText = Instant.ofEpochMilli(message.timestamp != null ? message.timestamp : System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMAT);
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("message-timestamp");

        VBox column = new VBox(3);
        column.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox bubbleRow = buildBubbleWithActions(bubble, message, isMine, contextMenu);
        VBox.setMargin(bubbleRow, new javafx.geometry.Insets(0, 0, 10, 0));

        HBox row = new HBox(8);
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (isMine) {
            column.getChildren().addAll(bubbleRow, timeLabel);
            row.getChildren().add(column);
        } else {
            String senderName = message.sender != null ? message.sender : "Unknown";
            Label initial = new Label(senderName.substring(0, 1).toUpperCase());
            initial.getStyleClass().add("avatar-text-small");

            StackPane avatar = new StackPane(initial);
            avatar.getStyleClass().add("avatar-circle-small");
            avatar.setStyle("-fx-background-color: " + avatarColorFor(senderName) + ";");

            Label nameLabel = new Label(senderName);
            nameLabel.getStyleClass().add("message-sender-name");

            column.getChildren().addAll(nameLabel, bubbleRow, timeLabel);
            row.getChildren().addAll(avatar, column);
        }
        return row;
    }

    private Button createActionButton(String text, String tip) 
    {
    Button b = new Button(text);
    b.getStyleClass().add("action-button");
    b.setTooltip(new Tooltip(tip));
    return b;
    }


    private javafx.scene.image.ImageView loadEmojiImage(String emoji, double size) 
    {
    String full = emoji.codePoints()
            .mapToObj(Integer::toHexString)
            .collect(java.util.stream.Collectors.joining("-"));
    String noFe0f = full.replace("-fe0f", "");
    java.io.InputStream s = getClass().getResourceAsStream("/emoji/" + full + ".png");
    if (s == null) {
        s = getClass().getResourceAsStream("/emoji/" + noFe0f + ".png");
    }
    if (s == null) return null;
    return new javafx.scene.image.ImageView(new javafx.scene.image.Image(s, size, size, true, true));
    }

    private void setEmojiContent(Button b, String emoji, double size) 
    {
    javafx.scene.image.ImageView iv = loadEmojiImage(emoji, size);
    if (iv != null) {
        b.setText(null);
        b.setGraphic(iv);
    } else {
        b.setGraphic(null);
        b.setText(emoji);
    }
    }

    private HBox buildBubbleWithActions(VBox bubble, ProtocolMessage message,
                                    boolean isMine, ContextMenu contextMenu) {
    Button replyBtn = createActionButton("❝", "Trả lời");
    replyBtn.setOnAction(e -> startReply(message));

    Button forwardBtn = createActionButton("➦", "Chuyển tiếp");
    forwardBtn.setOnAction(e -> openForwardDialog(message));

    HBox actions = new HBox(4, replyBtn, forwardBtn);
    actions.setAlignment(Pos.CENTER);
    actions.setVisible(false);

    
    Button reactBtn = createActionButton("", "Thích");
    reactBtn.getStyleClass().add("react-button");
    setEmojiContent(reactBtn, "👍", 12);
    reactBtn.setOpacity(0.6);
    reactBtn.setVisible(false);
    reactBtn.setTranslateY(12);
    reactBtn.setTranslateX(-6);

    StackPane bubbleStack = new StackPane(bubble, reactBtn);
    StackPane.setAlignment(reactBtn, Pos.BOTTOM_RIGHT);

    HBox bubbleRow = new HBox(4);
    bubbleRow.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    if (isMine) {
        bubbleRow.getChildren().addAll(actions, bubbleStack);
    } else {
        bubbleRow.getChildren().addAll(bubbleStack, actions);
    }

    String[] chosen = {null};   // cảm xúc đã thả, null = chưa thả
    Runnable refresh = () -> {
        boolean hover = bubbleRow.isHover();
        actions.setVisible(hover);
        reactBtn.setVisible(hover || chosen[0] != null);
    };
    bubbleRow.hoverProperty().addListener((o, a, b) -> refresh.run());

    java.util.function.Consumer<String> applyReaction = picked -> {
        chosen[0] = picked.equals(chosen[0]) ? null : picked;   
        setEmojiContent(reactBtn, chosen[0] != null ? chosen[0] : "👍", 12);
        reactBtn.setOpacity(chosen[0] != null ? 1.0 : 0.6);
        refresh.run();
    };

   
    reactBtn.setOnAction(e -> applyReaction.accept("👍"));
    javafx.animation.PauseTransition delay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));
    delay.setOnFinished(e -> showReactionPicker(reactBtn, applyReaction));
    reactBtn.setOnMouseEntered(e -> delay.playFromStart());
    reactBtn.setOnMouseExited(e -> delay.stop());

    return bubbleRow;
}

    private void showReactionPicker(Node anchor, java.util.function.Consumer<String> onPick) 
    {
    if (anchor.getScene() == null) return;
    Popup popup = new Popup();
    popup.setAutoHide(true);

    HBox box = new HBox(4);
    box.getStyleClass().add("reaction-picker");
    box.getStylesheets().addAll(anchor.getScene().getStylesheets());
    box.getStylesheets().addAll(anchor.getScene().getRoot().getStylesheets());

    for (String emoji : new String[]{"👍", "❤️", "😂", "😮", "😢", "😡"}) {
        Button b = new Button();
        b.getStyleClass().add("emoji-item-button");
        setEmojiContent(b, emoji, 26);
        b.setOnAction(e -> {
            onPick.accept(emoji);
            popup.hide();
        });
        box.getChildren().add(b);
    }

    popup.getContent().add(box);
    javafx.geometry.Bounds bd = anchor.localToScreen(anchor.getBoundsInLocal());
    popup.show(anchor, bd.getMinX() - 100, bd.getMinY() - 52);
    }


    private void addMessageBubble(ProtocolMessage message, boolean isMine) {
        if (message == null) return;
        if (message.messageId != null && messageNodeIndex.containsKey(message.messageId)) {
            return;
        }
        HBox row = createMessageRow(message, isMine);
        messageContainer.getChildren().add(row);
        if (message.messageId != null) {
            messageNodeIndex.put(message.messageId, row);
        }

        messageScrollPane.layout();
        messageScrollPane.setVvalue(1.0);
    }

    private void prependMessageBubble(ProtocolMessage message, boolean isMine) {
        if (message == null) return;
        if (message.messageId != null && messageNodeIndex.containsKey(message.messageId)) {
            return;
        }
        HBox row = createMessageRow(message, isMine);
        messageContainer.getChildren().add(0, row);
        if (message.messageId != null) {
            messageNodeIndex.put(message.messageId, row);
        }
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
    forwarded.convId = ConvId.forDm(currentUsername, target.username);
    forwarded.content = original.content;
    forwarded.timestamp = System.currentTimeMillis();
    forwarded.kind = "forward";
    forwarded.forwardFromMessageId = original.messageId;
    forwarded.forwardFromConvId = original.convId;
    forwarded.fwdFrom = original.messageId;
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