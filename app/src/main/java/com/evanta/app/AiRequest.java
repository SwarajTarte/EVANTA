package com.evanta.app;

import com.google.gson.annotations.SerializedName;

public class AiRequest {
    @SerializedName("user_role")
    private String userRole;

    @SerializedName("query")
    private String query;

    public AiRequest(String userRole, String query) {
        this.userRole = userRole;
        this.query = query;
    }

    public String getUserRole() { return userRole; }
    public String getQuery() { return query; }
}
