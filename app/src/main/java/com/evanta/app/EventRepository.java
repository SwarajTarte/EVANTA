package com.evanta.app;

import java.util.List;

import retrofit2.Call;

public class EventRepository {

    private final SupabaseApi api;

    public EventRepository() {
        api = RetrofitClient.getClient().create(SupabaseApi.class);
    }

    public Call<List<Event>> getFeaturedEvents(int limit) {
        return api.getFeaturedEvents("eq.true", "date_start.asc", limit);
    }

    public Call<List<Event>> getEvents(String selectedCategory, String searchQuery) {
        String categoryFilter = selectedCategory != null ? "eq." + selectedCategory : null;
        String titleFilter = searchQuery == null || searchQuery.isEmpty()
                ? null
                : "ilike.*" + searchQuery + "*";

        return api.getEvents(categoryFilter, titleFilter, "category.asc,date_start.asc");
    }

    public Call<List<Event>> getAllEvents() {
        return api.getEvents(null, null, "category.asc,date_start.asc");
    }

    public Call<List<Event>> getEventsByIds(String idsFilter) {
        return api.getEventsByIds(idsFilter, "category.asc,date_start.asc");
    }

    public Call<List<Event>> getEventsByCollege(String collegeId) {
        return api.getEventsByCollege("eq." + collegeId, "date_start.desc");
    }
}
