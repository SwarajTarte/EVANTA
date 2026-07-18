package com.example.evanta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit = null;
    private static Retrofit retrofitWithNulls = null;

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request()
                            .newBuilder()
                            .addHeader("apikey", SupabaseConfig.API_KEY)
                            .addHeader("Authorization",
                                    "Bearer " + SupabaseConfig.API_KEY)
                            .build();
                    return chain.proceed(request);
                })
                .build();
    }

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(SupabaseConfig.BASE_URL)
                    .client(buildClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static Retrofit getClientWithNulls() {
        if (retrofitWithNulls == null) {
            Gson gson = new GsonBuilder().serializeNulls().create();
            retrofitWithNulls = new Retrofit.Builder()
                    .baseUrl(SupabaseConfig.BASE_URL)
                    .client(buildClient())
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofitWithNulls;
    }
}