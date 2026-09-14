package vn.edu.ut.udm08.client.network;

public interface OpenDmCallback {
    void onSuccess(OpenDmResult result);

    void onFailure(String requestId, String errorCode, String errorMessage);
}
