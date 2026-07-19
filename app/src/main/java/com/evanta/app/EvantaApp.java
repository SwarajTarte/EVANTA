package com.evanta.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Holds the application context in a static field so lightweight static
 * cache classes (EventCache, PrefetchCache) can persist to SharedPreferences
 * without every caller having to pass a Context in.
 */
public class EvantaApp extends Application {

    private static Context appContext;

    /** Default channel used for all Evanta push notifications. */
    public static final String CHANNEL_DEFAULT = "evanta_default";

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        createNotificationChannels();
    }

    public static Context getAppContext() {
        return appContext;
    }

    private void createNotificationChannels() {
        // Channels are only required on Android 8.0 (API 26) and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel defaultChannel = new NotificationChannel(
                    CHANNEL_DEFAULT,
                    "General",
                    NotificationManager.IMPORTANCE_HIGH);
            defaultChannel.setDescription(
                    "Event updates, approvals and reminders");
            defaultChannel.enableLights(true);
            defaultChannel.enableVibration(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(defaultChannel);
            }
        }
    }
}
