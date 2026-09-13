package vn.edu.ut.udm08.shared.protocol;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result object containing parsed components of a conversation ID (convId).
 */
public final class ConvIdInfo {
    private final ConvType type;
    private final List<String> users;
    private final String groupId;
    private final String raw;

    public ConvIdInfo(ConvType type, List<String> users, String groupId, String raw) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.users = users != null ? Collections.unmodifiableList(users) : Collections.emptyList();
        this.groupId = groupId;
        this.raw = raw;
    }

    public ConvType getType() {
        return type;
    }

    public List<String> getUsers() {
        return users;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getRaw() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConvIdInfo that = (ConvIdInfo) o;
        return type == that.type &&
                Objects.equals(users, that.users) &&
                Objects.equals(groupId, that.groupId) &&
                Objects.equals(raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, users, groupId, raw);
    }

    @Override
    public String toString() {
        return "ConvIdInfo{" +
                "type=" + type +
                ", users=" + users +
                ", groupId='" + groupId + '\'' +
                ", raw='" + raw + '\'' +
                '}';
    }
}
