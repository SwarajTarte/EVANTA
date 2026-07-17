package com.example.evanta;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseApi {
    @GET("rest/v1/users?select=*")
    Call<List<Object>> getUsers();
    @POST("rest/v1/users")
    Call<Void> upsertUser(@Body User user);
    @GET("rest/v1/users?select=*")
    Call<List<User>> getUserByUid(@Query("uid") String uid);

    @retrofit2.http.PATCH("rest/v1/users")
    Call<Void> updateUser(@Query("uid") String uidFilter, @Body java.util.Map<String, Object> fields);

    @GET("rest/v1/events?select=*")
    Call<List<Event>> getFeaturedEvents(
            @Query("is_featured") String isFeatured,
            @Query("order") String order,
            @Query("limit") int limit);
}