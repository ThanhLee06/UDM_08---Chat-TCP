package vn.edu.ut.udm08.client.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-14: Process and Dispatch Incoming Server Messages Tests")
class ChatReceiverDispatchTest {

    private ChatClient client;
    private BufferedReader dummyReader;

    @BeforeEach
    void setUp() {
        client = new ChatClient();
        dummyReader = new BufferedReader(new StringReader(""));
    }

    @Test
    @DisplayName("TC_01: Phân loại gói tin HELLO_OK và kích hoạt callback onLoginSuccess")
    void testDispatchHelloOkInvokesOnLoginSuccess() {
        AtomicReference<ProtocolMessage> receivedMsg = new AtomicReference<>();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onLoginSuccess(ProtocolMessage message) {
                receivedMsg.set(message);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        ProtocolMessage helloOk = new ProtocolMessage(MessageType.HELLO_OK);
        helloOk.sender = "SERVER";
        helloOk.target = "Alice";
        helloOk.avatarId = "avatar_cat";

        receiver.dispatchMessage(helloOk);

        assertNotNull(receivedMsg.get());
        assertEquals(MessageType.HELLO_OK, receivedMsg.get().type);
        assertEquals("Alice", receivedMsg.get().target);
        assertEquals("avatar_cat", receivedMsg.get().avatarId);
    }

    @Test
    @DisplayName("TC_02: Phân loại gói tin USER_LIST và kích hoạt callback onUserListUpdated")
    void testDispatchUserListInvokesOnUserListUpdated() {
        AtomicReference<List<UserProfile>> receivedUsers = new AtomicReference<>();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onUserListUpdated(List<UserProfile> users) {
                receivedUsers.set(users);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        ProtocolMessage userListMsg = new ProtocolMessage(MessageType.USER_LIST);
        userListMsg.users = List.of(
                new UserProfile("Alice", "avatar_1"),
                new UserProfile("Bob", "avatar_2")
        );

        receiver.dispatchMessage(userListMsg);

        assertNotNull(receivedUsers.get());
        assertEquals(2, receivedUsers.get().size());
        assertEquals("Alice", receivedUsers.get().get(0).username);
        assertEquals("Bob", receivedUsers.get().get(1).username);
    }

    @Test
    @DisplayName("TC_03: Chuyển giao danh sách rỗng (Empty List) an toàn khi USER_LIST có users là null")
    void testDispatchUserListWithNullUsersDefaultsToEmptyList() {
        AtomicReference<List<UserProfile>> receivedUsers = new AtomicReference<>();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onUserListUpdated(List<UserProfile> users) {
                receivedUsers.set(users);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        ProtocolMessage userListMsg = new ProtocolMessage(MessageType.USER_LIST);
        userListMsg.users = null;

        assertDoesNotThrow(() -> receiver.dispatchMessage(userListMsg));
        assertNotNull(receivedUsers.get(), "Phải trả về List rỗng chứ không được null");
        assertTrue(receivedUsers.get().isEmpty());
    }

    @Test
    @DisplayName("TC_04: Phân loại gói tin CHAT và kích hoạt callback onMessageReceived")
    void testDispatchChatInvokesOnMessageReceived() {
        AtomicReference<ProtocolMessage> receivedMsg = new AtomicReference<>();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onMessageReceived(ProtocolMessage message) {
                receivedMsg.set(message);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        ProtocolMessage chatMsg = new ProtocolMessage(MessageType.CHAT);
        chatMsg.messageId = "msg-12345";
        chatMsg.sender = "Bob";
        chatMsg.target = "Alice";
        chatMsg.content = "Chao ban, toi la Bob!";

        receiver.dispatchMessage(chatMsg);

        assertNotNull(receivedMsg.get());
        assertEquals(MessageType.CHAT, receivedMsg.get().type);
        assertEquals("msg-12345", receivedMsg.get().messageId);
        assertEquals("Bob", receivedMsg.get().sender);
        assertEquals("Alice", receivedMsg.get().target);
        assertEquals("Chao ban, toi la Bob!", receivedMsg.get().content);
    }

    @Test
    @DisplayName("TC_05: Phân loại gói tin CHAT_OK và kích hoạt callback onMessageSentSuccess")
    void testDispatchChatOkInvokesOnMessageSentSuccess() {
        AtomicReference<String> receivedMsgId = new AtomicReference<>();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onMessageSentSuccess(String messageId) {
                receivedMsgId.set(messageId);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        ProtocolMessage chatOk = new ProtocolMessage(MessageType.CHAT_OK);
        chatOk.messageId = "uuid-chat-ok-8888";

        receiver.dispatchMessage(chatOk);

        assertNotNull(receivedMsgId.get());
        assertEquals("uuid-chat-ok-8888", receivedMsgId.get());
    }

    @Test
    @DisplayName("TC_06: Phân loại gói tin ERROR và kích hoạt callback onErrorReceived")
    void testDispatchErrorInvokesOnErrorReceived() {
        AtomicReference<String> errCode = new AtomicReference<>();
        AtomicReference<String> errMsg = new AtomicReference<>();
        ChatListener listener = new StubChatListener() {
            @Override
            public void onErrorReceived(String errorCode, String errorMessage) {
                errCode.set(errorCode);
                errMsg.set(errorMessage);
            }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        ProtocolMessage errorMsg = new ProtocolMessage(MessageType.ERROR);
        errorMsg.errorCode = "USERNAME_ALREADY_EXISTS";
        errorMsg.errorMessage = "Tên đăng nhập đã tồn tại trên Server";

        receiver.dispatchMessage(errorMsg);

        assertEquals("USERNAME_ALREADY_EXISTS", errCode.get());
        assertEquals("Tên đăng nhập đã tồn tại trên Server", errMsg.get());
    }

    @Test
    @DisplayName("TC_07: Xử lý an toàn khi gói tin null hoặc type không xác định")
    void testDispatchUnknownOrNullMessageTypeDoesNotThrow() {
        AtomicBoolean anyCallbackCalled = new AtomicBoolean(false);
        ChatListener listener = new StubChatListener() {
            @Override public void onLoginSuccess(ProtocolMessage m) { anyCallbackCalled.set(true); }
            @Override public void onUserListUpdated(List<UserProfile> u) { anyCallbackCalled.set(true); }
            @Override public void onMessageReceived(ProtocolMessage m) { anyCallbackCalled.set(true); }
            @Override public void onMessageSentSuccess(String id) { anyCallbackCalled.set(true); }
            @Override public void onErrorReceived(String c, String m) { anyCallbackCalled.set(true); }
        };

        ChatReceiver receiver = new ChatReceiver(client, dummyReader, listener);

        assertDoesNotThrow(() -> receiver.dispatchMessage(null));
        assertDoesNotThrow(() -> receiver.dispatchMessage(new ProtocolMessage())); // type = null

        ProtocolMessage unhandledMsg = new ProtocolMessage(MessageType.DISCONNECT);
        assertDoesNotThrow(() -> receiver.dispatchMessage(unhandledMsg));

        assertFalse(anyCallbackCalled.get(), "Không được gọi nhầm callback khi type lạ/null");
    }

    @Test
    @DisplayName("TC_08: An toàn tuyệt đối khi listener là null")
    void testDispatchWithNullListenerDoesNotThrow() {
        ChatReceiver receiver = new ChatReceiver(client, dummyReader, null);

        ProtocolMessage chatMsg = new ProtocolMessage(MessageType.CHAT);
        chatMsg.content = "Test";

        assertDoesNotThrow(() -> receiver.dispatchMessage(chatMsg));
    }

    private static class StubChatListener implements ChatListener {
        @Override public void onLoginSuccess(ProtocolMessage message) {}
        @Override public void onUserListUpdated(List<UserProfile> users) {}
        @Override public void onMessageReceived(ProtocolMessage message) {}
        @Override public void onMessageSentSuccess(String messageId) {}
        @Override public void onErrorReceived(String errorCode, String errorMessage) {}
        @Override public void onConnectionLost(Throwable cause) {}
    }
}
