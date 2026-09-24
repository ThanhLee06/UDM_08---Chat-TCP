package vn.edu.ut.udm08.client.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.model.*;
import org.reactfx.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class EmojiInput extends StackPane {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final TextOps<Either<String, Emoji>, String> OPS =
            SegmentOps.<String>styledTextOps()._or(new NodeSegmentOpsBase<Emoji, String>(new Emoji("", null)) {
                @Override public int length(Emoji emoji) { return emoji.unicode().isEmpty() ? 0 : 1; }
            }, (a, b) -> Optional.empty());
    private final Editor editor = new Editor();
    private final Label prompt = new Label();
    private EventHandler<ActionEvent> onAction;

    public EmojiInput() {
        getStyleClass().add("emoji-input");
        editor.getStyleClass().add("emoji-editor");
        editor.setWrapText(false);
        editor.setMinSize(0, 22);
        editor.setPrefHeight(22);
        editor.setMaxHeight(22);
        prompt.setMouseTransparent(true);
        prompt.getStyleClass().add("emoji-input-prompt");
        prompt.visibleProperty().bind(editor.lengthProperty().map(length -> length == 0));
        StackPane.setAlignment(prompt, Pos.CENTER_LEFT);
        getChildren().addAll(editor, prompt);
        setMinWidth(0);
        setPrefHeight(42);
        setMaxHeight(42);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (onAction != null) onAction.handle(new ActionEvent(this, this));
                event.consume();
            }
        });
        setOnMouseClicked(event -> editor.requestFocus());
    }

    public String getPromptText() { return prompt.getText(); }
    public void setPromptText(String value) { prompt.setText(value); }
    public void setOnAction(EventHandler<ActionEvent> handler) { onAction = handler; }
    public String getText() { return unicode(editor.getDocument()); }
    public void setText(String value) { editor.replaceText(0, editor.getLength(), value == null ? "" : value); }
    public void clear() { setText(""); }
    public void replaceSelection(String text) { editor.replaceSelection(text); editor.requestFollowCaret(); }
    public void selectAll() { editor.selectAll(); }
    public void copy() { editor.copy(); }
    public void paste() { editor.paste(); }
    public void cut() { editor.cut(); }
    public void undo() { editor.undo(); }
    public void redo() { editor.redo(); }
    public void moveTo(int position) { editor.moveTo(position); }
    public void deletePreviousChar() { editor.deletePreviousChar(); }
    public int getCaretPosition() { return editor.getCaretPosition(); }
    public void focusEditor() { editor.requestFocus(); }

    private static String unicode(StyledDocument<String, Either<String, Emoji>, String> document) {
        StringBuilder value = new StringBuilder();
        for (var paragraph : document.getParagraphs()) {
            if (!value.isEmpty()) value.append('\n');
            for (var segment : paragraph.getSegments()) value.append(segment.unify(text -> text, Emoji::unicode));
        }
        return value.toString();
    }

    private static List<StyledSegment<Either<String, Emoji>, String>> segments(String value) {
        List<StyledSegment<Either<String, Emoji>, String>> result = new ArrayList<>();
        var matcher = GRAPHEME.matcher(value);
        StringBuilder plain = new StringBuilder();
        while (matcher.find()) {
            String cluster = matcher.group();
            Image image = EmojiText.findImage(cluster);
            if (image == null) { plain.append(cluster); continue; }
            if (!plain.isEmpty()) {
                result.add(new StyledSegment<>(Either.left(plain.toString()), ""));
                plain.setLength(0);
            }
            result.add(new StyledSegment<>(Either.right(new Emoji(cluster, image)), ""));
        }
        if (!plain.isEmpty() || result.isEmpty()) result.add(new StyledSegment<>(Either.left(plain.toString()), ""));
        return result;
    }

    private record Emoji(String unicode, Image image) {}

    private static final class Editor extends GenericStyledArea<String, Either<String, Emoji>, String> {
        Editor() {
            super("", (paragraph, style) -> {}, "", OPS, segment -> segment.getSegment().unify(
                    text -> StyledTextArea.createStyledTextNode(text, segment.getStyle(),
                            (node, style) -> node.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-fill: #182230;")),
                    emoji -> {
                        ImageView image = new ImageView(emoji.image());
                        image.setFitWidth(16); image.setFitHeight(16);
                        image.setPreserveRatio(true); image.setSmooth(true);
                        image.setAccessibleText(emoji.unicode());
                        image.getStyleClass().add("color-emoji");
                        return image;
                    }));
        }

        @Override public void replaceText(int start, int end, String text) {
            String before = unicode(getDocument().subSequence(0, start));
            String after = unicode(getDocument().subSequence(end, getLength()));
            String inserted = text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
            String value = before + inserted + after;
            var parsed = segments(value);
            var builder = new ReadOnlyStyledDocumentBuilder<String, Either<String, Emoji>, String>(OPS, "");
            var document = builder.addParagraph(parsed).build();
            int unicodeCursor = before.length() + inserted.length();
            int consumed = 0;
            int caret = 0;
            for (var styled : parsed) {
                var segment = styled.getSegment();
                int size = segment.unify(String::length, emoji -> emoji.unicode().length());
                if (consumed + size <= unicodeCursor) caret += OPS.length(segment);
                else if (consumed < unicodeCursor) caret += segment.isLeft() ? unicodeCursor - consumed : 1;
                consumed += size;
            }
            getUndoManager().preventMerge();
            super.replace(0, getLength(), document);
            getUndoManager().preventMerge();
            selectRange(caret, caret);
            requestFollowCaret();
        }

        @Override public void copy() {
            if (getSelection().getLength() == 0) return;
            ClipboardContent content = new ClipboardContent();
            content.putString(unicode(getDocument().subSequence(getSelection().getStart(), getSelection().getEnd())));
            Clipboard.getSystemClipboard().setContent(content);
        }

        @Override public void cut() {
            if (!isEditable()) return;
            copy(); replaceSelection("");
        }

        @Override public void paste() {
            if (isEditable() && Clipboard.getSystemClipboard().hasString())
                replaceSelection(Clipboard.getSystemClipboard().getString());
        }
    }
}
