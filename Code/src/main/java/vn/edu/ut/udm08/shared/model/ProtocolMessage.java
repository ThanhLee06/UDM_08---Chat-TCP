package vn.edu.ut.udm08.shared.model;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtocolMessage {
    public MessageType type;
    public String messageId;
    public String sender;
    public String target;
    public String content;
    public String avatarId;
    public Long timestamp;
    public List<UserProfile> users;
    public String errorCode;
    public String errorMessage;
    public String replyToMessageId;
    public String replyToSender;
public String replyToContent;
    public ProtocolMessage() {}
    public ProtocolMessage(MessageType type) {
        this.type = type;
    }
}