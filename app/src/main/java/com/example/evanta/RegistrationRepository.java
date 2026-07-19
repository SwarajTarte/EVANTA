package com.example.evanta;

import java.util.List;
import java.util.Map;

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

    /**
     * Reapply after a rejection: resets the existing row back to pending and
     * bumps the attempt counter. Reuses the PATCH endpoint keyed by row id.
     */
    public Call<Void> reapply(String registrationId, int newAttempts) {
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("status", Registration.STATUS_PENDING);
        fields.put("attempts", newAttempts);
        return api.updateRegistrationStatus("eq." + registrationId, fields);
    }
}
