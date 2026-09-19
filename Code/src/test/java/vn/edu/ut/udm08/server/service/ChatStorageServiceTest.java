package vn.edu.ut.udm08.server.service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.server.exception.MessageConflictException;
import vn.edu.ut.udm08.server.model.ChatMessage;
import vn.edu.ut.udm08.server.model.Conversation;
import vn.edu.ut.udm08.server.repository.ConversationDao;
import vn.edu.ut.udm08.server.repository.MessageDao;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.shared.model.User;
import java.io.File;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class ChatStorageServiceTest {
    private File tempDbFile;
    private DatabaseConnectionFactory connectionFactory;
    private UserRepository userRepository;
    private MessageDao messageDao;
    private ConversationDao conversationDao;
    private ChatStorageService chatStorageService;
    private User testUser;
    @BeforeEach
    void setUp() {
        try {
            tempDbFile = File.createTempFile("test_tx_service_", ".db");
            String dbUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();
            connectionFactory = new DatabaseConnectionFactory(dbUrl);
            userRepository = new UserRepository(dbUrl);
            messageDao = new MessageDao(connectionFactory);
            conversationDao = new ConversationDao(connectionFactory, messageDao);
            chatStorageService = new ChatStorageService(connectionFactory, messageDao, conversationDao);
            testUser = new User();
            testUser.setUsername("user_tx");
            testUser.setPhoneNumber("0909999888");
            testUser.setPasswordHash("hash_tx");
            userRepository.save(testUser);
            Conversation conv = new Conversation("conv_tx_1", "GROUP", "Nhóm Transaction");
            conversationDao.createConversation(conv);
            conversationDao.addMember("conv_tx_1", testUser.getId(), "owner");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @AfterEach
    void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }
    @Test
    void shouldSaveMessageAndUpdateConversationInTransaction() {
        long now = System.currentTimeMillis();
        ChatMessage msg = new ChatMessage("msg_tx_001", "conv_tx_1", "user_tx", "Nội dung giao dịch", now);
        ChatMessage saved = chatStorageService.saveMessageWithTransaction(msg);
        assertNotNull(saved.getSequenceId());
        assertTrue(saved.getSequenceId() > 0);
        Optional<Conversation> conv = conversationDao.findById("conv_tx_1");
        assertTrue(conv.isPresent());
        assertEquals("Nội dung giao dịch", conv.get().getLastMessagePreview());
        assertEquals(now, conv.get().getLastActivity());
    }
    @Test
    void shouldRollbackWhenConversationUpdateFails() {
        ChatMessage msg = new ChatMessage("msg_tx_fail", "conv_non_existing", "user_tx", "Lưu thất bại", System.currentTimeMillis());
        assertThrows(IllegalStateException.class, () -> chatStorageService.saveMessageWithTransaction(msg));
        Optional<ChatMessage> found = messageDao.findByMessageId("msg_tx_fail");
        assertTrue(found.isEmpty());
    }
    @Test
    void shouldHandleIdempotentRetryForIdenticalMessage() {
        long now = System.currentTimeMillis();
        ChatMessage msg1 = new ChatMessage("msg_tx_repeat", "conv_tx_1", "user_tx", "Lặp lại", now);
        ChatMessage saved1 = chatStorageService.saveMessageWithTransaction(msg1);
        ChatMessage msg2 = new ChatMessage("msg_tx_repeat", "conv_tx_1", "user_tx", "Lặp lại", now);
        ChatMessage saved2 = chatStorageService.saveMessageWithTransaction(msg2);
        assertEquals(saved1.getSequenceId(), saved2.getSequenceId());
        assertEquals(1, messageDao.findByConvId("conv_tx_1", null, 10).size());
    }
    @Test
    void shouldThrowConflictWhenMessageIdReusedWithDifferentContent() {
        long now = System.currentTimeMillis();
        ChatMessage msg1 = new ChatMessage("msg_tx_conflict", "conv_tx_1", "user_tx", "Nội dung 1", now);
        chatStorageService.saveMessageWithTransaction(msg1);
        ChatMessage msg2 = new ChatMessage("msg_tx_conflict", "conv_tx_1", "user_tx", "Nội dung 2 (Khác)", now);
        assertThrows(MessageConflictException.class, () -> chatStorageService.saveMessageWithTransaction(msg2));
    }
}
