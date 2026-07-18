package com.example.evanta;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseApi {

    @GET("rest/v1/users?select=*")
    Call<List<Object>> getUsers();

    @POST("rest/v1/users")
    Call<Void> upsertUser(@Body User user);

    @GET("rest/v1/users?select=*")
    Call<List<User>> getUserByUid(@Query("uid") String uid);

    @PATCH("rest/v1/users")
    Call<Void> updateUser(@Query("uid") String uidFilter, @Body Map<String, Object> fields);

    @GET("rest/v1/events?select=*")
    Call<List<Event>> getFeaturedEvents(
            @Query("is_featured") String isFeatured,
            @Query("order") String order,
            @Query("limit") int limit);

    @GET("rest/v1/events?select=*")
    Call<List<Event>> getEvents(
            @Query("category") String category,
            @Query("title") String title,
            @Query("order") String order);

    /**
     * Register a user for an event. Uses Prefer: return=representation so the
     * server echoes back the newly created row (including the generated id).
     * Supabase will return 409 if a UNIQUE(user_uid, event_id) violation occurs.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/registrations")
    Call<List<Registration>> registerForEvent(@Body Registration registration);

    /**
     * Check whether a specific (user, event) pair already exists in the table.
     * Returns an empty list if not registered, or a list with one element if registered.
     * PostgREST eq. filter format: "eq.{value}"
     */
    @GET("rest/v1/registrations?select=id,event_id,certificate_url")
    Call<List<Registration>> checkRegistration(
            @Query("user_uid") String userUid,
            @Query("event_id") String eventId);

    @GET("rest/v1/registrations?select=id,user_uid,event_id,certificate_url,registered_at")
    Call<List<Registration>> getRegistrationsByEvent(@Query("event_id") String eventId);

    /**
     * Query events matching a filter of IDs (e.g. "in.(1,2,3)").
     * Uses PostgREST in. filter format.
     */
    @GET("rest/v1/events?select=*")
    Call<List<Event>> getEventsByIds(
            @Query("id") String idsFilter,
            @Query("order") String order);

    @GET("rest/v1/notifications?select=*&order=created_at.desc")
    Call<List<Notification>> getNotifications(
            @Query("user_uid") String userUidFilter);

    @GET("rest/v1/notifications?select=*&order=created_at.desc")
    Call<List<Notification>> getBroadcastNotifications(
            @Query("user_uid") String isNull);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/notifications")
    Call<Void> markNotificationRead(
            @Query("id") String idFilter,
            @Body Map<String, Object> fields);

    @GET("rest/v1/colleges?select=*&order=name.asc")
    Call<List<College>> getColleges();

    @GET("rest/v1/colleges?select=*")
    Call<List<College>> getCollegeById(@Query("id") String idFilter);

    @GET("rest/v1/events?select=*")
    Call<List<Event>> getFeaturedEventsByCollege(
            @Query("is_featured") String isFeatured,
            @Query("college_id") String collegeIdFilter,
            @Query("order") String order,
            @Query("limit") int limit);

    @GET("rest/v1/events?select=*")
    Call<List<Event>> getEventsByCollege(
            @Query("college_id") String collegeIdFilter,
            @Query("order") String order);
}
