package vn.edu.ut.udm08.client.ui.sidebar;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.client.controller.ChatController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SidebarControllerTest {
    private SidebarController controller;
    private Parent root;
    private ListView<SidebarConversation> list;
    private final List<CompletableFuture<List<ConversationSummary>>> requests = new ArrayList<>();
    @BeforeAll
    static void startFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> Platform.setImplicitExit(false));
        }
    }
    @BeforeEach
    @SuppressWarnings("unchecked")
    void load() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sidebar.fxml"));
            root = loader.load();
            new Scene(root, 310, 620);
            controller = loader.getController();
            list = (ListView<SidebarConversation>) root.lookup("#conversationList");
            controller.configure(() -> {
                var result = new CompletableFuture<List<ConversationSummary>>();
                requests.add(result);
                return result;
            }, "alice");
        });
    }
    @Test
    void loadsOfflineConversationsDeduplicatesAndFiltersTabs() throws Exception {
        fx(() -> {
            controller.reload();
            assertEquals(1, requests.size());
        });
        requests.getFirst().complete(List.of(summary("dm:alice:bob", "Bảo"), summary("dm:alice:bob", "Bảo"),
                summary("room:public", "Phòng chung"), summary("room:team", "Nhóm đồ án"), summary("bad", "Sai")));
        fx(() -> {
            assertEquals(3, list.getItems().size());
            assertEquals("room:public", list.getItems().get(0).getId());
            assertEquals("Bảo", list.getItems().get(1).getName());
            ((ToggleButton) root.lookup("#roomsTab")).fire();
            assertEquals(0, list.getItems().size());
            ((ToggleButton) root.lookup("#roomsTab")).fire();
            assertTrue(((ToggleButton) root.lookup("#roomsTab")).isSelected());
            ((ToggleButton) root.lookup("#directTab")).fire();
            assertEquals(3, list.getItems().size());
        });
    }
    @Test
    void failureCanRetryAndThenShowEmptyState() throws Exception {
        requests.getFirst().completeExceptionally(new IllegalStateException("timeout"));
        fx(() -> {
            controller.reload();
            assertEquals(2, requests.size());
        });
        requests.getLast().complete(List.of());
        fx(() -> {
            assertEquals(1, list.getItems().size());
            assertEquals("room:public", list.getItems().getFirst().getId());
        });
    }
    @Test
    void oldResponseDoesNotLeakIntoNewSession() throws Exception {
        var oldRequest = requests.getFirst();
        var newRequest = new CompletableFuture<List<ConversationSummary>>();
        fx(() -> {
            oldRequest.complete(List.of(summary("dm:alice:bob", "Bảo")));
            controller.configure(() -> newRequest, "carol");
        });
        newRequest.complete(List.of(summary("dm:carol:dave", "David")));
        fx(() -> {
            assertEquals(2, list.getItems().size());
            assertEquals("David", list.getItems().get(1).getName());
            controller.dispose();
            assertTrue(list.getItems().isEmpty());
            assertEquals("", ((Label) root.lookup("#currentUserLabel")).getText());
        });
    }
    @Test
    void selectionUsesIdAcrossTabSwitchAndReload() throws Exception {
        AtomicInteger selections = new AtomicInteger();
        requests.getFirst().complete(List.of(summary("dm:alice:bob", "Bảo"), summary("dm:alice:dan", "Danh")));
        fx(() -> {
            controller.setSelectionListener(item -> selections.incrementAndGet());
            list.getSelectionModel().select(1);
            ((ToggleButton) root.lookup("#roomsTab")).fire();
            ((ToggleButton) root.lookup("#directTab")).fire();
            assertEquals("dm:alice:bob", list.getSelectionModel().getSelectedItem().getId());
            controller.reload();
        });
        requests.getLast().complete(List.of(summary("dm:alice:dan", "Danh"), summary("dm:alice:bob", "Bảo")));
        fx(() -> {
            assertEquals("dm:alice:bob", list.getSelectionModel().getSelectedItem().getId());
            assertEquals(1, selections.get());
        });
    }
    @Test
    void chatFxmlLoadsSplitStylesAndSidebarFits() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent chat = loader.load();
            var chatController = (ChatController) loader.getController();
            chatController.setCurrentUsername("alice");
            chatController.loadSidebar(() -> CompletableFuture.completedFuture(List.of(
                    summary("dm:alice:bob", "Bảo"), summary("dm:alice:dan", "Danh Nguyễn với tên rất dài để kiểm tra"),
                    summary("room:public", "Phòng chung"))));
            root = chat;
            new Scene(root, 960, 620);
        });
        fx(() -> {
            root.applyCss();
            root.layout();
            assertEquals(3, root.getStylesheets().size());
            var sidebar = root.lookup("#sidebar");
            assertNotNull(sidebar);
            assertTrue(sidebar.getBoundsInParent().getWidth() >= 280);
            assertTrue(sidebar.getBoundsInParent().getWidth() <= 360);
            WritableImage snapshot = root.snapshot(null, null);
            var png = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) {
                for (int x = 0; x < png.getWidth(); x++) {
                    png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                }
            }
            javax.imageio.ImageIO.write(png, "png", Path.of("target", "sidebar-preview.png").toFile());
        });
    }
    @Test
    void updatesLastMessageAndSortsByRecentActivityDescendingKeepingPublicRoomPinned() throws Exception {
        requests.getFirst().complete(List.of(
            summary("dm:alice:bob", "Bảo"),
            summary("dm:alice:dan", "Danh")
        ));
        fx(() -> {
            assertEquals(3, list.getItems().size());
            assertEquals("room:public", list.getItems().get(0).getId());
            
            long now = System.currentTimeMillis();
            controller.updateLastMessage("dm:alice:bob", "Xin chào Bob", now - 5000);
            controller.updateLastMessage("dm:alice:dan", "Danh nhắn mới hơn", now);

            assertEquals("room:public", list.getItems().get(0).getId());
            assertEquals("dm:alice:dan", list.getItems().get(1).getId());
            assertEquals("Danh nhắn mới hơn", list.getItems().get(1).getLastMessage());
            assertEquals("dm:alice:bob", list.getItems().get(2).getId());
            assertEquals("Xin chào Bob", list.getItems().get(2).getLastMessage());
        });
    }
    @Test
    void supportsGroupRoomSelection() throws Exception {
        requests.getFirst().complete(List.of(
            summary("room:dev-team", "Nhóm Lập Trình")
        ));
        AtomicInteger selectedCount = new AtomicInteger();
        fx(() -> {
            controller.setSelectionListener(item -> selectedCount.incrementAndGet());
            list.getSelectionModel().select(1);
            assertEquals("room:dev-team", list.getSelectionModel().getSelectedItem().getId());
            assertEquals(1, selectedCount.get());
        });
    }
    private static ConversationSummary summary(String id, String name) {
        var result = new ConversationSummary();
        result.convId = id;
        result.displayName = name;
        result.avatar = "avatar1";
        return result;
    }
    private static void fx(IFxAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
    private interface IFxAction {
        void run() throws Exception;
    }
}
