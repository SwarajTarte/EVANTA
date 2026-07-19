package com.evanta.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Caches event lists both in memory (fast) and on disk (survives a cold start).
 *
 * The public API is unchanged from the original in-memory version, so existing
 * callers keep working. On a cold start the in-memory fields are null, so the
 * getters transparently fall back to the persisted copy — giving instant,
 * lag-free data on the next app open while the network refresh happens silently.
 */
public class EventCache {

    private static final String PREFS_NAME = "event_cache";
    private static final String KEY_FEATURED = "featured_events";
    private static final String KEY_ALL = "all_events";

    private static final Gson gson = new Gson();
    private static final Type EVENT_LIST_TYPE = new TypeToken<List<Event>>() {}.getType();

    private static List<Event> cachedFeaturedEvents;
    private static List<Event> cachedAllEvents;

    private EventCache() {}

    // ---------- Featured events ----------

    public static List<Event> get() {
        if (cachedFeaturedEvents == null) {
            cachedFeaturedEvents = readFromDisk(KEY_FEATURED);
        }
        return cachedFeaturedEvents;
    }

    public static void set(List<Event> events) {
        cachedFeaturedEvents = events;
        writeToDisk(KEY_FEATURED, events);
    }

    // ---------- All (browse) events ----------

    public static List<Event> getAllEvents() {
        if (cachedAllEvents == null) {
            cachedAllEvents = readFromDisk(KEY_ALL);
        }
        return cachedAllEvents;
    }

    public static void setAllEvents(List<Event> events) {
        cachedAllEvents = events;
        writeToDisk(KEY_ALL, events);
    }

    public static void clear() {
        cachedFeaturedEvents = null;
        cachedAllEvents = null;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().clear().apply();
    }

    // ---------- Disk helpers ----------

    private static SharedPreferences prefs() {
        Context ctx = EvantaApp.getAppContext();
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void writeToDisk(String key, List<Event> events) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        try {
            prefs.edit().putString(key, gson.toJson(events)).apply();
        } catch (Exception ignored) {}
    }

    private static List<Event> readFromDisk(String key) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return null;
        String json = prefs.getString(key, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, EVENT_LIST_TYPE);
        } catch (Exception e) {
            return null;
        }
    }
}
