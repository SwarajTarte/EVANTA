package com.evanta.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Central rule for whether an event should appear in the student-facing
 * discovery surfaces (Home featured + Browse).
 *
 * An event is hidden once its registration deadline has passed — the deadline
 * day itself still counts as open, mirroring {@code EventDetailActivity
 * .isRegistrationClosed}. Events without a deadline are always visible.
 *
 * This is display-only filtering: My Events (for already-enrolled students) and
 * the Event Detail screen deliberately do NOT use this, so enrolled students can
 * still open a past-deadline event they joined.
 */
public final class EventVisibility {

    private EventVisibility() {}

    /** True if the event may still be shown in Home/Browse (deadline not yet past). */
    public static boolean isRegistrationOpen(Event event) {
        if (event == null) return false;
        String deadline = event.getRegistrationDeadline();
        // No deadline set → treat as always open (never auto-hidden).
        if (deadline == null || deadline.trim().isEmpty()) return true;

        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date deadlineDate = input.parse(deadline.trim());
            Date today = input.parse(input.format(new Date()));
            // Open through the end of the deadline day; hidden only once it's in the past.
            return deadlineDate == null || !deadlineDate.before(today);
        } catch (Exception e) {
            // If the date can't be parsed, fail open so a bad value never hides an event.
            return true;
        }
    }

    /** Returns a new list containing only events whose registration is still open. */
    public static List<Event> filterOpen(List<Event> events) {
        List<Event> out = new ArrayList<>();
        if (events == null) return out;
        for (Event e : events) {
            if (isRegistrationOpen(e)) out.add(e);
        }
        return out;
    }
}
