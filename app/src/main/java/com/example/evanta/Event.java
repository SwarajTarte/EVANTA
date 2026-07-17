package com.example.evanta;

import com.google.gson.annotations.SerializedName;

public class Event {

    private String id;
    private String title;
    private String subtitle;
    private String description;
    private String category;

    @SerializedName("date_start")
    private String dateStart;

    @SerializedName("date_end")
    private String dateEnd;

    @SerializedName("time_start")
    private String timeStart;

    private String location;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("is_featured")
    private boolean isFeatured;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getDateStart() { return dateStart; }
    public String getDateEnd() { return dateEnd; }
    public String getTimeStart() { return timeStart; }
    public String getLocation() { return location; }
    public String getImageUrl() { return imageUrl; }
    public boolean isFeatured() { return isFeatured; }
}