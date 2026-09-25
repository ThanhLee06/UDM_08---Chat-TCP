package vn.edu.ut.udm08.server.handler;
import java.io.IOException;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.*;
public final class PhoneLookupHandler {
    private final IUserRepository users;
    public PhoneLookupHandler(IUserRepository users){this.users=users;}
    public void handle(ClientSession session,ProtocolMessage request){
        ProtocolMessage response=new ProtocolMessage(MessageType.PHONE_LOOKUP_RESPONSE);response.requestId=request.requestId;
        String phone=request.content==null?"":request.content.trim();
        response.users=java.util.List.of();
        if(!phone.matches("(?:0[35789][0-9]{8}|\\+84[35789][0-9]{8})")) {response.type=MessageType.ERROR;response.errorCode="INVALID_PHONE";response.errorMessage="Nhập đầy đủ số điện thoại Việt Nam (10 số hoặc +84).";}
        else users.findByPhoneNumber(phone).filter(user->!user.getId().equals(session.getUser().getId())).ifPresent(user->{UserProfile profile=new UserProfile(user.getUsername(),user.getAvatarPath());profile.userId=String.valueOf(user.getId());profile.displayName=user.getDisplayName();response.users=java.util.List.of(profile);});
        try{session.sendMessage(response);}catch(IOException e){session.close();}
    }
}
