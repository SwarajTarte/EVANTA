package com.evanta.app;

/**
 * A wrapper model combining an Event with its corresponding Registration.
 * Used for binding data in the "My Events" grid recycler view.
 */
public class MyEventItem {
    private final Event event;
    private final Registration registration;

    public MyEventItem(Event event, Registration registration) {
        this.event = event;
        this.registration = registration;
    }

    public Event getEvent() {
        return event;
    }

    public Registration getRegistration() {
        return registration;
    }
}
