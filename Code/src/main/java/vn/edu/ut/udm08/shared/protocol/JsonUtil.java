package vn.edu.ut.udm08.shared.protocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private JsonUtil() {}
    public static String toJson(ProtocolMessage message) throws JsonProcessingException {
        return MAPPER.writeValueAsString(message);
    }
    public static ProtocolMessage fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, ProtocolMessage.class);
    }
}