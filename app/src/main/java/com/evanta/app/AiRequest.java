package com.evanta.app;

public class AiRequest {
    private String user_role;
    private String query;

    public AiRequest(String user_role, String query) {
        this.user_role = user_role;
        this.query = query;
    }

    public String getUser_role() { return user_role; }
    public String getQuery() { return query; }
}
