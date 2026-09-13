package vn.edu.ut.udm08.shared.model;

/**
 * Constants for ProtocolMessage kind field.
 */
public final class MessageKind {
    public static final String TEXT = "text";
    public static final String REPLY = "reply";
    public static final String FORWARD = "forward";
    public static final String SYSTEM = "system";

    private MessageKind() {}
}
