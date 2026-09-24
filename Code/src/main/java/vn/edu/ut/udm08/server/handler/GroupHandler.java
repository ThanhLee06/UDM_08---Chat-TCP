package vn.edu.ut.udm08.server.handler;
import java.io.IOException;
import java.util.HashSet;
import vn.edu.ut.udm08.server.repository.GroupRepository;
import vn.edu.ut.udm08.server.session.*;
import vn.edu.ut.udm08.shared.dto.*;
import vn.edu.ut.udm08.shared.model.*;
import vn.edu.ut.udm08.shared.protocol.JsonUtil;
public final class GroupHandler {
    private final GroupRepository repository;
    private final OnlineUserRegistry online;
    public GroupHandler(GroupRepository repository,OnlineUserRegistry online){this.repository=repository;this.online=online;}
    public void handle(ClientSession session,ProtocolMessage request) {
        ProtocolMessage response=new ProtocolMessage(MessageType.GROUP_RESPONSE);response.requestId=request.requestId;
        try {
            GroupCommand command=JsonUtil.fromJson(request.content,GroupCommand.class);
            HashSet<String> affected=new HashSet<>();
            if(command!=null && !"CREATE".equals(command.action)) {
                GroupCommand get=new GroupCommand();get.action="GET";get.convId=command.convId;
                repository.execute(session.getUser().getId(),get).members.forEach(member -> affected.add(member.username));
            }
            GroupDetails details=repository.execute(session.getUser().getId(),command);
            response.content=JsonUtil.toJson(details);send(session,response);
            if(!"GET".equals(command.action)) {
                details.members.forEach(member -> affected.add(member.username));
                ProtocolMessage changed=new ProtocolMessage(MessageType.CONVERSATION_CHANGED);changed.convId=details.convId;
                for(ClientSession peer:online.getSessions()) if(affected.contains(peer.getUsername())) {
                    try{peer.sendMessage(changed);}catch(IOException ignored){}
                }
            }
        }catch(Exception e){response.type=MessageType.ERROR;response.errorCode="GROUP_ERROR";response.errorMessage=e instanceof IllegalArgumentException?e.getMessage():"Không xử lý được nhóm. Vui lòng thử lại.";send(session,response);}
    }
    private void send(ClientSession session, ProtocolMessage message) {
        try { session.sendMessage(message); } catch (IOException e) { session.close(); }
    }
}
