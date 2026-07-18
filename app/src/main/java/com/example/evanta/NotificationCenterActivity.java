package com.example.evanta;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationCenterActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar loader;
    private View emptyState;
    private NotificationAdapter adapter;
    private final List<Notification> notifications = new ArrayList<>();

    private SupabaseApi api;
    private String currentUid;

    private int sourcesLoaded = 0;
    private final List<Notification> derivedNotifications = new ArrayList<>();
    private final List<Notification> pushedNotifications = new ArrayList<>();

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

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.notification_recycler);
        loader = findViewById(R.id.notification_loader);
        emptyState = findViewById(R.id.notification_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notifications, this::onNotificationTapped);
        recyclerView.setAdapter(adapter);

        api = RetrofitClient.getClient().create(SupabaseApi.class);

        User user = UserCache.get(this);
        if (user == null) {
            showEmpty();
            return;
        }

        currentUid = user.getUid();
        showLoading();
        loadDerivedNotifications();
        loadPushedNotifications();
        loadCollegeNewEvents();
    }

    // ---------- Source 1: derived from registered events ----------

    private void loadDerivedNotifications() {
        new RegistrationRepository().getRegistrationsForUser(currentUid)
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Registration>> call,
                                           @NonNull Response<List<Registration>> response) {
                        if (isFinishing()) return;
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().isEmpty()) {
                            onSourceFinished();
                            return;
                        }
                        loadEventsForDerived(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Registration>> call,
                                          @NonNull Throwable t) {
                        if (!isFinishing()) onSourceFinished();
                    }
                });
    }

    private void loadEventsForDerived(List<Registration> registrations) {
        Map<String, Registration> regMap = new HashMap<>();
        StringBuilder ids = new StringBuilder("in.(");
        int added = 0;

        for (Registration reg : registrations) {
            if (reg.getEventId() == null) continue;
            if (added > 0) ids.append(",");
            ids.append(reg.getEventId());
            regMap.put(reg.getEventId(), reg);
            added++;
        }
        ids.append(")");

        if (added == 0) {
            onSourceFinished();
            return;
        }

        new EventRepository().getEventsByIds(ids.toString())
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            for (Event event : response.body()) {
                                Registration reg = regMap.get(event.getId());
                                buildDerivedNotifications(event, reg);
                            }
                        }
                        onSourceFinished();
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call,
                                          @NonNull Throwable t) {
                        if (!isFinishing()) onSourceFinished();
                    }
                });
    }

    private void buildDerivedNotifications(Event event, Registration reg) {
        Set<String> readKeys = getReadKeys();
        Set<String> dismissedKeys = getDismissedKeys();

        if (reg != null && reg.getCertificateUrl() != null
                && !reg.getCertificateUrl().trim().isEmpty()) {
            String key = "certificate_" + event.getId();
            // Permanently dismissed once the certificate has been downloaded.
            if (!dismissedKeys.contains(key)) {
                Notification n = buildNotif(
                        Notification.TYPE_CERTIFICATE,
                        "Certificate Available",
                        "Your certificate for " + event.getTitle() + " is ready to download.",
                        event.getId());
                // Re-appears as unread each visit until it is downloaded.
                n.setRead(false);
                n.setId(key);
                n.setCreatedAt(event.getDateEnd() != null && !event.getDateEnd().isEmpty()
                        ? event.getDateEnd() : event.getDateStart());
                derivedNotifications.add(n);
            }
        }

        if (!isCompleted(event)) {
            String key = "upcoming_" + event.getId();
            Notification n = buildNotif(
                    Notification.TYPE_UPCOMING,
                    "Upcoming Event",
                    event.getTitle() + " is scheduled for " + event.getDateStart() + ".",
                    event.getId());
            // Upcoming & certificate re-appear as unread on every visit.
            n.setRead(false);
            n.setId(key);
            n.setCreatedAt(event.getDateStart());
            derivedNotifications.add(n);
        }

        if (isRegistrationClosed(event)) {
            String key = "closed_" + event.getId();
            Notification n = buildNotif(
                    Notification.TYPE_REGISTRATION_CLOSED,
                    "Registration Closed",
                    event.getTitle() + " is no longer accepting registrations.",
                    event.getId());
            n.setRead(readKeys.contains(key));
            n.setId(key);
            n.setCreatedAt(event.getRegistrationDeadline());
            derivedNotifications.add(n);
        }
    }

    // ---------- Source 3: new events at the student's college ----------

    private void loadCollegeNewEvents() {
        User user = UserCache.get(this);
        final String collegeId = user != null ? user.getCollegeId() : null;
        if (collegeId == null || collegeId.trim().isEmpty()) {
            onSourceFinished();
            return;
        }

        // First find which events the user is already enrolled in, so we can
        // exclude them from "new event" notifications.
        new RegistrationRepository().getRegistrationsForUser(currentUid)
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Registration>> call,
                                           @NonNull Response<List<Registration>> response) {
                        if (isFinishing()) return;
                        Set<String> enrolledIds = new HashSet<>();
                        if (response.isSuccessful() && response.body() != null) {
                            for (Registration reg : response.body()) {
                                if (reg.getEventId() != null) enrolledIds.add(reg.getEventId());
                            }
                        }
                        fetchCollegeEvents(collegeId, enrolledIds);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Registration>> call,
                                          @NonNull Throwable t) {
                        if (isFinishing()) return;
                        // Couldn't load registrations — proceed without exclusions.
                        fetchCollegeEvents(collegeId, new HashSet<>());
                    }
                });
    }

    private void fetchCollegeEvents(String collegeId, Set<String> enrolledIds) {
        new EventRepository().getEventsByCollege(collegeId)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            buildNewEventNotifications(response.body(), enrolledIds);
                        }
                        onSourceFinished();
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call,
                                          @NonNull Throwable t) {
                        if (!isFinishing()) onSourceFinished();
                    }
                });
    }

    private void buildNewEventNotifications(List<Event> events, Set<String> enrolledIds) {
        Set<String> dismissedKeys = getDismissedKeys();

        for (Event event : events) {
            if (event.getId() == null) continue;
            // #2 — skip events the user is already enrolled in.
            if (enrolledIds.contains(event.getId())) continue;
            // Skip events that have already finished.
            if (isCompleted(event)) continue;
            // Permanently dismissed once tapped — never show again.
            String key = "newevent_" + event.getId();
            if (dismissedKeys.contains(key)) continue;

            Notification n = buildNotif(
                    Notification.TYPE_NEW_EVENT,
                    "New Event at Your College",
                    event.getTitle() + " is now open for registration.",
                    event.getId());
            n.setRead(false);
            n.setId(key);
            n.setCreatedAt(event.getDateStart());
            derivedNotifications.add(n);
        }
    }

    // ---------- Source 2: admin-pushed ----------

    private void loadPushedNotifications() {
        api.getNotifications("eq." + currentUid).enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(@NonNull Call<List<Notification>> call,
                                   @NonNull Response<List<Notification>> response) {
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null) {
                    pushedNotifications.addAll(response.body());
                }
                loadBroadcastNotifications();
            }

            @Override
            public void onFailure(@NonNull Call<List<Notification>> call,
                                  @NonNull Throwable t) {
                if (!isFinishing()) loadBroadcastNotifications();
            }
        });
    }

    private void loadBroadcastNotifications() {
        api.getBroadcastNotifications("is.null").enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(@NonNull Call<List<Notification>> call,
                                   @NonNull Response<List<Notification>> response) {
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null) {
                    pushedNotifications.addAll(response.body());
                }
                onSourceFinished();
            }

            @Override
            public void onFailure(@NonNull Call<List<Notification>> call,
                                  @NonNull Throwable t) {
                if (!isFinishing()) onSourceFinished();
            }
        });
    }

    // ---------- Merge ----------

    private synchronized void onSourceFinished() {
        sourcesLoaded++;
        if (sourcesLoaded < 3) return;

        runOnUiThread(() -> {
            Set<String> readKeys = getReadKeys();

            // Apply locally-tracked read state to broadcast rows (shared DB row,
            // so read state must be per-user and cannot live in the row itself).
            for (Notification n : pushedNotifications) {
                if (n.getUserUid() == null && n.getId() != null
                        && readKeys.contains("broadcast_" + n.getId())) {
                    n.setRead(true);
                }
            }

            notifications.clear();
            notifications.addAll(pushedNotifications);
            notifications.addAll(derivedNotifications);

            // Newest first. createdAt is ISO-ish text, so a reverse string
            // compare orders correctly; nulls sink to the bottom.
            java.util.Collections.sort(notifications, (a, b) -> {
                String ca = a.getCreatedAt(), cb = b.getCreatedAt();
                if (ca == null && cb == null) return 0;
                if (ca == null) return 1;
                if (cb == null) return -1;
                return cb.compareTo(ca);
            });

            if (notifications.isEmpty()) {
                showEmpty();
            } else {
                loader.setVisibility(View.GONE);
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    // ---------- Mark as read ----------

    private void onNotificationTapped(Notification notif, int position) {
        // Open the linked event if there is one.
        if (notif.getEventId() != null && !notif.getEventId().trim().isEmpty()) {
            openEvent(notif.getEventId());
        }

        if (notif.isRead()) return;

        notif.setRead(true);
        adapter.notifyItemChanged(position);

        if (notif.getId() == null) return;

        // New-event notifications are permanently dismissed once tapped —
        // stored locally so they are never rebuilt on future visits.
        if (notif.getId().startsWith("newevent_")) {
            markDismissed(notif.getId());
            return;
        }

        // Derived notifications: read state tracked locally.
        if (notif.getId().startsWith("upcoming_")
                || notif.getId().startsWith("certificate_")
                || notif.getId().startsWith("closed_")) {
            markKeyRead(notif.getId());
            return;
        }

        // Broadcast notifications share one DB row across all users, so track
        // their read state locally instead of patching the shared row.
        if (notif.getUserUid() == null) {
            markKeyRead("broadcast_" + notif.getId());
            return;
        }

        // User-specific pushed notification: safe to persist on its own row.
        Map<String, Object> patch = new HashMap<>();
        patch.put("is_read", true);

        api.markNotificationRead("eq." + notif.getId(), patch)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call,
                                           @NonNull Response<Void> response) {}

                    @Override
                    public void onFailure(@NonNull Call<Void> call,
                                          @NonNull Throwable t) {}
                });
    }

    private void openEvent(String eventId) {
        new EventRepository().getEventsByIds("in.(" + eventId + ")")
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            android.content.Intent intent = new android.content.Intent(
                                    NotificationCenterActivity.this, EventDetailActivity.class);
                            intent.putExtra(EventDetailActivity.EXTRA_EVENT,
                                    response.body().get(0));
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call,
                                          @NonNull Throwable t) {}
                });
    }

    // ---------- SharedPreferences for derived read state ----------

    private Set<String> getReadKeys() {
        return getSharedPreferences("notif_read", MODE_PRIVATE)
                .getStringSet("read_keys", new HashSet<>());
    }

    private void markKeyRead(String key) {
        Set<String> keys = new HashSet<>(getReadKeys());
        keys.add(key);
        getSharedPreferences("notif_read", MODE_PRIVATE)
                .edit().putStringSet("read_keys", keys).apply();
    }

    // ---------- SharedPreferences for permanently-dismissed new events ----------

    private Set<String> getDismissedKeys() {
        return getSharedPreferences("notif_read", MODE_PRIVATE)
                .getStringSet("dismissed_keys", new HashSet<>());
    }

    private void markDismissed(String key) {
        Set<String> keys = new HashSet<>(getDismissedKeys());
        keys.add(key);
        getSharedPreferences("notif_read", MODE_PRIVATE)
                .edit().putStringSet("dismissed_keys", keys).apply();
    }

    // ---------- UI helpers ----------

    private void showLoading() {
        loader.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loader.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    // ---------- Notification builder ----------

    private Notification buildNotif(String type, String title, String body, String eventId) {
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setEventId(eventId);
        n.setRead(false);
        return n;
    }

    // ---------- Date helpers ----------

    private boolean isCompleted(Event event) {
        String dateValue = event.getDateEnd() != null && !event.getDateEnd().isEmpty()
                ? event.getDateEnd() : event.getDateStart();
        try {
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date eventDate = fmt.parse(dateValue);
            java.util.Date today = fmt.parse(fmt.format(new java.util.Date()));
            return eventDate != null && eventDate.before(today);
        } catch (Exception e) { return false; }
    }

    private boolean isRegistrationClosed(Event event) {
        if (event.getRegistrationDeadline() == null
                || event.getRegistrationDeadline().trim().isEmpty()) return false;
        try {
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date deadline = fmt.parse(event.getRegistrationDeadline());
            java.util.Date today = fmt.parse(fmt.format(new java.util.Date()));
            return deadline != null && deadline.before(today);
        } catch (Exception e) { return false; }
    }
}