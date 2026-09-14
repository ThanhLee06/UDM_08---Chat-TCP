package vn.edu.ut.udm08.shared.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolMessageTest {

    @Test
    @DisplayName("ST055-TC01: Full Fields Serialization - new fields included when present")
    void testFullFieldsSerialization() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-101";
        msg.sender = "alice";
        msg.target = "bob";
        msg.convId = "conv-alice-bob";
        msg.replyTo = "msg-100";
        msg.fwdFrom = "msg-099";
        msg.kind = MessageKind.REPLY;

        String json = JsonUtil.toJson(msg);

        assertTrue(json.contains("\"convId\":\"conv-alice-bob\""), "JSON should contain convId");
        assertTrue(json.contains("\"replyTo\":\"msg-100\""), "JSON should contain replyTo");
        assertTrue(json.contains("\"fwdFrom\":\"msg-099\""), "JSON should contain fwdFrom");
        assertTrue(json.contains("\"kind\":\"reply\""), "JSON should contain kind");
    }

    @Test
    @DisplayName("ST055-TC02: Full Fields Deserialization - deserialize new fields accurately")
    void testFullFieldsDeserialization() throws Exception {
        String json = "{"
                + "\"type\":\"CHAT\","
                + "\"messageId\":\"msg-202\","
                + "\"sender\":\"bob\","
                + "\"target\":\"alice\","
                + "\"convId\":\"conv-bob-alice\","
                + "\"replyTo\":\"msg-201\","
                + "\"fwdFrom\":\"msg-200\","
                + "\"kind\":\"forward\""
                + "}";

        ProtocolMessage msg = JsonUtil.fromJson(json);

        assertNotNull(msg);
        assertEquals(MessageType.CHAT, msg.type);
        assertEquals("msg-202", msg.messageId);
        assertEquals("bob", msg.sender);
        assertEquals("alice", msg.target);
        assertEquals("conv-bob-alice", msg.convId);
        assertEquals("msg-201", msg.replyTo);
        assertEquals("msg-200", msg.fwdFrom);
        assertEquals(MessageKind.FORWARD, msg.kind);
    }

    @Test
    @DisplayName("ST055-TC03: Omit Null Fields - null new fields not present in JSON string")
    void testOmitNullFields() throws Exception {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT);
        msg.messageId = "msg-303";
        msg.sender = "alice";
        msg.target = "bob";
        msg.content = "Hello Bob";
        // convId, replyTo, fwdFrom, kind are left null

        String json = JsonUtil.toJson(msg);

        assertFalse(json.contains("convId"), "JSON should not contain convId when null");
        assertFalse(json.contains("replyTo"), "JSON should not contain replyTo when null");
        assertFalse(json.contains("fwdFrom"), "JSON should not contain fwdFrom when null");
        assertFalse(json.contains("kind"), "JSON should not contain kind when null");
    }

    @Test
    @DisplayName("ST055-TC04: Backward Compatibility - deserialize JSON without new fields")
    void testBackwardCompatibility() throws Exception {
        String legacyJson = "{"
                + "\"type\":\"CHAT\","
                + "\"messageId\":\"msg-legacy-1\","
                + "\"sender\":\"alice\","
                + "\"target\":\"bob\","
                + "\"content\":\"Hello from legacy client\""
                + "}";

        ProtocolMessage msg = JsonUtil.fromJson(legacyJson);

        assertNotNull(msg);
        assertEquals(MessageType.CHAT, msg.type);
        assertEquals("msg-legacy-1", msg.messageId);
        assertEquals("alice", msg.sender);
        assertEquals("bob", msg.target);
        assertEquals("Hello from legacy client", msg.content);
        assertNull(msg.convId, "convId should be null for legacy JSON");
        assertNull(msg.replyTo, "replyTo should be null for legacy JSON");
        assertNull(msg.fwdFrom, "fwdFrom should be null for legacy JSON");
        assertNull(msg.kind, "kind should be null for legacy JSON");
    }

    @Test
    @DisplayName("ST055-TC05: Valid Kind Values Round-trip - text, reply, forward, system")
    void testValidKindValuesRoundTrip() throws Exception {
        String[] allowedKinds = {
                MessageKind.TEXT,
                MessageKind.REPLY,
                MessageKind.FORWARD,
                MessageKind.SYSTEM
        };

        for (String kindValue : allowedKinds) {
            ProtocolMessage original = new ProtocolMessage(MessageType.CHAT);
            original.messageId = "msg-kind-" + kindValue;
            original.kind = kindValue;

            String json = JsonUtil.toJson(original);
            ProtocolMessage deserialized = JsonUtil.fromJson(json);

            assertNotNull(deserialized);
            assertEquals(kindValue, deserialized.kind, "Kind value should survive JSON round-trip for: " + kindValue);
        }
    }

    @Test
    @DisplayName("ST055-TC06: Existing Fields Regression - all original fields serialize and deserialize unaffected")
    void testExistingFieldsRegression() throws Exception {
        ProtocolMessage original = new ProtocolMessage(MessageType.USER_LIST);
        original.messageId = "msg-reg-1";
        original.sender = "server";
        original.target = "alice";
        original.content = "User list response";
        original.avatarId = "avatar_01";
        original.timestamp = 1700000000000L;

        UserProfile profile = new UserProfile("bob", "avatar_02");
        original.users = List.of(profile);

        original.errorCode = "ERR_NONE";
        original.errorMessage = "No error";

        // Also populate new fields
        original.convId = "conv-sys-1";
        original.kind = MessageKind.SYSTEM;

        String json = JsonUtil.toJson(original);
        ProtocolMessage deserialized = JsonUtil.fromJson(json);

        assertNotNull(deserialized);
        assertEquals(MessageType.USER_LIST, deserialized.type);
        assertEquals("msg-reg-1", deserialized.messageId);
        assertEquals("server", deserialized.sender);
        assertEquals("alice", deserialized.target);
        assertEquals("User list response", deserialized.content);
        assertEquals("avatar_01", deserialized.avatarId);
        assertEquals(1700000000000L, deserialized.timestamp);
        assertNotNull(deserialized.users);
        assertEquals(1, deserialized.users.size());
        assertEquals("bob", deserialized.users.get(0).username);
        assertEquals("ERR_NONE", deserialized.errorCode);
        assertEquals("No error", deserialized.errorMessage);
        assertEquals("conv-sys-1", deserialized.convId);
        assertEquals(MessageKind.SYSTEM, deserialized.kind);
    }
}
