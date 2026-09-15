package vn.edu.ut.udm08.client.ui.sidebar;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
public final class ConversationCell extends ListCell<SidebarConversation> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private final Label initial = new Label();
    private final ImageView image = new ImageView();
    private final StackPane avatar = new StackPane(initial, image);
    private final Label name = new Label();
    private final Label time = new Label();
    private final HBox topRow = new HBox(name, time);
    private final Label detail = new Label();
    private final VBox text = new VBox(3, topRow, detail);
    private final HBox row = new HBox(12, avatar, text);
    public ConversationCell() {
        avatar.getStyleClass().add("sidebar-avatar");
        initial.getStyleClass().add("sidebar-initial");
        name.getStyleClass().add("conversation-name");
        time.getStyleClass().add("conversation-time");
        detail.getStyleClass().add("conversation-detail");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        detail.setTextOverrun(OverrunStyle.ELLIPSIS);
        detail.setMaxWidth(Double.MAX_VALUE);
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
        image.setImage(null);
        image.setVisible(false);
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
        Long lastAct = item.getLastActivity();
        if (lastAct != null && lastAct > 0) {
            time.setText(formatTime(lastAct));
            time.setVisible(true);
        } else {
            time.setText("");
            time.setVisible(false);
        }
        initial.setText(item.getName().substring(0, item.getName().offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
        String value = item.getAvatar();
        if (value != null && value.matches("[a-zA-Z0-9_-]+(?:\\.(?:png|jpg|jpeg))?")) {
            String filename = value.contains(".") ? value : value + ".jpg";
            URL resource = getClass().getResource("/images/" + filename);
            if (resource != null) {
                Image picture = new Image(resource.toExternalForm(), 44, 44, false, true);
                if (!picture.isError()) {
                    image.setImage(picture);
                    image.setVisible(true);
                }
            }
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
        } else {
            return DATE_FORMAT.format(dateTime);
        }
    }
}
