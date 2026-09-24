package vn.edu.ut.udm08.server.model;
import java.util.Objects;
public class ChatMessage {
    private Long sequenceId;
    private String messageId;
    private String convId;
    private String senderUsername;
    private String content;
    private long timestamp;
    private String kind = "text";
    private String replyToMessageId;
    private String forwardFromMessageId;
    private String forwardFromConvId;
    public ChatMessage() {
    }
    public ChatMessage(String messageId, String convId, String senderUsername, String content, long timestamp) {
        this.messageId = messageId;
        this.convId = convId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.timestamp = timestamp;
    }
    public Long getSequenceId() {
        return sequenceId;
    }
    public void setSequenceId(Long sequenceId) {
        this.sequenceId = sequenceId;
    }
    public String getMessageId() {
        return messageId;
    }
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    public String getConvId() {
        return convId;
    }
    public void setConvId(String convId) {
        this.convId = convId;
    }
    public String getSenderUsername() {
        return senderUsername;
    }
    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    public String getKind() {
        return kind;
    }
    public void setKind(String kind) {
        this.kind = kind;
    }
    public String getReplyToMessageId() {
        return replyToMessageId;
    }
    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }
    public String getForwardFromMessageId() {
        return forwardFromMessageId;
    }
    public void setForwardFromMessageId(String forwardFromMessageId) {
        this.forwardFromMessageId = forwardFromMessageId;
    }
    public String getForwardFromConvId() {
        return forwardFromConvId;
    }
    public void setForwardFromConvId(String forwardFromConvId) {
        this.forwardFromConvId = forwardFromConvId;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return timestamp == that.timestamp && Objects.equals(sequenceId, that.sequenceId) && Objects.equals(messageId, that.messageId) && Objects.equals(convId, that.convId) && Objects.equals(senderUsername, that.senderUsername) && Objects.equals(content, that.content) && Objects.equals(kind, that.kind) && Objects.equals(replyToMessageId, that.replyToMessageId) && Objects.equals(forwardFromMessageId, that.forwardFromMessageId) && Objects.equals(forwardFromConvId, that.forwardFromConvId);
    }
    @Override
    public int hashCode() {
        return Objects.hash(sequenceId, messageId, convId, senderUsername, content, timestamp, kind, replyToMessageId, forwardFromMessageId, forwardFromConvId);
    }
}
