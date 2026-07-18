package com.example.evanta;

import android.app.Application;
import android.content.Context;

/**
 * Holds the application context in a static field so lightweight static
 * cache classes (EventCache, PrefetchCache) can persist to SharedPreferences
 * without every caller having to pass a Context in.
 */
public class EvantaApp extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context getAppContext() {
        return appContext;
    }
}
