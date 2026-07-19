package com.evanta.app;

import android.graphics.Color;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;

/**
 * The app's UI is always dark (deep navy/purple gradients), regardless of the
 * device's system light/dark mode setting. EdgeToEdge.enable(activity) with no
 * arguments picks status/nav bar icon color based on the *system* theme, which
 * means on a device in light mode it renders dark icons on our dark background
 * — effectively invisible.
 *
 * Call this instead of EdgeToEdge.enable(activity) to always force light
 * (white) status bar and navigation bar icons, matching the app's fixed dark
 * theme.
 */
final class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {}

    static void enableAlwaysDark(ComponentActivity activity) {
        EdgeToEdge.enable(
                activity,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
    }
}
