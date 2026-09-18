package vn.edu.ut.udm08.server.search;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import vn.edu.ut.udm08.server.repository.IUserRepository;
import vn.edu.ut.udm08.server.session.ClientSession;
import vn.edu.ut.udm08.shared.model.MessageType;
import vn.edu.ut.udm08.shared.model.ProtocolMessage;
import vn.edu.ut.udm08.shared.model.UserProfile;

public class UserSearchHandler {
    private final IUserRepository userRepository;

    public UserSearchHandler(IUserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("userRepository != null");
        }
        this.userRepository = userRepository;
    }
    public void handleSearchRequest(ClientSession session, ProtocolMessage message) {
        if (session == null || !session.isAuthenticated()) {
            return;
        }
        if (message == null || message.type != MessageType.USER_SEARCH_REQUEST) {
            return;
        }
        String keyword = message.keyword != null ? message.keyword.trim() : "";
        int limit = (message.limit != null && message.limit > 0 && message.limit <= 20) ? message.limit : 20;

        List<UserProfile> results;
        if (keyword.isBlank()) {
            results = Collections.emptyList();
        } else {
            long currentUserId = (session.getUser() != null) ? session.getUser().getId() : 0L;
            results = userRepository.searchUsers(keyword, currentUserId, session.getUsername(), 20);
        }

        ProtocolMessage response = new ProtocolMessage(MessageType.USER_SEARCH_RESPONSE);
        response.messageId = message.messageId;
        response.requestId = message.requestId;
        response.sender = "SERVER";
        response.target = session.getUsername();
        response.users = results;
        response.timestamp = System.currentTimeMillis();
        try {
            session.sendMessage(response);
        } catch (IOException ignored) {
        }
    }
}
