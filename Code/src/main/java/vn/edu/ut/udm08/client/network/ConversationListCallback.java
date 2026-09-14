package vn.edu.ut.udm08.client.network;

public interface ConversationListCallback {
    void onSuccess(ConversationListResult result);

    void onFailure(String requestId, String errorCode, String errorMessage);
}
