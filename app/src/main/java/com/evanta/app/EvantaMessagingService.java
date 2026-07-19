package com.evanta.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives Firebase Cloud Messaging pushes.
 *
 * <p>onMessageReceived fires when a data message arrives, or when a
 * notification message arrives while the app is in the foreground. (When a
 * notification-type message arrives while the app is backgrounded, the system
 * tray builds the notification automatically using the manifest meta-data.)
 *
 * <p>We send DATA messages from the server so this method always runs and we
 * control the exact look — title, body, small silhouette icon, large full
 * colour logo, and where a tap lands.
 */
public class EvantaMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        String title = null;
        String body = null;
        String eventId = null;

        // Prefer the data payload (present on data messages we send server-side).
        Map<String, String> data = remoteMessage.getData();
        if (!data.isEmpty()) {
            title = data.get("title");
            body = data.get("body");
            eventId = data.get("event_id");
        }

        // Fall back to the notification block if the server sent a
        // notification-type message and we're in the foreground.
        if (remoteMessage.getNotification() != null) {
            if (title == null) title = remoteMessage.getNotification().getTitle();
            if (body == null) body = remoteMessage.getNotification().getBody();
        }

        if (title == null) title = "Evanta";
        if (body == null) body = "";

        showNotification(title, body, eventId);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // The device's FCM token rotated. Push the fresh one to Supabase so the
        // server can still reach this device. Safe to call when signed out —
        // the helper no-ops if there is no Firebase user.
        DeviceTokenManager.registerToken(getApplicationContext(), token);
    }

    private void showNotification(String title, String body, String eventId) {
        Intent intent = new Intent(this, NotificationCenterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (eventId != null) {
            intent.putExtra("event_id", eventId);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent, flags);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, EvantaApp.CHANNEL_DEFAULT)
                        .setSmallIcon(R.drawable.ic_stat_evanta)
                        .setLargeIcon(BitmapFactory.decodeResource(
                                getResources(), R.drawable.logo))
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setColor(getResources().getColor(R.color.nav_selected))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // Unique id per notification so multiple pushes stack instead of
            // replacing one another.
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
