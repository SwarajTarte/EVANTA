package com.example.evanta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrefetchCache {

    private static final long MY_EVENTS_TTL_MS = 5 * 60 * 1000L; // 5 min

    private static List<MyEventItem> cachedMyEventItems;
    private static List<Registration> cachedRegistrations;
    private static long myEventsCachedAt = 0L;

    private PrefetchCache() {}

    public static synchronized void setMyEventsData(List<Registration> registrations, List<Event> events) {
        Map<String, Registration> registrationMap = new HashMap<>();
        List<Registration> safeRegistrations = new ArrayList<>();

        if (registrations != null) {
            for (Registration registration : registrations) {
                if (registration == null) continue;
                safeRegistrations.add(registration);
                if (registration.getEventId() != null) {
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

        cachedRegistrations = safeRegistrations;
        cachedMyEventItems = items;
        myEventsCachedAt = System.currentTimeMillis();
    }

    public static synchronized List<MyEventItem> getMyEventItemsFresh() {
        if (!hasFreshMyEventsData()) return null;
        return new ArrayList<>(cachedMyEventItems);
    }

    public static synchronized List<Registration> getRegistrationsFresh() {
        if (!hasFreshMyEventsData()) return null;
        return new ArrayList<>(cachedRegistrations);
    }

    public static synchronized boolean hasFreshMyEventsData() {
        return cachedMyEventItems != null
                && (System.currentTimeMillis() - myEventsCachedAt) <= MY_EVENTS_TTL_MS;
    }

    public static synchronized void clearMyEventsData() {
        cachedMyEventItems = null;
        cachedRegistrations = null;
        myEventsCachedAt = 0L;
    }

    public static synchronized void clear() {
        clearMyEventsData();
    }
}