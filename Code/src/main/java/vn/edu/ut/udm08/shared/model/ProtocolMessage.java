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
    public String targetUserId;
    public String content;
    public String keyword;
    public String avatarId;
    public Long timestamp;
    public Long sequence;
    public String status;
    public List<UserProfile> users;
    public List<ProtocolMessage> messages;
    public List<ConversationSummary> conversations;
    public ConversationSummary conversation;
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
    public String replyToMessageId;
    public String replyToSender;
    public String replyToContent;
    public String forwardedFromSender;
    public boolean isForwarded;
    public MessageSendStatus sendStatus;
    public Boolean isNew;
    public String chatType;
    public String displayName;
    public String avatar;

    public ProtocolMessage() {}

    public ProtocolMessage(MessageType type) {
        this.type = type;
    }
}
