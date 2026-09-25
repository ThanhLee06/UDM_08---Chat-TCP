package vn.edu.ut.udm08.server.handler;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.service.AvatarStore;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.server.session.LoginHandler;
import vn.edu.ut.udm08.shared.dto.AuthUserDto;
import vn.edu.ut.udm08.shared.dto.ProfileUpdate;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public final class ProfileHandler {
    private final IUserRepository users;
    private final AvatarStore avatars;
    private final LoginHandler login;
    public ProfileHandler(IUserRepository users, AvatarStore avatars, LoginHandler login) {
        this.users = users;
        this.avatars = avatars;
        this.login = login;
    }
    public void handle(ClientSession session, ProtocolMessage request) {
        try {
            if (!session.isAuthenticated() || session.getUser() == null) throw new IllegalArgumentException("Vui lòng đăng nhập lại");
            var user = users.findById(session.getUser().getId()).orElseThrow();
            ProtocolMessage response = new ProtocolMessage(MessageType.PROFILE_RESPONSE);
            response.requestId = request.requestId;
            if (request.type == MessageType.AVATAR_GET) {
                response.type = MessageType.AVATAR_RESPONSE;
                response.content = avatars.read(request.content);
            } else {
                if (request.type == MessageType.PROFILE_UPDATE) {
                    ProfileUpdate update = JsonUtil.fromJson(request.content, ProfileUpdate.class);
                    if (update == null || update.displayName == null || update.displayName.isBlank()
                            || update.displayName.trim().length() > 50 || update.displayName.chars().anyMatch(Character::isISOControl)) {
                        throw new IllegalArgumentException("Tên hiển thị phải có 1–50 ký tự");
                    }
                    String avatar = update.avatar != null && update.avatar.equals(user.getAvatarPath()) ? update.avatar : avatars.save(update.avatar);
                    if (!users.updateProfile(user.getId(), update.displayName.trim(), avatar)) throw new IllegalStateException("Không lưu được hồ sơ");
                    user = users.findById(user.getId()).orElseThrow();
                    session.setUser(user);
                }
                response.content = JsonUtil.toJson(new AuthUserDto(user));
            }
            session.sendMessage(response);
            if (request.type == MessageType.PROFILE_UPDATE) login.broadcastUserList();
        } catch (Exception error) {
            ProtocolMessage response = new ProtocolMessage(MessageType.ERROR);
            response.requestId = request.requestId;
            response.errorCode = "PROFILE_ERROR";
            response.errorMessage = error instanceof IllegalArgumentException ? error.getMessage() : "Không thể xử lý hồ sơ. Vui lòng thử lại.";
            try { session.sendMessage(response); } catch (java.io.IOException failure) { session.close(); }
        }
    }
}
