package vn.edu.ut.udm08.support;

import vn.edu.ut.udm08.server.session.*;
import vn.edu.ut.udm08.shared.model.*;

/** Fixture for lifecycle unit tests after credentials have already been verified.
 * Actual credential checks and forbidden HELLO are covered by TCP integration tests. */
public final class TrustedLogin {
    private TrustedLogin() {}
    public static boolean establish(LoginHandler handler, ClientSession session, ProtocolMessage identity) {
        User user = new User();
        user.setId(1L + Integer.toUnsignedLong(identity.sender.hashCode()));
        user.setUsername(identity.sender);
        user.setAvatarPath(identity.avatarId);
        try {
            ProtocolMessage ack = new ProtocolMessage(MessageType.AUTH_LOGIN_OK);
            ack.target = identity.sender;
            session.sendMessage(ack);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        return handler.handleLoginSuccess(session, user);
    }
}
