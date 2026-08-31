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
        String sql = "SELECT id FROM users WHERE phone_number = ?";
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, phoneNumber != null ? phoneNumber.trim() : "");
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT id FROM users WHERE LOWER(email) = ?";
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email != null ? email.trim().toLowerCase() : "");
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
                INSERT INTO users (username, phone_number, email, password_hash, avatar_type, avatar_path)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPhoneNumber());
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getPasswordHash());
            pstmt.setString(5, user.getAvatarType());
            pstmt.setString(6, user.getAvatarPath());
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                user.setId(rs.getLong(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return user;
    }
    @Override
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM users WHERE phone_number = ?";
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, phoneNumber.trim());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        return Optional.empty();
    }
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPhoneNumber(rs.getString("phone_number"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setAvatarType(rs.getString("avatar_type"));
        user.setAvatarPath(rs.getString("avatar_path"));
        user.setGender(rs.getString("gender"));
        user.setBio(rs.getString("bio"));
        user.setCoverUrl(rs.getString("cover_url"));
        return user;
    }
}