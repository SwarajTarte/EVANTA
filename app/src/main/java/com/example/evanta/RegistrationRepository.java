package com.example.evanta;

import java.util.List;

import retrofit2.Call;

public class RegistrationRepository {

    private final SupabaseApi api;

    public RegistrationRepository() {
        api = RetrofitClient.getClient().create(SupabaseApi.class);
    }

    public Call<List<Registration>> registerForEvent(String userUid, String eventId) {
        return api.registerForEvent(new Registration(userUid, eventId));
    }

    public Call<List<Registration>> checkRegistration(String userUid, String eventId) {
        return api.checkRegistration("eq." + userUid, "eq." + eventId);
    }

    public Call<List<Registration>> getRegistrationsForUser(String userUid) {
        return api.checkRegistration("eq." + userUid, null);
    }

    public Call<List<Registration>> getRegistrationsForEvent(String eventId) {
        return api.getRegistrationsByEvent("eq." + eventId);
    }
}
