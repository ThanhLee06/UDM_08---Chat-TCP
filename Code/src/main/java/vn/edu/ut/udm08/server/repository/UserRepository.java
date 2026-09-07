package vn.edu.ut.udm08.server.repository;
import vn.edu.ut.udm08.shared.model.User;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Optional;
public class UserRepository implements IUserRepository {
    private final String dbUrl = "jdbc:sqlite:udm08_chat.db";
    public UserRepository() {
        initDatabase();
    }
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }
    private void initDatabase() {
        try (InputStream is = getClass().getResourceAsStream("/db/migration/V2__auth.sql")) {
            if (is == null) {
                return;
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("ALTER TABLE users DROP COLUMN email");
                } catch (SQLException ignored) {
                }
                stmt.execute(sql);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                INSERT INTO users (username, phone_number, password_hash, avatar_type, avatar_path)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPhoneNumber());
            pstmt.setString(3, user.getPasswordHash());
            pstmt.setString(4, user.getAvatarType());
            pstmt.setString(5, user.getAvatarPath());
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
    public boolean deleteByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return false;
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
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPhoneNumber(rs.getString("phone_number"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setAvatarType(rs.getString("avatar_type"));
        user.setAvatarPath(rs.getString("avatar_path"));
        user.setGender(rs.getString("gender"));
        user.setBio(rs.getString("bio"));
        user.setCoverUrl(rs.getString("cover_url"));
        return user;
    }
}