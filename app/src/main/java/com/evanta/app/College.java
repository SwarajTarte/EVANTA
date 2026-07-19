package com.evanta.app;

import com.google.gson.annotations.SerializedName;

public class College {

    private String id;
    private String name;
    private String location;

    @SerializedName("created_at")
    private String createdAt;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getCreatedAt() { return createdAt; }
}