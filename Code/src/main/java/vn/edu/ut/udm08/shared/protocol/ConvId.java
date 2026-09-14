package vn.edu.ut.udm08.shared.protocol;

import java.util.List;
import java.util.Objects;

/**
 * Helper class for creating, parsing, and inspecting conversation IDs (convId).
 * <p>
 * Supported convId formats:
 * <ul>
 *   <li><b>DM</b>: <code>dm:&lt;user1&gt;:&lt;user2&gt;</code> (user IDs are sorted alphabetically)</li>
 *   <li><b>Public Room</b>: <code>room:public</code></li>
 *   <li><b>Group Room</b>: <code>room:&lt;groupId&gt;</code></li>
 * </ul>
 */
public final class ConvId {

    public static final String PUBLIC_ROOM_ID = "room:public";
    public static final String PUBLIC_ROOM_NAME = "public";
    public static final String PREFIX_DM = "dm:";
    public static final String PREFIX_ROOM = "room:";

    private ConvId() {}

    /**
     * Creates a conversation ID for a Direct Message (DM) between two users.
     * User IDs are sorted alphabetically to ensure uniqueness regardless of order.
     *
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Formatted convId (e.g., "dm:alice:bob")
     * @throws IllegalArgumentException if either user ID is null, blank, or contains colons
     */
    public static String forDm(String userId1, String userId2) {
        if (userId1 == null || userId1.isBlank()) {
            throw new IllegalArgumentException("userId1 must not be null or blank");
        }
        if (userId2 == null || userId2.isBlank()) {
            throw new IllegalArgumentException("userId2 must not be null or blank");
        }
        String u1 = userId1.trim();
        String u2 = userId2.trim();

        if (u1.contains(":") || u2.contains(":")) {
            throw new IllegalArgumentException("User IDs must not contain colons");
        }

        if (u1.compareTo(u2) <= 0) {
            return PREFIX_DM + u1 + ":" + u2;
        } else {
            return PREFIX_DM + u2 + ":" + u1;
        }
    }

    /**
     * Returns the conversation ID for the public room ("room:public").
     */
    public static String forPublicRoom() {
        return PUBLIC_ROOM_ID;
    }

    /**
     * Creates a conversation ID for a group room.
     *
     * @param groupId Group identifier
     * @return Formatted convId (e.g., "room:team-backend")
     * @throws IllegalArgumentException if groupId is null or blank
     */
    public static String forGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId must not be null or blank");
        }
        String id = groupId.trim();
        if (id.equals(PUBLIC_ROOM_NAME) || id.equals(PUBLIC_ROOM_ID)) {
            throw new IllegalArgumentException("For public room, use forPublicRoom() instead");
        }
        if (id.startsWith(PREFIX_ROOM)) {
            return id;
        }
        return PREFIX_ROOM + id;
    }

    /**
     * Checks if the convId represents a DM conversation.
     */
    public static boolean isDm(String convId) {
        if (convId == null || !convId.startsWith(PREFIX_DM)) {
            return false;
        }
        ConvIdInfo info = parse(convId);
        return info != null && info.getType() == ConvType.DM;
    }

    /**
     * Checks if the convId represents the public room.
     */
    public static boolean isPublicRoom(String convId) {
        return PUBLIC_ROOM_ID.equals(convId);
    }

    /**
     * Checks if the convId represents a group room.
     */
    public static boolean isGroup(String convId) {
        if (convId == null || !convId.startsWith(PREFIX_ROOM) || PUBLIC_ROOM_ID.equals(convId)) {
            return false;
        }
        ConvIdInfo info = parse(convId);
        return info != null && info.getType() == ConvType.GROUP;
    }

    /**
     * Given a DM convId and one participant's user ID, returns the other participant's user ID.
     *
     * @param convId The DM conversation ID (e.g., "dm:alice:bob")
     * @param myUserId The user ID of the caller (e.g., "alice")
     * @return The other user ID (e.g., "bob"), or null if convId is not DM or myUserId is not a participant
     */
    public static String getOtherUser(String convId, String myUserId) {
        if (convId == null || myUserId == null || myUserId.isBlank()) {
            return null;
        }
        ConvIdInfo info = parse(convId);
        if (info == null || info.getType() != ConvType.DM) {
            return null;
        }
        List<String> users = info.getUsers();
        if (users.size() != 2) {
            return null;
        }
        String cleanMyId = myUserId.trim();
        if (users.get(0).equals(cleanMyId)) {
            return users.get(1);
        } else if (users.get(1).equals(cleanMyId)) {
            return users.get(0);
        }
        return null;
    }

    /**
     * Parses a convId string into a structured {@link ConvIdInfo} object.
     *
     * @param convId The conversation ID string to parse
     * @return {@link ConvIdInfo} if valid format, or null if null/blank/invalid format
     */
    public static ConvIdInfo parse(String convId) {
        if (convId == null || convId.isBlank()) {
            return null;
        }
        String trimmed = convId.trim();

        if (PUBLIC_ROOM_ID.equals(trimmed)) {
            return new ConvIdInfo(ConvType.PUBLIC, null, PUBLIC_ROOM_NAME, trimmed);
        }

        if (trimmed.startsWith(PREFIX_DM)) {
            String remainder = trimmed.substring(PREFIX_DM.length());
            String[] parts = remainder.split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return null;
            }
            return new ConvIdInfo(ConvType.DM, List.of(parts[0], parts[1]), null, trimmed);
        }

        if (trimmed.startsWith(PREFIX_ROOM)) {
            String groupId = trimmed.substring(PREFIX_ROOM.length());
            if (groupId.isBlank()) {
                return null;
            }
            if (groupId.equals(PUBLIC_ROOM_NAME)) {
                return new ConvIdInfo(ConvType.PUBLIC, null, PUBLIC_ROOM_NAME, trimmed);
            }
            return new ConvIdInfo(ConvType.GROUP, null, groupId, trimmed);
        }

        return null;
    }
}
