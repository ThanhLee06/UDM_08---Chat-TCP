package vn.edu.ut.udm08.server.repository;

import vn.edu.ut.udm08.shared.model.User;
import java.util.Optional;

public interface IUserRepository {
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    default Optional<User> findByUsername(String username) { return Optional.empty(); }
    User save(User user);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean updatePassword(String phoneNumber, String newPasswordHash);
    default java.util.List<User> findAll() { return java.util.Collections.emptyList(); }
    default java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> searchUsers(String query, String excludeUsername, int limit) { return searchUsers(query, 0L, excludeUsername, limit); }
    default java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> searchUsers(String query, long excludeUserId, String excludeUsername, int limit) { return java.util.Collections.emptyList(); }
    default java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> searchUsers(String query, long excludeUserId, int limit) { return searchUsers(query, excludeUserId, null, limit); }
    default boolean deleteByPhoneNumber(String phoneNumber) { return false; }
    default boolean deleteById(long id) { return false; }
    default boolean deleteAll() { return false; }
}
