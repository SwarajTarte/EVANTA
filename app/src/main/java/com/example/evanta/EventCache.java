package com.example.evanta;

import java.util.List;

public class EventCache {

    private static List<Event> cachedFeaturedEvents;

    private EventCache() {}

    public static List<Event> get() {
        return cachedFeaturedEvents;
    }

    public static void set(List<Event> events) {
        cachedFeaturedEvents = events;
    }

    public static void clear() {
        cachedFeaturedEvents = null;
    }
}