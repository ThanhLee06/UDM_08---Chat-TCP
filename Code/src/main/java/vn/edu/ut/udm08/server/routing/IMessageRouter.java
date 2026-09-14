package vn.edu.ut.udm08.server.routing;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
public interface IMessageRouter {
    void handleChatMessage(ClientSession senderSession, ProtocolMessage msg);
}
