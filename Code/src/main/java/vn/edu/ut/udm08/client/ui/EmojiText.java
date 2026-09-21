package vn.edu.ut.udm08.client.ui;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class EmojiText {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final Map<String, Image> IMAGES = new HashMap<>();

    private EmojiText() {}

    public static void install(Labeled label, double size, double maxWidth) {
        label.textProperty().addListener((obs, oldText, newText) -> render(label, size, maxWidth));
        render(label, size, maxWidth);
    }

    public static void installSingleLine(Labeled label, double size) {
        Runnable refresh = () -> {
            String value = label.getText() == null ? "" : label.getText().replace('\n', ' ');
            double available = Math.max(0, label.getWidth() - label.getInsets().getLeft() - label.getInsets().getRight());
            Text measure = new Text("…");
            measure.setFont(label.getFont());
            double ellipsisWidth = measure.getLayoutBounds().getWidth();
            double used = 0;
            StringBuilder clipped = new StringBuilder();
            var matcher = GRAPHEME.matcher(value);
            while (matcher.find()) {
                String cluster = matcher.group();
                measure.setText(cluster);
                double width = findImage(cluster) == null ? measure.getLayoutBounds().getWidth() : size;
                if (used + width + (matcher.end() < value.length() ? ellipsisWidth : 0) > available) {
                    if (available >= ellipsisWidth) clipped.append('…');
                    break;
                }
                clipped.append(cluster);
                used += width;
            }
            render(label, size, Double.MAX_VALUE, clipped.toString());
            label.setAccessibleText(value);
        };
        label.textProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        label.widthProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        label.fontProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();
    }

    private static void render(Labeled label, double size, double maxWidth) {
        String value = label.getText() == null ? "" : label.getText();
        render(label, size, maxWidth, value);
    }

    private static void render(Labeled label, double size, double maxWidth, String value) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(maxWidth);
        flow.setMouseTransparent(true);
        StringBuilder plain = new StringBuilder();
        var matcher = GRAPHEME.matcher(value);
        boolean colored = false;
        while (matcher.find()) {
            String cluster = matcher.group();
            Image image = findImage(cluster);
            if (image == null) {
                plain.append(cluster);
                continue;
            }
            addText(flow, plain, label);
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setAccessibleText(cluster);
            view.getStyleClass().add("color-emoji");
            flow.getChildren().add(view);
            colored = true;
        }
        addText(flow, plain, label);
        boolean useGraphic = colored || !value.equals(label.getText());
        label.setGraphic(useGraphic ? flow : null);
        label.setContentDisplay(useGraphic ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.TEXT_ONLY);
        label.setAccessibleText(value);
    }

    private static void addText(TextFlow flow, StringBuilder plain, Labeled label) {
        if (plain.isEmpty()) return;
        Text text = new Text(plain.toString());
        text.fontProperty().bind(label.fontProperty());
        text.fillProperty().bind(label.textFillProperty());
        flow.getChildren().add(text);
        plain.setLength(0);
    }

    static Image findImage(String cluster) {
        if (cluster.codePoints().noneMatch(cp -> cp >= 0x2600)) return null;
        boolean joined = cluster.indexOf('\u200d') >= 0;
        String key = cluster.codePoints()
                .filter(cp -> joined || cp != 0xfe0f)
                .mapToObj(Integer::toHexString).collect(Collectors.joining("-"));
        if (IMAGES.containsKey(key)) return IMAGES.get(key);
        var resource = EmojiText.class.getResource("/emoji/" + key + ".png");
        if (resource == null) return null;
        Image image = new Image(resource.toExternalForm());
        if (image.isError()) return null;
        IMAGES.put(key, image);
        return image;
    }
}
