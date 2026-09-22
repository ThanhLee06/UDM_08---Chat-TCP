package vn.edu.ut.udm08.server.validation;
import vn.edu.ut.udm08.server.core.ServerConfig;
public class InputValidator {
    
    private final int messageMaxLength;
    private final int historyMaxLimit;
    private final int searchMaxLength;
    
    public InputValidator() {
        this(ServerConfig.load());
    }

    public InputValidator(ServerConfig config) {
        this(config.getMessageMaxLength(), config.getHistoryMaxLimit(), config.getSearchMaxLength());
    }

    public InputValidator(int messageMaxLength, int historyMaxLimit, int searchMaxLength) {
        this.messageMaxLength = messageMaxLength;
        this.historyMaxLimit = historyMaxLimit;
        this.searchMaxLength = searchMaxLength;
    }

    public boolean validateMessageContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.length() <= messageMaxLength;
    }

    public int normalizeHistoryLimit(int limit) {
        if (limit <= 0) {
            return 30;
        }
        return Math.min(limit, historyMaxLimit);
    }

    public boolean validateSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return keyword.length() <= searchMaxLength;
    }
}