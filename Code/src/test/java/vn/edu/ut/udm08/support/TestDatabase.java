package vn.edu.ut.udm08.support;

import vn.edu.ut.udm08.server.config.*;
import vn.edu.ut.udm08.server.repository.UserRepository;

public final class TestDatabase {
    private TestDatabase() {}
    public static UserRepository repository() { return repository(new DatabaseConnectionFactory()); }
    public static UserRepository repository(String url) { return repository(new DatabaseConnectionFactory(url)); }
    public static UserRepository repository(DatabaseConnectionFactory factory) {
        new DatabaseInitializer(factory).initialize();
        return new UserRepository(factory);
    }
}
