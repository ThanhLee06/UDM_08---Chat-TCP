package vn.edu.ut.udm08.server.service;
import vn.edu.ut.udm08.server.model.ChatMessage;
public interface IChatStorageService {
    ChatMessage saveMessageWithTransaction(ChatMessage message);
}
