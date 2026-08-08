package com.evanta.app;

import androidx.annotation.NonNull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiRepository {

    private final AiApiService apiService;

    public AiRepository(AiApiService apiService) {
        this.apiService = apiService;
    }

    public interface AiCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }

    // Student Feature: Get Event Recommendations
    public void getRecommendedEvents(String userPreferences, AiCallback callback) {
        sendRequest("student", userPreferences, callback);
    }

    // Admin Feature: Generate Event Summary
    public void generateEventSummary(String rawNotes, AiCallback callback) {
        sendRequest("admin", rawNotes, callback);
    }

    private void sendRequest(String role, String query, AiCallback callback) {
        AiRequest request = new AiRequest(role, query);

        // Explicitly specify <AiResponse> here
        apiService.getAiResponse(request).enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(@NonNull Call<AiResponse> call, @NonNull Response<AiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AiResponse body = response.body();
                    if (body.getError() != null) {
                        callback.onError(body.getError());
                    } else {
                        callback.onSuccess(body.getResult());
                    }
                } else {
                    String errorMsg = "Server error " + response.code();
                    if (response.errorBody() != null) {
                        try {
                            String errStr = response.errorBody().string();
                            org.json.JSONObject obj = new org.json.JSONObject(errStr);
                            if (obj.has("error")) {
                                errorMsg = obj.getString("error");
                            }
                        } catch (Exception ignored) {}
                    }
                    callback.onError(errorMsg);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AiResponse> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
}
