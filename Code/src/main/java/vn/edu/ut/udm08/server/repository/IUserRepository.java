package vn.edu.ut.udm08.server.repository;

import vn.edu.ut.udm08.shared.model.User;
import java.util.Optional;

public interface IUserRepository {
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    User save(User user);
    Optional<User> findByPhoneNumber(String phoneNumber);
}
