package vn.edu.ut.udm08.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import vn.edu.ut.udm08.client.network.ChatListener;
import vn.edu.ut.udm08.server.core.ServerApplication;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionGuiTest {
    @TempDir Path temp;
    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException ignored) {}
    }
    @Test void wrongOtpResponseUnlocksBoxesAndBackButton() throws Exception {
        final LoginController[] controller = new LoginController[1]; final Parent[] root = new Parent[1];
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            root[0] = loader.load(); new Scene(root[0], 420, 680); controller[0] = loader.getController();
            var id = LoginController.class.getDeclaredField("currentRegistrationId"); id.setAccessible(true); id.set(controller[0], "test-session");
            invoke(controller[0], "showOtpPane");
            for (int i = 1; i <= 6; i++) ((TextField)root[0].lookup("#otp" + i)).setText("1");
            assertTrue(root[0].lookup("#otpBackBtn").isDisabled());
            ((ChatListener)invoke(controller[0], "createAuthListener")).onErrorReceived("VERIFY_FAILED", "Wrong OTP");
        });
        fx(() -> {
            assertFalse(root[0].lookup("#otpBackBtn").isDisabled());
            for (int i = 1; i <= 6; i++) assertFalse(root[0].lookup("#otp" + i).isDisabled());
            invoke(controller[0], "endOtpSession");
        });
    }
    @Test void serverGuiLoadsAndCanCloseCleanly() throws Exception {
        fx(() -> {
            ServerApplication app = new ServerApplication(); Stage stage = new Stage();
            try { app.start(stage); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                assertTrue(stage.isShowing()); snapshot(stage.getScene().getRoot(), "server-ui.png");
            } finally { app.stop(); stage.close(); }
        });
    }
    @Test void chatRendersAvatarEmojiAndReply() throws Exception {
        Parent[] rendered = new Parent[1]; ChatController[] controller = new ChatController[1];
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load(); new Scene(root, 1000, 700);
            ChatController chat = loader.getController(); chat.setCurrentUsername("alice"); chat.selectPublicRoom();
            var first = new vn.edu.ut.udm08.shared.model.ProtocolMessage(vn.edu.ut.udm08.shared.model.MessageType.CHAT);
            first.messageId = "gui-original"; first.sender = "bob"; first.convId = "room:public";
            first.content = "Xin chào Thanh 😀"; first.avatarId = "avatar2";
            chat.receiveMessage(first);
            var reply = new vn.edu.ut.udm08.shared.model.ProtocolMessage(vn.edu.ut.udm08.shared.model.MessageType.CHAT);
            reply.messageId = "gui-reply"; reply.sender = "bob"; reply.convId = "room:public";
            reply.content = "Tin trả lời 👍"; reply.avatarId = "avatar2"; reply.replyToMessageId = first.messageId;
            reply.replyToSender = "bob"; reply.replyToContent = first.content; chat.receiveMessage(reply);
            rendered[0] = root; controller[0] = chat;
        });
        fx(() -> {
            Parent root = rendered[0]; ChatController chat = controller[0];
            root.applyCss(); root.layout();
            assertTrue(root.lookupAll(".reply-quote-block").size() >= 1);
            assertTrue(root.lookupAll(".color-emoji").size() >= 4);
            snapshot(root, "chat-ui.png"); chat.disposeSidebar();
        });
    }
    @Test void pickerAssetsHaveColorAndJoinedEmojiStayIntact() throws Exception {
        fx(() -> {
            var field = ChatController.class.getDeclaredField("EMOJI_GROUPS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var groups = (java.util.Map<String, String[]>) field.get(null);
            for (String[] group : groups.values()) for (String emoji : group) {
                Button button = new Button(emoji);
                vn.edu.ut.udm08.client.ui.EmojiText.install(button, 24, 32);
                assertNotNull(button.getGraphic(), emoji);
                var flow = (javafx.scene.text.TextFlow) button.getGraphic();
                assertEquals(1, flow.getChildren().size(), emoji);
                var view = (javafx.scene.image.ImageView) flow.getChildren().getFirst();
                assertFalse(view.getImage().isError(), emoji);
                assertEquals(emoji, view.getAccessibleText());
            }
            Label mixed = new Label("Thanh ❤️‍🔥 👍🏽 xin chào");
            vn.edu.ut.udm08.client.ui.EmojiText.install(mixed, 20, 300);
            var flow = (javafx.scene.text.TextFlow) mixed.getGraphic();
            assertEquals(3, flow.getChildren().size());
            assertEquals(" 👍🏽 xin chào", ((javafx.scene.text.Text) flow.getChildren().getLast()).getText());
            var image = ((javafx.scene.image.ImageView) flow.getChildren().get(1)).getImage();
            boolean hasColor = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                var color = image.getPixelReader().getColor(x, y);
                if (color.getOpacity() > .5 && Math.abs(color.getRed() - color.getBlue()) > .2) hasColor = true;
            }
            assertTrue(hasColor);
        });
    }
    @Test void composerKeepsUnicodePayloadWithoutPreview() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load(); new Scene(root, 1000, 700);
            ChatController chat = loader.getController(); chat.setCurrentUsername("alice"); chat.selectPublicRoom();
            var input = (vn.edu.ut.udm08.client.ui.EmojiInput) root.lookup("#messageInput");
            String text = "Thanh 😀 ❤️‍🔥";
            input.setText(text);
            assertNull(root.lookup("#emojiPreview"));
            root.applyCss(); root.layout();
            assertEquals(2, input.lookupAll(".color-emoji").size());
            snapshot(root, "inline-emoji-input.png");
            var sent = new java.util.ArrayList<vn.edu.ut.udm08.shared.model.ProtocolMessage>();
            chat.setSendListener(sent::add);
            input.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
            assertEquals(text, sent.getFirst().content);
            assertTrue(input.getText().isEmpty());
            chat.disposeSidebar();
        });
    }
    @Test void inlineEmojiEditingPreservesSelectionAndClipboardUnicode() throws Exception {
        fx(() -> {
            var input = new vn.edu.ut.udm08.client.ui.EmojiInput();
            new Scene(input, 400, 42);
            input.setText("A❤️‍🔥B");
            input.moveTo(2);
            input.deletePreviousChar();
            assertEquals("AB", input.getText());
            input.undo();
            assertEquals("A❤️‍🔥B", input.getText());
            input.redo();
            assertEquals("AB", input.getText());
            input.moveTo(1);
            input.replaceSelection("😀");
            assertEquals("A😀B", input.getText());
            input.selectAll();
            input.replaceSelection("Tiếng Việt ❤️‍🔥");
            assertEquals("Tiếng Việt ❤️‍🔥", input.getText());
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var saved = new java.util.HashMap<javafx.scene.input.DataFormat, Object>();
            for (var type : clipboard.getContentTypes()) saved.put(type, clipboard.getContent(type));
            try {
                input.selectAll(); input.copy();
                assertEquals(input.getText(), clipboard.getString());
                input.cut(); assertEquals("", input.getText());
                input.paste(); assertEquals("Tiếng Việt ❤️‍🔥", input.getText());
            } finally { clipboard.setContent(saved); }
            input.setText("Dòng rất dài ".repeat(80) + "😀");
            input.applyCss(); input.layout();
            assertTrue(input.getText().endsWith("😀"));
        });
    }
    private static Object invoke(Object target, String method) throws Exception {
        var m = target.getClass().getDeclaredMethod(method); m.setAccessible(true); return m.invoke(target);
    }
    @Test void sidebarEmojiAndDeliveryLabelsAreUserFacing() throws Exception {
        fx(() -> {
            Label preview = new Label("Bạn: 😀❤️‍🔥 nội dung dài ".repeat(8));
            preview.setMinWidth(0); preview.setMaxWidth(170);
            vn.edu.ut.udm08.client.ui.EmojiText.installSingleLine(preview, 14);
            var pane = new javafx.scene.layout.StackPane(preview);
            new Scene(pane, 170, 30); pane.applyCss(); pane.layout();
            assertNotNull(preview.getGraphic());
            assertFalse(preview.lookupAll(".color-emoji").isEmpty());
            assertTrue(preview.getGraphic().getBoundsInLocal().getWidth() <= 171);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load(); new Scene(root, 1000, 700);
            ChatController chat = loader.getController();
            assertInstanceOf(javafx.scene.shape.SVGPath.class, ((Label) root.lookup("#emojiIcon")).getGraphic());
            var message = new vn.edu.ut.udm08.shared.model.ProtocolMessage(vn.edu.ut.udm08.shared.model.MessageType.CHAT);
            message.messageId = "status-test"; message.sender = "alice"; message.content = "😀";
            var render = ChatController.class.getDeclaredMethod("createMessageRow", vn.edu.ut.udm08.shared.model.ProtocolMessage.class, boolean.class);
            render.setAccessible(true);
            Parent history = (Parent) render.invoke(chat, message, true);
            assertFalse(history.lookup("#delivery-status").isManaged());
            message.sendStatus = vn.edu.ut.udm08.shared.model.MessageSendStatus.SENT;
            Parent sent = (Parent) render.invoke(chat, message, true);
            assertEquals("Đã gửi", ((Label) sent.lookup("#delivery-status")).getText());
            assertFalse(sent.lookup("#delivery-status").getParent().getStyleClass().contains("message-bubble-sent"));
            message.sendStatus = vn.edu.ut.udm08.shared.model.MessageSendStatus.FAILED;
            Parent failed = (Parent) render.invoke(chat, message, true);
            assertEquals("Gửi thất bại", ((Label) failed.lookup("#delivery-status")).getText());
            chat.disposeSidebar();
        });
    }
    private static void fx(Checked action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get(10, TimeUnit.SECONDS);
    }
    private static void snapshot(Parent root, String name) throws Exception {
        var image = root.snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(png, "png", Path.of("target", name).toFile());
    }
    private interface Checked { void run() throws Exception; }
}
