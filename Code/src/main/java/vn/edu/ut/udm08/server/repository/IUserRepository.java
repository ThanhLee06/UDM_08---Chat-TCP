package vn.edu.ut.udm08.server.repository;

import vn.edu.ut.udm08.shared.model.User;
import java.util.Optional;

public interface IUserRepository {
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);
    User save(User user);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean updatePassword(String phoneNumber, String newPasswordHash);
    default java.util.List<User> findAll() { return java.util.Collections.emptyList(); }
    default boolean deleteByPhoneNumber(String phoneNumber) { return false; }
    default boolean deleteById(long id) { return false; }
    default boolean deleteAll() { return false; }
}
