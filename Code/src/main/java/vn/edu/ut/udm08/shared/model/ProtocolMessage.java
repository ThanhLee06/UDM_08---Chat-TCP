package vn.edu.ut.udm08.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtocolMessage {
    public MessageType type;
    public String messageId;
    public String requestId;
    public String sender;
    public String target;
    public String content;
    public String avatarId;
    public Long timestamp;
    public List<UserProfile> users;
    public List<ProtocolMessage> messages;
    public String convId;
    public String cursor;
    public Integer limit;
    public String nextCursor;
    public Boolean hasMore;
    public String replyTo;
    public String fwdFrom;
    public String kind;
    public String errorCode;
    public String errorMessage;
    public MessageSendStatus sendStatus;

    public ProtocolMessage() {}

    public ProtocolMessage(MessageType type) {
        this.type = type;
    }
}
