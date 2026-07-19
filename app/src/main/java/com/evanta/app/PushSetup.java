package com.evanta.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * One-call setup for push notifications from an Activity's onCreate:
 * asks for the POST_NOTIFICATIONS runtime permission on Android 13+ and
 * registers this device's FCM token with Supabase.
 */
public final class PushSetup {

    public static final int REQ_POST_NOTIFICATIONS = 4711;

    private PushSetup() {}

    public static void ensure(Activity activity) {
        // Register the token regardless — permission only governs whether the
        // system *shows* the notification, not whether we can receive data.
        DeviceTokenManager.syncCurrentToken(activity.getApplicationContext());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS);
            }
        }
    }
}
