package vn.edu.ut.udm08.shared.mapper;
import vn.edu.ut.udm08.shared.dto.RegisterRequest;
import vn.edu.ut.udm08.shared.dto.RegisterResponse;
import vn.edu.ut.udm08.shared.model.User;
import vn.edu.ut.udm08.shared.model.UserProfile;
import java.time.LocalDateTime;
public class UserMapper {
    public static User toEntity(RegisterRequest request) {
        if (request == null) {
            return null;
        }
        User user = new User();
        if (request.getUsername() != null) {
            user.setUsername(request.getUsername().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim().toLowerCase());
        }
        user.setPasswordHash(request.getPassword());
        if (request.getAvatarType() != null) {
            user.setAvatarType(request.getAvatarType());
        } else {
            user.setAvatarType("PRESET");
        }
        if (request.getAvatarPath() != null) {
            user.setAvatarPath(request.getAvatarPath());
        } else {
            user.setAvatarPath("01.png");
        }
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
    public static UserProfile toProfile(User user) {
        if (user == null) {
            return null;
        }
        return new UserProfile(user.getUsername(), user.getAvatarPath());
    }
    public static RegisterResponse toRegisterResponse(boolean success, String message, User user) {
        return new RegisterResponse(success, message, user);
    }
}
