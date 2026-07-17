package com.example.evanta;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationCenterActivity extends AppCompatActivity {

    private LinearLayout notificationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_notification_center);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());
        notificationList = findViewById(R.id.notification_list);
        loadNotifications();
    }

    private void loadNotifications() {
        User user = UserCache.get(this);
        if (user == null) {
            addNotification("No profile loaded", "Log in again to refresh student notifications.");
            return;
        }

        new RegistrationRepository().getRegistrationsForUser(user.getUid())
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Registration>> call,
                                           @NonNull Response<List<Registration>> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                            addNotification("No notifications yet", "Your event updates will appear here.");
                            return;
                        }
                        loadRegisteredEvents(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Registration>> call, @NonNull Throwable t) {
                        addNotification("Could not load notifications", "Check your connection and try again.");
                    }
                });
    }

    private void loadRegisteredEvents(List<Registration> registrations) {
        Map<String, Registration> registrationMap = new HashMap<>();
        StringBuilder ids = new StringBuilder("in.(");
        int added = 0;
        for (Registration registration : registrations) {
            if (registration.getEventId() == null) continue;
            if (added > 0) ids.append(",");
            ids.append(registration.getEventId());
            registrationMap.put(registration.getEventId(), registration);
            added++;
        }
        ids.append(")");

        if (added == 0) {
            addNotification("No notifications yet", "Your event updates will appear here.");
            return;
        }

        new EventRepository().getEventsByIds(ids.toString())
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                            addNotification("No notifications yet", "Your event updates will appear here.");
                            return;
                        }

                        notificationList.removeAllViews();
                        for (Event event : response.body()) {
                            Registration registration = registrationMap.get(event.getId());
                            if (registration != null && registration.getCertificateUrl() != null
                                    && !registration.getCertificateUrl().trim().isEmpty()) {
                                addNotification("Certificate available", event.getTitle() + " certificate is ready to download.");
                            }
                            if (!isCompleted(event)) {
                                addNotification("Upcoming event", event.getTitle() + " is scheduled for " + formatDate(event.getDateStart()) + ".");
                            }
                            if (isRegistrationClosed(event)) {
                                addNotification("Registration closed", event.getTitle() + " is no longer accepting registrations.");
                            }
                        }
                        if (notificationList.getChildCount() == 0) {
                            addNotification("No notifications yet", "Important event updates will appear here.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                        addNotification("Could not load notifications", "Check your connection and try again.");
                    }
                });
    }

    private void addNotification(String title, String body) {
        TextView item = new TextView(this);
        item.setText(title + "\n" + body);
        item.setTextColor(Color.WHITE);
        item.setTextSize(14);
        item.setLineSpacing(6, 1f);
        item.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        item.setBackgroundResource(R.drawable.bg_notification_item);
        item.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        notificationList.addView(item, params);
    }

    private boolean isCompleted(Event event) {
        String dateValue = event.getDateEnd() != null && !event.getDateEnd().isEmpty()
                ? event.getDateEnd()
                : event.getDateStart();
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date eventDate = input.parse(dateValue);
            Date today = input.parse(input.format(new Date()));
            return eventDate != null && eventDate.before(today);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRegistrationClosed(Event event) {
        if (event.getRegistrationDeadline() == null || event.getRegistrationDeadline().trim().isEmpty()) {
            return false;
        }
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date deadline = input.parse(event.getRegistrationDeadline());
            Date today = input.parse(input.format(new Date()));
            return deadline != null && deadline.before(today);
        } catch (Exception e) {
            return false;
        }
    }

    private String formatDate(String date) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());
            return output.format(input.parse(date));
        } catch (Exception e) {
            return date != null ? date : "";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
