package vn.edu.ut.udm08.client.ui.sidebar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.model.UserProfile;
import vn.edu.ut.udm08.shared.protocol.ConvId;

public final class SidebarController {

    @FXML private ToggleButton directTab;
    @FXML private ToggleButton roomsTab;
    @FXML private ToggleGroup conversationTabs;
    @FXML private ListView<SidebarConversation> conversationList;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private VBox statusPane;
    @FXML private Label statusTitle;
    @FXML private Label statusDetail;
    @FXML private Label conversationCount;
    @FXML private Label currentUserLabel;
    @FXML private Button retryButton;
    @FXML private Button refreshButton;

    private final Map<String, SidebarConversation> activeConversationsMap = new LinkedHashMap<>();
    private List<UserProfile> onlineUsersList = new ArrayList<>();

    private final ObservableList<SidebarConversation> conversations = FXCollections.observableArrayList();
    private final FilteredList<SidebarConversation> filtered = new FilteredList<>(conversations);

    private IConversationSource source;
    private String currentUser;
    private String selectedId = null;
    private int generation;
    private boolean loading;
    private boolean failed;
    private boolean updatingSelection;
    private CompletableFuture<List<ConversationSummary>> pending;
    private Consumer<SidebarConversation> selectionListener = item -> {};

    @FXML
    private void initialize() {
        conversationList.setItems(filtered);
        conversationList.setCellFactory(list -> new ConversationCell());
        conversationList.setPlaceholder(new Label(""));

        if (statusPane != null) {
            statusPane.managedProperty().bind(statusPane.visibleProperty());
            statusPane.setVisible(false);
        }
        if (loadingIndicator != null) {
            loadingIndicator.managedProperty().bind(loadingIndicator.visibleProperty());
            loadingIndicator.setVisible(false);
        }
        if (retryButton != null) {
            retryButton.managedProperty().bind(retryButton.visibleProperty());
            retryButton.setVisible(false);
        }

        conversationTabs.selectedToggleProperty().addListener((observable, oldValue, value) -> {
            if (value == null && oldValue != null) {
                conversationTabs.selectToggle(oldValue);
                return;
            }
            rebuildConversations();
        });

        conversationList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, item) -> {
            if (!updatingSelection && item != null && !item.getId().equals(selectedId)) {
                selectedId = item.getId();
                selectionListener.accept(item);
            }
        });
        conversationList.setOnMouseClicked(e -> {
            SidebarConversation item = conversationList.getSelectionModel().getSelectedItem();
            if (item != null) {
                selectedId = item.getId();
                selectionListener.accept(item);
            }
        });

        rebuildConversations();
    }

    public void configure(IConversationSource source, String username) {
        dispose();
        this.source = Objects.requireNonNull(source);
        currentUser = username;
        currentUserLabel.setText(username == null ? "" : username);
        reload();
    }

    public void setSelectionListener(Consumer<SidebarConversation> listener) {
        selectionListener = Objects.requireNonNull(listener);
    }

    @FXML
    public void reload() {
        if (source == null || loading) {
            return;
        }
        int requestGeneration = ++generation;
        loading = true;
        failed = false;
        renderState();
        try {
            pending = source.load();
            pending.whenComplete((items, error) -> Platform.runLater(() -> {
                if (requestGeneration != generation) {
                    return;
                }
                loading = false;
                failed = error != null;
                pending = null;
                if (!failed) {
                    replaceConversations(items);
                } else {
                    rebuildConversations();
                }
            }));
        } catch (RuntimeException error) {
            loading = false;
            failed = true;
            rebuildConversations();
        }
    }

    private void replaceConversations(List<ConversationSummary> items) {
        activeConversationsMap.clear();
        if (items != null) {
            for (ConversationSummary summary : items) {
                SidebarConversation item = SidebarConversation.from(summary, currentUser);
                if (item != null && !item.getId().equals(ConvId.PUBLIC_ROOM_ID)) {
                    activeConversationsMap.put(item.getId(), item);
                }
            }
        }
        rebuildConversations();
    }

    private void ensurePublicRoom(Map<String, SidebarConversation> map) {
        if (!map.containsKey(ConvId.PUBLIC_ROOM_ID)) {
            ConversationSummary summary = new ConversationSummary();
            summary.convId = ConvId.PUBLIC_ROOM_ID;
            summary.displayName = "Phòng chung";
            summary.chatType = "PUBLIC";
            SidebarConversation item = SidebarConversation.from(summary, currentUser);
            if (item != null) {
                map.put(item.getId(), item);
            }
        }
    }

    public void updateOnlineUsers(List<UserProfile> users) {
        this.onlineUsersList = (users != null) ? new ArrayList<>(users) : new ArrayList<>();
        rebuildConversations();
    }

    private void rebuildConversations() {
        Map<String, SidebarConversation> resultMap = new LinkedHashMap<>();

        boolean isDirectTab = (conversationTabs != null && conversationTabs.getSelectedToggle() == directTab);

        if (isDirectTab) {
            ensurePublicRoom(resultMap);
            for (SidebarConversation item : activeConversationsMap.values()) {
                if (!item.getId().equals(ConvId.PUBLIC_ROOM_ID)) {
                    resultMap.put(item.getId(), item);
                }
            }
        } else {
            for (UserProfile user : onlineUsersList) {
                if (user != null && user.username != null && currentUser != null && !user.username.equalsIgnoreCase(currentUser)) {
                    String convId = ConvId.forDm(currentUser, user.username);
                    if (!resultMap.containsKey(convId)) {
                        ConversationSummary summary = new ConversationSummary();
                        summary.convId = convId;
                        summary.displayName = user.username;
                        summary.avatar = user.avatarId;
                        summary.chatType = "DM";
                        SidebarConversation item = SidebarConversation.from(summary, currentUser);
                        if (item != null) {
                            resultMap.put(item.getId(), item);
                        }
                    }
                }
            }
        }

        updatingSelection = true;
        filtered.setPredicate(item -> true);
        conversations.setAll(resultMap.values());
        restoreSelection();
        updatingSelection = false;
        renderState();
    }

    private void restoreSelection() {
        conversationList.getSelectionModel().clearSelection();
        if (selectedId != null) {
            for (SidebarConversation item : filtered) {
                if (item.getId().equals(selectedId)) {
                    conversationList.getSelectionModel().select(item);
                    return;
                }
            }
        }
    }

    private void renderState() {
        conversationCount.setText(filtered.size() + " cuộc trò chuyện");
        refreshButton.setDisable(source == null || loading);
        if (loadingIndicator != null) loadingIndicator.setVisible(false);
        if (retryButton != null) retryButton.setVisible(false);
        if (statusPane != null) statusPane.setVisible(false);
        conversationList.setVisible(true);
    }

    public SidebarConversation ensureConversation(String convId, String displayName, String avatar) {
        if (convId == null || convId.isBlank()) return null;
        if (!convId.equals(ConvId.PUBLIC_ROOM_ID) && !activeConversationsMap.containsKey(convId)) {
            ConversationSummary summary = new ConversationSummary();
            summary.convId = convId;
            summary.displayName = displayName;
            summary.avatar = avatar != null ? avatar : "avatar1";
            summary.chatType = convId.startsWith("room:") ? "PUBLIC" : "DM";
            SidebarConversation item = SidebarConversation.from(summary, currentUser);
            if (item != null) {
                activeConversationsMap.put(item.getId(), item);
                rebuildConversations();
            }
            return item;
        }
        return activeConversationsMap.get(convId);
    }

    public void dispose() {
        generation++;
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
        source = null;
        currentUser = null;
        selectedId = null;
        loading = false;
        failed = false;
        activeConversationsMap.clear();
        onlineUsersList.clear();
        conversations.clear();
        currentUserLabel.setText("");
        renderState();
    }
}
