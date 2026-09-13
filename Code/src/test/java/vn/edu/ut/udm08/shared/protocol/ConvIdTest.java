package vn.edu.ut.udm08.shared.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConvIdTest {

    @Test
    @DisplayName("ST056-TC01: DM Creation & Alphabetical Sorting")
    void testDmCreationAndSorting() {
        String conv1 = ConvId.forDm("bob", "alice");
        String conv2 = ConvId.forDm("alice", "bob");

        assertEquals("dm:alice:bob", conv1, "forDm('bob', 'alice') should sort user IDs");
        assertEquals("dm:alice:bob", conv2, "forDm('alice', 'bob') should produce identical sorted convId");
        assertEquals(conv1, conv2, "Both orderings must yield the exact same convId");
    }

    @Test
    @DisplayName("ST056-TC02: Public Room & Group Creation")
    void testPublicRoomAndGroupCreation() {
        assertEquals("room:public", ConvId.forPublicRoom(), "forPublicRoom() must return 'room:public'");

        String group1 = ConvId.forGroup("team-dev");
        assertEquals("room:team-dev", group1, "forGroup('team-dev') must prepend 'room:'");

        String group2 = ConvId.forGroup("room:team-dev");
        assertEquals("room:team-dev", group2, "forGroup with existing 'room:' prefix should not double prefix");
    }

    @Test
    @DisplayName("ST056-TC03: Type Checker Methods (isDm, isPublicRoom, isGroup)")
    void testTypeCheckerMethods() {
        String dm = "dm:alice:bob";
        String pub = "room:public";
        String group = "room:team-dev";
        String invalid = "invalid:id";

        assertTrue(ConvId.isDm(dm), "isDm should be true for DM convId");
        assertFalse(ConvId.isDm(pub), "isDm should be false for public room");
        assertFalse(ConvId.isDm(group), "isDm should be false for group room");
        assertFalse(ConvId.isDm(invalid), "isDm should be false for invalid convId");

        assertTrue(ConvId.isPublicRoom(pub), "isPublicRoom should be true for 'room:public'");
        assertFalse(ConvId.isPublicRoom(dm), "isPublicRoom should be false for DM");
        assertFalse(ConvId.isPublicRoom(group), "isPublicRoom should be false for group");

        assertTrue(ConvId.isGroup(group), "isGroup should be true for group room");
        assertFalse(ConvId.isGroup(pub), "isGroup should be false for public room");
        assertFalse(ConvId.isGroup(dm), "isGroup should be false for DM");
        assertFalse(ConvId.isGroup(invalid), "isGroup should be false for invalid convId");
    }

    @Test
    @DisplayName("ST056-TC04: Extract Other User for DM (getOtherUser)")
    void testGetOtherUser() {
        String dm = "dm:alice:bob";

        assertEquals("bob", ConvId.getOtherUser(dm, "alice"), "getOtherUser for 'alice' should return 'bob'");
        assertEquals("alice", ConvId.getOtherUser(dm, "bob"), "getOtherUser for 'bob' should return 'alice'");
        assertNull(ConvId.getOtherUser(dm, "charlie"), "getOtherUser for non-participant should return null");
        assertNull(ConvId.getOtherUser("room:public", "alice"), "getOtherUser for non-DM should return null");
        assertNull(ConvId.getOtherUser(null, "alice"), "getOtherUser for null convId should return null");
    }

    @Test
    @DisplayName("ST056-TC05: Parse DM, Public, and Group convId")
    void testParseConvId() {
        ConvIdInfo dmInfo = ConvId.parse("dm:alice:bob");
        assertNotNull(dmInfo);
        assertEquals(ConvType.DM, dmInfo.getType());
        assertEquals(List.of("alice", "bob"), dmInfo.getUsers());
        assertNull(dmInfo.getGroupId());
        assertEquals("dm:alice:bob", dmInfo.getRaw());

        ConvIdInfo pubInfo = ConvId.parse("room:public");
        assertNotNull(pubInfo);
        assertEquals(ConvType.PUBLIC, pubInfo.getType());
        assertEquals("public", pubInfo.getGroupId());
        assertTrue(pubInfo.getUsers().isEmpty());
        assertEquals("room:public", pubInfo.getRaw());

        ConvIdInfo groupInfo = ConvId.parse("room:abc-123");
        assertNotNull(groupInfo);
        assertEquals(ConvType.GROUP, groupInfo.getType());
        assertEquals("abc-123", groupInfo.getGroupId());
        assertTrue(groupInfo.getUsers().isEmpty());
        assertEquals("room:abc-123", groupInfo.getRaw());
    }

    @Test
    @DisplayName("ST056-TC06: Exception & Edge Cases Handling")
    void testExceptionAndEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> ConvId.forDm(null, "bob"));
        assertThrows(IllegalArgumentException.class, () -> ConvId.forDm("alice", ""));
        assertThrows(IllegalArgumentException.class, () -> ConvId.forDm("alice:1", "bob"));
        assertThrows(IllegalArgumentException.class, () -> ConvId.forGroup(null));
        assertThrows(IllegalArgumentException.class, () -> ConvId.forGroup("public"));

        assertNull(ConvId.parse(null));
        assertNull(ConvId.parse(""));
        assertNull(ConvId.parse("   "));
        assertNull(ConvId.parse("dm:alice"));
        assertNull(ConvId.parse("dm:a:b:c"));
        assertNull(ConvId.parse("room:"));
        assertNull(ConvId.parse("unknown:prefix"));
    }
}
