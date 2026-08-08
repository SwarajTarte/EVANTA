package com.evanta.app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AiApiService {
    @POST("functions/v1/ai-assistant")
    Call<AiResponse> getAiResponse(@Body AiRequest request);
}