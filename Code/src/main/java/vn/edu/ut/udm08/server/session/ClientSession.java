package vn.edu.ut.udm08.server.session;

import vn.edu.ut.udm08.shared.model.ProtocolMessage;

public interface ClientSession {
    String getUsername();
    void setUsername(String username);
    String getAvatarId();
    void setAvatarId(String avatarId);
    void sendMessage(ProtocolMessage message) throws Exception;
    boolean isConnected();
    void close();
}
