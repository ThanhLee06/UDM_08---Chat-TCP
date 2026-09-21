package vn.edu.ut.udm08.server.repository;
import vn.edu.ut.udm08.shared.model.User;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Optional;
public class UserRepository implements IUserRepository {
    private final vn.edu.ut.udm08.server.config.DatabaseConnectionFactory connectionFactory;

    public UserRepository() {
        this(new vn.edu.ut.udm08.server.config.DatabaseConnectionFactory());
    }

    public UserRepository(String dbUrl) {
        this(new vn.edu.ut.udm08.server.config.DatabaseConnectionFactory(dbUrl));
    }

    public UserRepository(vn.edu.ut.udm08.server.config.DatabaseConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("ConnectionFactory != null");
        }
        this.connectionFactory = connectionFactory;
    }

    private Connection getConnection() throws SQLException {
        return connectionFactory.getConnection();
    }
    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }
    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE lower(email) = ?")) {
            stmt.setString(1, email.trim().toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rows = stmt.executeQuery()) {
                return rows.next() ? Optional.of(mapResultSetToUser(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu email", e);
        }
    }
    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE LOWER(username) = ?")) {
            stmt.setString(1, username.trim().toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rows = stmt.executeQuery()) {
                return rows.next() ? Optional.of(mapResultSetToUser(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu username", e);
        }
    }
    @Override
    public Optional<User> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rows = stmt.executeQuery()) {
                return rows.next() ? Optional.of(mapResultSetToUser(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tra cứu user id", e);
        }
    }
    @Override
    public boolean existsByUsername(String username) {
        String sql = "SELECT id FROM users WHERE LOWER(username) = ?";
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username != null ? username.trim().toLowerCase() : "");
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        String raw = phoneNumber.trim();
        String clean = raw.replaceAll("[^0-9]", "");
        String e164 = clean.startsWith("0") ? "+84" + clean.substring(1) : (clean.startsWith("84") ? "+" + clean : "+84" + clean);
        String local = clean.startsWith("84") ? "0" + clean.substring(2) : (clean.startsWith("0") ? clean : "0" + clean);

        String sql = "SELECT id FROM users WHERE phone_number = ? OR phone_number = ? OR phone_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, raw);
            pstmt.setString(2, e164);
            pstmt.setString(3, local);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public User save(User user) {
        if (user == null) {
            return null;
        }
        String sql = """
                INSERT INTO users (username, phone_number, password_hash, avatar_type, avatar_path, email)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPhoneNumber());
            pstmt.setString(3, user.getPasswordHash());
            pstmt.setString(4, user.getAvatarType() != null ? user.getAvatarType() : "PRESET");
            pstmt.setString(5, user.getAvatarPath() != null ? user.getAvatarPath() : "01.png");
            pstmt.setString(6, (user.getEmail() == null || user.getEmail().isBlank()) ? null : user.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    user.setId(rs.getLong(1));
                }
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        String raw = phoneNumber.trim();
        String clean = raw.replaceAll("[^0-9]", "");
        String e164 = clean.startsWith("0") ? "+84" + clean.substring(1) : (clean.startsWith("84") ? "+" + clean : "+84" + clean);
        String local = clean.startsWith("84") ? "0" + clean.substring(2) : (clean.startsWith("0") ? clean : "0" + clean);

        String sql = "SELECT * FROM users WHERE phone_number = ? OR phone_number = ? OR phone_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, raw);
            pstmt.setString(2, e164);
            pstmt.setString(3, local);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    @Override
    public boolean updatePassword(String phoneNumber, String newPasswordHash) {
        if (phoneNumber == null || newPasswordHash == null) {
            return false;
        }
        String sql = "UPDATE users SET password_hash = ? WHERE phone_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, phoneNumber.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public java.util.List<User> findAll() {
        java.util.List<User> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    @Override
    public java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> searchUsers(String query, String excludeUsername, int limit) {
        return searchUsers(query, 0L, excludeUsername, limit);
    }
    @Override
    public java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> searchUsers(String query, long excludeUserId, int limit) {
        return searchUsers(query, excludeUserId, null, limit);
    }
    @Override
    public java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> searchUsers(String query, long excludeUserId, String excludeUsername, int limit) {
        if (query == null || query.isBlank() || query.length() > 200) {
            return java.util.Collections.emptyList();
        }
        int maxLimit = (limit <= 0 || limit > 20) ? 20 : limit;
        String rawQuery = query.trim();
        String normalizedPhone = rawQuery.replaceAll("\\s+", "");
        String trimmedQuery = rawQuery.toLowerCase(java.util.Locale.ROOT);
        String exclude = (excludeUsername != null) ? excludeUsername.trim().toLowerCase(java.util.Locale.ROOT) : "";
        String sql = """
                SELECT id, username, avatar_type, avatar_path,
                       CASE
                           WHEN phone_number = ? THEN 1
                           WHEN LOWER(username) = ? THEN 2
                           WHEN LOWER(username) LIKE ? THEN 3
                           WHEN LOWER(username) LIKE ? THEN 4
                           WHEN LOWER(COALESCE(email, '')) = ? THEN 5
                           ELSE 6
                       END AS search_rank
                FROM users
                WHERE id != ?
                  AND LOWER(username) != ?
                  AND (
                      LOWER(username) LIKE ?
                      OR phone_number = ?
                      OR LOWER(COALESCE(email, '')) = ?
                  )
                ORDER BY search_rank ASC, username ASC
                LIMIT ?
                """;
        java.util.List<vn.edu.ut.udm08.shared.model.UserProfile> list = new java.util.ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedPhone);
            stmt.setString(2, trimmedQuery);
            stmt.setString(3, trimmedQuery + "%");
            stmt.setString(4, "%" + trimmedQuery + "%");
            stmt.setString(5, trimmedQuery);
            stmt.setLong(6, excludeUserId);
            stmt.setString(7, exclude);
            stmt.setString(8, "%" + trimmedQuery + "%");
            stmt.setString(9, normalizedPhone);
            stmt.setString(10, trimmedQuery);
            stmt.setInt(11, maxLimit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    vn.edu.ut.udm08.shared.model.UserProfile profile = new vn.edu.ut.udm08.shared.model.UserProfile();
                    profile.userId = String.valueOf(rs.getLong("id"));
                    profile.username = rs.getString("username");
                    profile.displayName = rs.getString("username");
                    profile.avatarId = rs.getString("avatar_type");
                    profile.avatarPath = rs.getString("avatar_path");
                    list.add(profile);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    @Override
    public boolean deleteByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }
        String sql = "DELETE FROM users WHERE phone_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, phoneNumber.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public boolean deleteAll() {
        String sql = "DELETE FROM users";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    private boolean hasColumn(ResultSet rs, String columnName) {
        try {
            rs.findColumn(columnName);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPhoneNumber(rs.getString("phone_number"));
        user.setEmail(hasColumn(rs, "email") ? rs.getString("email") : null);
        user.setPasswordHash(rs.getString("password_hash"));
        user.setAvatarType(rs.getString("avatar_type"));
        user.setAvatarPath(rs.getString("avatar_path"));
        user.setGender(hasColumn(rs, "gender") ? rs.getString("gender") : null);
        user.setBio(hasColumn(rs, "bio") ? rs.getString("bio") : null);
        user.setCoverUrl(hasColumn(rs, "cover_url") ? rs.getString("cover_url") : null);
        return user;
    }
}
