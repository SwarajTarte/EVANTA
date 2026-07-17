package com.example.evanta;

import com.google.gson.annotations.SerializedName;

public class Notification {

    private String id;

    @SerializedName("user_uid")
    private String userUid;

    private String title;
    private String body;
    private String type;

    @SerializedName("event_id")
    private String eventId;

    @SerializedName("is_read")
    private boolean isRead;

    @SerializedName("created_at")
    private String createdAt;

    // Types
    public static final String TYPE_UPCOMING = "upcoming";
    public static final String TYPE_CERTIFICATE = "certificate";
    public static final String TYPE_REGISTRATION_CLOSED = "registration_closed";
    public static final String TYPE_GENERAL = "general";

    public String getId() { return id; }
    public String getUserUid() { return userUid; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getType() { return type; }
    public String getEventId() { return eventId; }
    public boolean isRead() { return isRead; }
    public String getCreatedAt() { return createdAt; }
    public void setRead(boolean read) { isRead = read; }
    public void setType(String type) { this.type = type; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setId(String id) { this.id = id; }
}