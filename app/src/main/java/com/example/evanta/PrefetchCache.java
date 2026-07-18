package com.example.evanta;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches the user's "My Events" data (registrations + their events) in memory
 * and on disk. Disk persistence lets the data survive a cold start, so the next
 * app open can render instantly from cache while the network refresh runs
 * silently in the background (stale-while-revalidate).
 *
 * Two read modes:
 *   - getMyEventItemsFresh()  → only within the TTL (used to decide whether a
 *                               background refresh is still needed).
 *   - getMyEventItemsStale()  → whatever is cached regardless of age (used to
 *                               paint the UI immediately on open).
 */
public class PrefetchCache {

    private static final long MY_EVENTS_TTL_MS = 5 * 60 * 1000L; // 5 min

    private static final String PREFS_NAME = "prefetch_cache";
    private static final String KEY_REGISTRATIONS = "registrations";
    private static final String KEY_EVENTS = "events";
    private static final String KEY_CACHED_AT = "cached_at";

    private static final Gson gson = new Gson();
    private static final Type REG_LIST_TYPE = new TypeToken<List<Registration>>() {}.getType();
    private static final Type EVENT_LIST_TYPE = new TypeToken<List<Event>>() {}.getType();

    private static List<MyEventItem> cachedMyEventItems;
    private static List<Registration> cachedRegistrations;
    private static long myEventsCachedAt = 0L;
    private static boolean loadedFromDisk = false;

    private PrefetchCache() {}

    public static synchronized void setMyEventsData(List<Registration> registrations, List<Event> events) {
        List<Registration> safeRegistrations = new ArrayList<>();
        if (registrations != null) {
            for (Registration registration : registrations) {
                if (registration != null) safeRegistrations.add(registration);
            }
        }
        List<Event> safeEvents = events != null ? events : new ArrayList<>();

        cachedRegistrations = safeRegistrations;
        cachedMyEventItems = buildItems(safeRegistrations, safeEvents);
        myEventsCachedAt = System.currentTimeMillis();
        loadedFromDisk = true;

        persist(safeRegistrations, safeEvents, myEventsCachedAt);
    }

    /** Combines registrations with their events into display items. */
    private static List<MyEventItem> buildItems(List<Registration> registrations, List<Event> events) {
        Map<String, Registration> registrationMap = new HashMap<>();
        if (registrations != null) {
            for (Registration registration : registrations) {
                if (registration != null && registration.getEventId() != null) {
                    registrationMap.put(registration.getEventId(), registration);
                }
            }
        }

        List<MyEventItem> items = new ArrayList<>();
        if (events != null) {
            for (Event event : events) {
                if (event == null || event.getId() == null) continue;
                Registration registration = registrationMap.get(event.getId());
                if (registration != null) {
                    items.add(new MyEventItem(event, registration));
                }
            }
        }
        return items;
    }

    /** Loads the persisted copy into memory the first time it's needed. */
    private static synchronized void ensureLoaded() {
        if (loadedFromDisk) return;
        loadedFromDisk = true;

        SharedPreferences prefs = prefs();
        if (prefs == null) return;

        String regJson = prefs.getString(KEY_REGISTRATIONS, null);
        String eventJson = prefs.getString(KEY_EVENTS, null);
        if (regJson == null || eventJson == null) return;

        try {
            List<Registration> registrations = gson.fromJson(regJson, REG_LIST_TYPE);
            List<Event> events = gson.fromJson(eventJson, EVENT_LIST_TYPE);
            if (registrations == null) registrations = new ArrayList<>();
            if (events == null) events = new ArrayList<>();

            cachedRegistrations = registrations;
            cachedMyEventItems = buildItems(registrations, events);
            myEventsCachedAt = prefs.getLong(KEY_CACHED_AT, 0L);
        } catch (Exception ignored) {}
    }

    public static synchronized List<MyEventItem> getMyEventItemsFresh() {
        ensureLoaded();
        if (!hasFreshMyEventsData()) return null;
        return new ArrayList<>(cachedMyEventItems);
    }

    /** Returns cached items regardless of age (null only if nothing is cached). */
    public static synchronized List<MyEventItem> getMyEventItemsStale() {
        ensureLoaded();
        if (cachedMyEventItems == null) return null;
        return new ArrayList<>(cachedMyEventItems);
    }

    public static synchronized List<Registration> getRegistrationsFresh() {
        ensureLoaded();
        if (!hasFreshMyEventsData()) return null;
        return new ArrayList<>(cachedRegistrations);
    }

    public static synchronized boolean hasFreshMyEventsData() {
        ensureLoaded();
        return cachedMyEventItems != null
                && (System.currentTimeMillis() - myEventsCachedAt) <= MY_EVENTS_TTL_MS;
    }

    public static synchronized void clearMyEventsData() {
        cachedMyEventItems = null;
        cachedRegistrations = null;
        myEventsCachedAt = 0L;
        loadedFromDisk = true; // nothing to load; treat as loaded-empty
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().clear().apply();
    }

    public static synchronized void clear() {
        clearMyEventsData();
    }

    // ---------- Disk helpers ----------

    private static SharedPreferences prefs() {
        Context ctx = EvantaApp.getAppContext();
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void persist(List<Registration> registrations, List<Event> events, long cachedAt) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        try {
            prefs.edit()
                    .putString(KEY_REGISTRATIONS, gson.toJson(registrations))
                    .putString(KEY_EVENTS, gson.toJson(events))
                    .putLong(KEY_CACHED_AT, cachedAt)
                    .apply();
        } catch (Exception ignored) {}
    }
}
