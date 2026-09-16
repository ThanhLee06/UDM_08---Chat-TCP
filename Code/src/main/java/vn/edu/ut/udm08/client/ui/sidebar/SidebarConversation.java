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
    private SidebarConversation(String id, String name, String avatar, ConvType type, String lastMessage, Long lastActivity) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.type = type;
        this.lastMessage = lastMessage;
        this.lastActivity = lastActivity;
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
        return new SidebarConversation(id, name.trim(), summary.avatar, info.getType(), summary.lastMessage, summary.lastActivity);
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
    public String getLastActivityText() {
        return lastActivity != null ? String.valueOf(lastActivity) : "";
    }
    public SidebarConversation withLastMessage(String newLastMessage, Long newLastActivity) {
        return new SidebarConversation(this.id, this.name, this.avatar, this.type, newLastMessage, newLastActivity);
    }
    public String getTypeLabel() {
        return switch (type) {
            case DM -> "Tin nhắn riêng";
            case PUBLIC -> "Phòng chung";
            case GROUP -> "Nhóm trò chuyện";
        };
    }
}
