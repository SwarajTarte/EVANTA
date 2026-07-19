package com.evanta.app;

import android.content.Intent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin-facing notification center. Rather than the student's mixed feed, this
 * surfaces exactly one actionable signal: how many enrollment requests are still
 * pending for each of the admin's events. Tapping a row returns the event id to
 * {@link AdminEventsFragment}, which opens that event's approve/reject sheet.
 */
public class AdminNotificationCenterActivity extends AppCompatActivity {

    /** Result extra: the event id whose approvals sheet should open. */
    public static final String RESULT_EVENT_ID = "result_event_id";

    private RecyclerView recyclerView;
    private ProgressBar loader;
    private View emptyState;
    private NotificationAdapter adapter;
    private final List<Notification> notifications = new ArrayList<>();

    private SupabaseApi api;

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
        String collegeId = user != null ? user.getCollegeId() : null;
        if (collegeId == null || collegeId.trim().isEmpty()) {
            showEmpty();
            return;
        }

        showLoading();
        loadAdminEvents(collegeId);
    }

    // ---------- Step 1: the admin's events ----------

    private void loadAdminEvents(String collegeId) {
        new EventRepository().getEventsByCollege(collegeId)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            List<Event> mine = keepOwnAndLegacy(response.body());
                            if (mine.isEmpty()) {
                                showEmpty();
                            } else {
                                loadPendingRequests(mine);
                            }
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call,
                                          @NonNull Throwable t) {
                        if (!isFinishing()) showEmpty();
                    }
                });
    }

    /**
     * Keeps only events this admin created plus legacy (null-owner) events, so
     * the approvals feed never surfaces another admin's registrations.
     */
    private List<Event> keepOwnAndLegacy(List<Event> events) {
        User me = UserCache.get(this);
        String myUid = me != null ? me.getUid() : null;
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            String owner = e.getCreatedBy();
            if (owner == null || owner.isEmpty()
                    || (myUid != null && myUid.equals(owner))) {
                out.add(e);
            }
        }
        return out;
    }

    // ---------- Step 2: pending registrations across those events ----------

    private void loadPendingRequests(List<Event> events) {
        // Keep a title lookup, and preserve the college fetch order (newest first).
        Map<String, Event> eventById = new LinkedHashMap<>();
        StringBuilder ids = new StringBuilder("in.(");
        int added = 0;
        for (Event e : events) {
            if (e.getId() == null) continue;
            if (added > 0) ids.append(",");
            ids.append(e.getId());
            eventById.put(e.getId(), e);
            added++;
        }
        ids.append(")");

        if (added == 0) {
            showEmpty();
            return;
        }

        api.getPendingRegistrationsForEvents(ids.toString(),
                "eq." + Registration.STATUS_PENDING)
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Registration>> call,
                                           @NonNull Response<List<Registration>> response) {
                        if (isFinishing()) return;
                        List<Registration> pending =
                                response.isSuccessful() && response.body() != null
                                        ? response.body() : new ArrayList<>();
                        buildRows(eventById, pending);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Registration>> call,
                                          @NonNull Throwable t) {
                        if (!isFinishing()) showEmpty();
                    }
                });
    }

    // ---------- Step 3: one row per event with pending requests ----------

    private void buildRows(Map<String, Event> eventById, List<Registration> pending) {
        // Count pending per event.
        Map<String, Integer> counts = new HashMap<>();
        for (Registration r : pending) {
            String eid = r.getEventId();
            if (eid == null) continue;
            Integer c = counts.get(eid);
            counts.put(eid, c == null ? 1 : c + 1);
        }

        notifications.clear();
        // Iterate events in their original (newest-first) order so rows are stable.
        for (Map.Entry<String, Event> entry : eventById.entrySet()) {
            Integer count = counts.get(entry.getKey());
            if (count == null || count == 0) continue;

            Event event = entry.getValue();
            String plural = count == 1 ? "request" : "requests";
            Notification n = new Notification();
            n.setType(Notification.TYPE_APPROVAL);
            n.setTitle(count + " pending " + plural);
            n.setBody(event.getTitle() + " has " + count + " student "
                    + (count == 1 ? "request" : "requests") + " awaiting your approval.");
            n.setEventId(event.getId());
            n.setRead(false);
            n.setId("approval_" + event.getId());
            // Sort key: surface the most recently-starting events first.
            n.setCreatedAt(event.getDateStart());
            notifications.add(n);
        }

        if (notifications.isEmpty()) {
            showEmpty();
        } else {
            loader.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    // ---------- Tap → return event id to the fragment ----------

    private void onNotificationTapped(Notification notif, int position) {
        if (notif.getEventId() == null || notif.getEventId().trim().isEmpty()) return;
        Intent data = new Intent();
        data.putExtra(RESULT_EVENT_ID, notif.getEventId());
        setResult(RESULT_OK, data);
        finish();
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
}
