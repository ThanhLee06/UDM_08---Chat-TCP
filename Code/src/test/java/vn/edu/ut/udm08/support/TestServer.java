package vn.edu.ut.udm08.support;

import java.nio.file.Files;
import java.util.Properties;
import vn.edu.ut.udm08.server.core.*;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.dto.AuthLoginRequest;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;

public final class TestServer {
    public static final String PASSWORD = "DemoPass123!";
    public static ChatServer create() throws Exception {
        Properties p = new Properties(); p.setProperty("server.port", "0");
        p.setProperty("db.url", "jdbc:sqlite:" + Files.createTempDirectory("udm08-test-").resolve("chat.db"));
        UserRepository repo = TestDatabase.repository(p.getProperty("db.url"));
        String hash = new vn.edu.ut.udm08.shared.security.PasswordEncoder().encode(PASSWORD);
        int i = 0;
        for (String name : new String[]{"alice", "bob", "charlie"}) {
            User user = new User(); user.setUsername(name); user.setEmail(name + "@example.test");
            user.setPhoneNumber("090000000" + (++i)); user.setPasswordHash(hash);
            user.setAvatarType("PRESET"); user.setAvatarPath("avatar" + i); repo.save(user);
        }
        return new ChatServer(ServerConfig.fromProperties(p));
    }
    public static ProtocolMessage login(String name) {
        ProtocolMessage message = new ProtocolMessage(MessageType.AUTH_LOGIN);
        message.requestId = java.util.UUID.randomUUID().toString();
        message.content = JsonUtil.toJson(new AuthLoginRequest(name + "@example.test", PASSWORD));
        return message;
    }
}
