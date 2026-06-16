package com.example.evanta;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

public interface SupabaseApi {
    @GET("rest/v1/users?select=*")
    Call<List<Object>> getUsers();
}