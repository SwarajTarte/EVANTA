package com.evanta.app;

import com.google.gson.annotations.SerializedName;

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

    private String status;

    private int attempts;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    /** Max total submissions allowed: 1 original + 3 reapplies. */
    public static final int MAX_ATTEMPTS = 4;

    public Registration(String userUid, String eventId) {
        this.userUid = userUid;
        this.eventId = eventId;
        this.status = STATUS_PENDING;
    }

    public String getId()             { return id; }
    public String getUserUid()        { return userUid; }
    public String getEventId()        { return eventId; }
    public String getRegisteredAt()   { return registeredAt; }
    public String getCertificateUrl() { return certificateUrl; }
    public String getStatus()         { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts()          { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setCertificateUrl(String certificateUrl) { this.certificateUrl = certificateUrl; }

    public boolean isPending()  { return STATUS_PENDING.equals(status); }
    public boolean isApproved() { return STATUS_APPROVED.equals(status); }
    public boolean isRejected() { return STATUS_REJECTED.equals(status); }

    /** True if the student may still reapply after a rejection. */
    public boolean canReapply() { return isRejected() && attempts < MAX_ATTEMPTS; }
}