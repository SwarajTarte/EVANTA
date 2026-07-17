package com.example.evanta;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a single row in the Supabase `registrations` table.
 * Used both for POSTing a new registration and for reading existing ones.
 */
public class Registration {

    private String id;

    @SerializedName("user_uid")
    private String userUid;

    @SerializedName("event_id")
    private String eventId;

    @SerializedName("registered_at")
    private String registeredAt;

    @SerializedName("certificate_url")
    private String certificateUrl;

    /** Constructor used when creating a new registration (POST body). */
    public Registration(String userUid, String eventId) {
        this.userUid = userUid;
        this.eventId = eventId;
    }

    public String getId()           { return id; }
    public String getUserUid()      { return userUid; }
    public String getEventId()      { return eventId; }
    public String getRegisteredAt() { return registeredAt; }
    public String getCertificateUrl() { return certificateUrl; }
}
