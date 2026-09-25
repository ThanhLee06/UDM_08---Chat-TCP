package vn.edu.ut.udm08.server.handler;
import java.io.IOException;
import vn.edu.ut.udm08.server.repository.ReadStateRepository;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public final class ReadStateHandler {
    private final ReadStateRepository repository;
    public ReadStateHandler(ReadStateRepository repository) { this.repository=repository; }
    public void handle(ClientSession session, ProtocolMessage request) {
        ProtocolMessage response=new ProtocolMessage(MessageType.CONVERSATION_READ_OK);
        response.requestId=request.requestId;
        try {
            ProtocolMessage read=JsonUtil.fromJson(request.content);
            repository.markRead(session.getUser().getId(),read.convId,read.messageId);
        } catch (Exception e) {
            response.type=MessageType.ERROR; response.errorCode="READ_FAILED";
            response.errorMessage="Không cập nhật được trạng thái đã đọc";
        }
        send(session,response);
    }
    private void send(ClientSession session, ProtocolMessage message) {
        try { session.sendMessage(message); } catch (IOException e) { session.close(); }
    }
}
