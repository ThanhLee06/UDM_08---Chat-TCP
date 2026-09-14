package vn.edu.ut.udm08.client.network;

public interface MessageHistoryCallback {
    void onSuccess(MessageHistoryPage page);

    void onFailure(String requestId, String errorCode, String errorMessage);
}
