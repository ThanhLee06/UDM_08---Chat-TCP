package vn.edu.ut.udm08.client.ui.sidebar;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public final class ConversationCell extends ListCell<SidebarConversation> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM");

    private final SidebarController controller;

    private final Label initial = new Label();
    private final ImageView image = new ImageView();
    private final Circle onlineDot = new Circle(5);
    private final StackPane avatar = new StackPane(initial, image, onlineDot);

    private final Label pinIcon = new Label("📌");
    private final Label name = new Label();
    private final Label muteIcon = new Label("🔕");
    private final Label time = new Label();
    private final Label optionsBtn = new Label("•••");
    private final HBox topRow = new HBox(4, pinIcon, name, muteIcon, time, optionsBtn);

    private final Label detail = new Label();
    private final Label badge = new Label();
    private final HBox bottomRow = new HBox(detail, badge);

    private final VBox text = new VBox(3, topRow, bottomRow);
    private final HBox row = new HBox(12, avatar, text);
    private ContextMenu cellContextMenu;

    public ConversationCell() {
        this(null);
    }

    public ConversationCell(SidebarController controller) {
        this.controller = controller;

        avatar.getStyleClass().add("sidebar-avatar");
        initial.getStyleClass().add("sidebar-initial");
        name.getStyleClass().add("conversation-name");
        time.getStyleClass().add("conversation-time");
        detail.getStyleClass().add("conversation-detail");
        detail.setMinWidth(0);
        vn.edu.ut.udm08.client.ui.EmojiText.installSingleLine(detail, 14);
        badge.getStyleClass().add("conversation-unread-badge");
        pinIcon.getStyleClass().add("conversation-pin-icon");
        muteIcon.getStyleClass().add("conversation-mute-icon");
        optionsBtn.getStyleClass().add("conversation-options-btn");

        optionsBtn.setVisible(false);
        optionsBtn.setManaged(false);

        optionsBtn.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && cellContextMenu != null) {
                cellContextMenu.show(optionsBtn, Side.BOTTOM, 0, 4);
                e.consume();
            }
        });

        hoverProperty().addListener((obs, oldVal, isHovered) -> {
            if (getItem() != null) {
                boolean showBtn = Boolean.TRUE.equals(isHovered);
                optionsBtn.setVisible(showBtn);
                optionsBtn.setManaged(showBtn);
                Long lastAct = getItem().getLastActivity();
                if (lastAct != null && lastAct > 0) {
                    time.setVisible(!showBtn);
                    time.setManaged(!showBtn);
                }
            }
        });

        onlineDot.setFill(javafx.scene.paint.Color.web("#10B981"));
        onlineDot.setStroke(javafx.scene.paint.Color.WHITE);
        onlineDot.setStrokeWidth(1.5);
        onlineDot.setVisible(false);
        StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);

        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        topRow.setAlignment(Pos.CENTER_LEFT);

        detail.setTextOverrun(OverrunStyle.ELLIPSIS);
        detail.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(detail, Priority.ALWAYS);

        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.setSpacing(6);

        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        image.setFitWidth(44);
        image.setFitHeight(44);
        image.setClip(new Circle(22, 22, 22));
        row.prefWidthProperty().bind(widthProperty().subtract(28));
    }

    @Override
    protected void updateItem(SidebarConversation item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        setTooltip(null);
        setContextMenu(null);
        image.setImage(null);
        image.setVisible(false);
        onlineDot.setVisible(false);
        if (empty || item == null) {
            setGraphic(null);
            setAccessibleText(null);
            return;
        }

        name.setText(item.getName());
        String lastMsg = item.getLastMessage();
        if (lastMsg != null && !lastMsg.isBlank()) {
            detail.setText(lastMsg.trim());
        } else {
            detail.setText(item.getTypeLabel());
        }

        boolean isOnlineDM = item.isOnline() && item.getType() == vn.edu.ut.udm08.shared.protocol.ConvType.DM;
        onlineDot.setVisible(isOnlineDM);

        boolean isHovered = isHover();
        optionsBtn.setVisible(isHovered);
        optionsBtn.setManaged(isHovered);

        Long lastAct = item.getLastActivity();
        if (lastAct != null && lastAct > 0) {
            time.setText(formatTime(lastAct));
            time.setVisible(!isHovered);
            time.setManaged(!isHovered);
        } else {
            time.setText("");
            time.setVisible(false);
            time.setManaged(false);
        }

        pinIcon.setVisible(item.isPinned());
        pinIcon.setManaged(item.isPinned());

        muteIcon.setVisible(item.isMuted());
        muteIcon.setManaged(item.isMuted());

        int unread = item.getUnreadCount();
        if (unread > 0) {
            badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            badge.setVisible(true);
            badge.setManaged(true);
            if (!detail.getStyleClass().contains("conversation-detail-unread")) {
                detail.getStyleClass().add("conversation-detail-unread");
            }
            if (!name.getStyleClass().contains("conversation-name-unread")) {
                name.getStyleClass().add("conversation-name-unread");
            }
            if (!time.getStyleClass().contains("conversation-time-unread")) {
                time.getStyleClass().add("conversation-time-unread");
            }
        } else {
            badge.setText("");
            badge.setVisible(false);
            badge.setManaged(false);
            detail.getStyleClass().remove("conversation-detail-unread");
            name.getStyleClass().remove("conversation-name-unread");
            time.getStyleClass().remove("conversation-time-unread");
        }

        initial.setText(item.getName().substring(0, item.getName().offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
        vn.edu.ut.udm08.client.ui.AvatarImages.apply(image, item.getAvatar());
        image.setVisible(true);

        if (controller != null) {
            ContextMenu menu = new ContextMenu();
            menu.getStyleClass().add("custom-context-menu");

            MenuItem pinItem = new MenuItem(item.isPinned() ? "📌 Bỏ ghim trò chuyện" : "📌 Ghim trò chuyện");
            pinItem.setOnAction(e -> controller.togglePin(item.getId()));

            MenuItem markReadItem = new MenuItem(item.isUnread() ? "✉ Đánh dấu đã đọc" : "✉ Đánh dấu chưa đọc");
            markReadItem.setOnAction(e -> {
                if (item.isUnread()) {
                    controller.markAsRead(item.getId());
                } else {
                    controller.markAsUnread(item.getId());
                }
            });

            MenuItem muteItem = new MenuItem(item.isMuted() ? "🔔 Bật thông báo" : "🔕 Tắt thông báo");
            muteItem.setOnAction(e -> controller.toggleMute(item.getId()));

            MenuItem deleteItem = new MenuItem("❌ Xóa cuộc trò chuyện");
            deleteItem.getStyleClass().add("menu-item-danger");
            deleteItem.setOnAction(e -> controller.deleteConversationWithConfirmation(item.getId(), item.getName()));

            menu.getItems().addAll(pinItem, markReadItem, muteItem, new SeparatorMenuItem(), deleteItem);
            setContextMenu(menu);
            this.cellContextMenu = menu;
        } else {
            this.cellContextMenu = null;
        }

        setTooltip(new Tooltip(item.getName()));
        setAccessibleText(item.getName() + ", " + item.getTypeLabel());
        setGraphic(row);
    }

    private static String formatTime(long timestamp) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        if (dateTime.toLocalDate().equals(now.toLocalDate())) {
            return TIME_FORMAT.format(dateTime);
        } else if (dateTime.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return "Hôm qua";
        } else {
            return DATE_FORMAT.format(dateTime);
        }
    }
}
