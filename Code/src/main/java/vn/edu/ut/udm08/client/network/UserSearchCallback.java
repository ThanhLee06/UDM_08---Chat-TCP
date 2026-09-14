package vn.edu.ut.udm08.client.network;

public interface UserSearchCallback {
    void onSuccess(UserSearchResult result);

    void onFailure(String requestId, String errorCode, String errorMessage);
}
