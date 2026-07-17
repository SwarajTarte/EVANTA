package com.example.evanta;

import java.util.List;

public class EventCache {

    private static List<Event> cachedFeaturedEvents;
    private static List<Event> cachedAllEvents;

    private EventCache() {}

    public static List<Event> get() {
        return cachedFeaturedEvents;
    }

    public static void set(List<Event> events) {
        cachedFeaturedEvents = events;
    }

    public static List<Event> getAllEvents() {
        return cachedAllEvents;
    }

    public static void setAllEvents(List<Event> events) {
        cachedAllEvents = events;
    }

    public static void clear() {
        cachedFeaturedEvents = null;
        cachedAllEvents = null;
    }
}