package vn.edu.ut.udm08.client.ui.sidebar;
import vn.edu.ut.udm08.shared.model.ConversationSummary;
import vn.edu.ut.udm08.shared.protocol.ConvId;
import vn.edu.ut.udm08.shared.protocol.ConvIdInfo;
import vn.edu.ut.udm08.shared.protocol.ConvType;
public final class SidebarConversation {
    private final String id;
    private final String name;
    private final String avatar;
    private final ConvType type;
    private final String lastMessage;
    private final Long lastActivity;
    private final int unreadCount;
    private final boolean pinned;
    private final boolean muted;
    private final boolean online;

    private SidebarConversation(String id, String name, String avatar, ConvType type, String lastMessage, Long lastActivity, int unreadCount, boolean pinned, boolean muted, boolean online) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.type = type;
        this.lastMessage = lastMessage;
        this.lastActivity = lastActivity;
        this.unreadCount = Math.max(0, unreadCount);
        this.pinned = pinned;
        this.muted = muted;
        this.online = online;
    }

    public static SidebarConversation from(ConversationSummary summary, String currentUser) {
        if (summary == null) {
            return null;
        }
        ConvIdInfo info = ConvId.parse(summary.convId);
        if (info == null) {
            return null;
        }
        String id = summary.convId.trim();
        String name = summary.displayName;
        if (name == null || name.isBlank() || name.equals(id)) {
            name = switch (info.getType()) {
                case DM -> ConvId.getOtherUser(id, currentUser);
                case PUBLIC -> "Phòng chung";
                case GROUP -> info.getGroupId();
            };
        }
        if (name == null || name.isBlank()) {
            name = "Cuộc trò chuyện";
        }
        boolean defaultPinned = id.equals(ConvId.PUBLIC_ROOM_ID);
        return new SidebarConversation(id, name.trim(), summary.avatar, info.getType(), summary.lastMessage, summary.lastActivity, summary.unreadCount, defaultPinned, false, false);
    }
    public SidebarConversation withProfile(String displayName, String avatar) {
        return new SidebarConversation(id, displayName, avatar, type, lastMessage, lastActivity, unreadCount, pinned, muted, online);
    }
    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getAvatar() {
        return avatar;
    }
    public ConvType getType() {
        return type;
    }
    public String getLastMessage() {
        return lastMessage;
    }
    public Long getLastActivity() {
        return lastActivity;
    }
    public int getUnreadCount() {
        return unreadCount;
    }
    public boolean isUnread() {
        return unreadCount > 0;
    }
    public boolean isPinned() {
        return pinned;
    }
    public boolean isMuted() {
        return muted;
    }
    public boolean isOnline() {
        return online;
    }
    public String getLastActivityText() {
        return lastActivity != null ? String.valueOf(lastActivity) : "";
    }
    public SidebarConversation withLastMessage(String newLastMessage, Long newLastActivity) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, newLastMessage, newLastActivity, this.unreadCount, this.pinned, this.muted, this.online);
    }
    public SidebarConversation withLastMessage(String newLastMessage, Long newLastActivity, int newUnreadCount) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, newLastMessage, newLastActivity, newUnreadCount, this.pinned, this.muted, this.online);
    }
    public SidebarConversation withUnreadCount(int newUnreadCount) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, this.lastMessage, this.lastActivity, newUnreadCount, this.pinned, this.muted, this.online);
    }
    public SidebarConversation withPinned(boolean newPinned) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, this.lastMessage, this.lastActivity, this.unreadCount, newPinned, this.muted, this.online);
    }
    public SidebarConversation withMuted(boolean newMuted) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, this.lastMessage, this.lastActivity, this.unreadCount, this.pinned, newMuted, this.online);
    }
    public SidebarConversation withOnline(boolean newOnline) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, this.lastMessage, this.lastActivity, this.unreadCount, this.pinned, this.muted, newOnline);
    }
    public String getTypeLabel() {
        return switch (type) {
            case DM -> "Tin nhắn riêng";
            case PUBLIC -> "Phòng chung";
            case GROUP -> "Nhóm trò chuyện";
        };
    }
}
