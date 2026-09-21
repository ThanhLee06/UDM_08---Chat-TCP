package vn.edu.ut.udm08.server.repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ut.udm08.server.config.DatabaseConnectionFactory;
import vn.edu.ut.udm08.server.model.ChatMessage;
import vn.edu.ut.udm08.shared.model.User;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class MessageDaoTest {
    private File tempDbFile;
    private DatabaseConnectionFactory connectionFactory;
    private UserRepository userRepository;
    private MessageDao messageDao;
    @BeforeEach
    void setUp() throws Exception {
        tempDbFile = File.createTempFile("test_msg_dao_", ".db");
        String dbUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();
        connectionFactory = new DatabaseConnectionFactory(dbUrl);
        userRepository = new UserRepository(dbUrl);
        messageDao = new MessageDao(connectionFactory);
        User user = new User();
        user.setUsername("hieu_test");
        user.setPhoneNumber("0901111222");
        user.setPasswordHash("hash123");
        userRepository.save(user);
        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO conversations (conv_id, type, name) VALUES ('conv_test_1', 'GROUP', 'Nhóm Test')");
        }
    }
    @AfterEach
    void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }
    @Test
    void shouldInsertAndFindMessageByMessageId() {
        ChatMessage msg = new ChatMessage("msg_001", "conv_test_1", "hieu_test", "Nội dung thử nghiệm", System.currentTimeMillis());
        ChatMessage saved = messageDao.insertMessage(msg);
        assertNotNull(saved.getSequenceId());
        assertTrue(saved.getSequenceId() > 0);
        Optional<ChatMessage> found = messageDao.findByMessageId("msg_001");
        assertTrue(found.isPresent());
        assertEquals("msg_001", found.get().getMessageId());
        assertEquals("conv_test_1", found.get().getConvId());
        assertEquals("hieu_test", found.get().getSenderUsername());
        assertEquals("Nội dung thử nghiệm", found.get().getContent());
    }
    @Test
    void shouldPreserveVietnameseAndEmojiContent() {
        String emojiText = "Xin chào! 😀🇻🇳 trân trọng cảm ơn 🚀";
        ChatMessage msg = new ChatMessage("msg_emoji", "conv_test_1", "hieu_test", emojiText, System.currentTimeMillis());
        messageDao.insertMessage(msg);
        Optional<ChatMessage> found = messageDao.findByMessageId("msg_emoji");
        assertTrue(found.isPresent());
        assertEquals(emojiText, found.get().getContent());
    }
    @Test
    void shouldPreserveReplyAndForwardMetadata() {
        ChatMessage replyMsg = new ChatMessage("msg_reply", "conv_test_1", "hieu_test", "Phản hồi", System.currentTimeMillis());
        replyMsg.setKind("reply");
        replyMsg.setReplyToMessageId("msg_parent_001");
        messageDao.insertMessage(replyMsg);
        Optional<ChatMessage> foundReply = messageDao.findByMessageId("msg_reply");
        assertTrue(foundReply.isPresent());
        assertEquals("reply", foundReply.get().getKind());
        assertEquals("msg_parent_001", foundReply.get().getReplyToMessageId());
        ChatMessage fwdMsg = new ChatMessage("msg_fwd", "conv_test_1", "hieu_test", "Chuyển tiếp", System.currentTimeMillis());
        fwdMsg.setKind("forward");
        fwdMsg.setForwardFromMessageId("orig_msg_99");
        fwdMsg.setForwardFromConvId("orig_conv_88");
        messageDao.insertMessage(fwdMsg);
        Optional<ChatMessage> foundFwd = messageDao.findByMessageId("msg_fwd");
        assertTrue(foundFwd.isPresent());
        assertEquals("forward", foundFwd.get().getKind());
        assertEquals("orig_msg_99", foundFwd.get().getForwardFromMessageId());
        assertEquals("orig_conv_88", foundFwd.get().getForwardFromConvId());
    }
    @Test
    void shouldPaginateMessagesChronologically() {
        for (int i = 1; i <= 5; i++) {
            ChatMessage msg = new ChatMessage("msg_seq_" + i, "conv_test_1", "hieu_test", "Tin thứ " + i, System.currentTimeMillis() + i);
            messageDao.insertMessage(msg);
        }
        List<ChatMessage> page1 = messageDao.findByConvId("conv_test_1", null, 3);
        assertEquals(3, page1.size());
        assertEquals("msg_seq_3", page1.get(0).getMessageId());
        assertEquals("msg_seq_4", page1.get(1).getMessageId());
        assertEquals("msg_seq_5", page1.get(2).getMessageId());
        Long cursor = page1.get(0).getSequenceId();
        List<ChatMessage> page2 = messageDao.findByConvId("conv_test_1", cursor, 3);
        assertEquals(2, page2.size());
        assertEquals("msg_seq_1", page2.get(0).getMessageId());
        assertEquals("msg_seq_2", page2.get(1).getMessageId());
    }
    @Test
    void shouldReturnEmptyWhenMessageIdDoesNotExist() {
        Optional<ChatMessage> found = messageDao.findByMessageId("non_existing_id");
        assertTrue(found.isEmpty());
    }
}