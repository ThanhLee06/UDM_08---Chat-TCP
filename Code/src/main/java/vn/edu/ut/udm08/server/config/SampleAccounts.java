package vn.edu.ut.udm08.server.config;

import java.io.IOException;
import java.util.Properties;
import vn.edu.ut.udm08.server.core.ServerConfig;
import vn.edu.ut.udm08.server.repository.UserRepository;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.security.PasswordEncoder;

public final class SampleAccounts {
    private SampleAccounts() {}

    public static void initialize(ServerConfig config) throws IOException {
        var factory = new DatabaseConnectionFactory(config.getDbUrl(), config.getDbBusyTimeout());
        new DatabaseInitializer(factory).initialize();
        var users = new UserRepository(factory);
        Properties accounts = new Properties();
        try (var input = SampleAccounts.class.getResourceAsStream("/sample-accounts.properties")) {
            if (input == null) throw new IOException("Missing sample-accounts.properties");
            accounts.load(input);
        }
        PasswordEncoder encoder = new PasswordEncoder();
        for (String username : accounts.getProperty("accounts").split(",")) {
            String email = accounts.getProperty(username + ".email");
            if (users.findByEmail(email).isPresent()) continue;
            String phone = accounts.getProperty(username + ".phone");
            if (users.existsByUsername(username) || users.existsByPhoneNumber(phone)) continue;
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPhoneNumber(phone);
            user.setPasswordHash(encoder.encode(accounts.getProperty(username + ".password")));
            user.setAvatarType("PRESET");
            user.setAvatarPath(accounts.getProperty(username + ".avatar"));
            if (users.save(user) == null) throw new IOException("Cannot create sample account: " + username);
        }
    }
}
