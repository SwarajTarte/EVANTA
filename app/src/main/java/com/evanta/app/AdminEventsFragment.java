package com.evanta.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin "Events" tab: a student-home-style header over a compact, browse-style
 * list of the admin's own events (the same row look as the student Browse tab).
 * Tapping a row opens {@link AdminEventEditActivity}, which hosts the full edit
 * form plus the Approvals / Certificates management flows.
 */
public class AdminEventsFragment extends Fragment {

    private TextView greeting;
    private TextView avatarInitial;
    private ImageView avatarImage;
    private EditText searchBar;

    private RecyclerView recycler;
    private ProgressBar loader;
    private View emptyState;
    private TextView emptyTitle, emptyMessage;

    // Full set from the server; `visible` is what the adapter renders (post-search).
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> visible = new ArrayList<>();
    private AdminEventListAdapter adapter;
    private String query = "";

    // Opening the admin notification center; a tapped row returns an event id
    // whose approvals screen we then open.
    private final ActivityResultLauncher<Intent> notificationLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null) return;
                        String eventId = result.getData()
                                .getStringExtra(AdminNotificationCenterActivity.RESULT_EVENT_ID);
                        if (eventId != null && !eventId.trim().isEmpty()) {
                            openApprovalsForEventId(eventId);
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        greeting = view.findViewById(R.id.admin_greeting);
        avatarInitial = view.findViewById(R.id.admin_avatar_initial);
        avatarImage = view.findViewById(R.id.admin_avatar_image);
        searchBar = view.findViewById(R.id.admin_events_search);
        recycler = view.findViewById(R.id.admin_events_recycler);
        loader = view.findViewById(R.id.admin_events_loader);
        emptyState = view.findViewById(R.id.admin_events_state);
        emptyTitle = view.findViewById(R.id.admin_events_state_title);
        emptyMessage = view.findViewById(R.id.admin_events_state_message);

        view.findViewById(R.id.admin_notification_icon).setOnClickListener(v ->
                notificationLauncher.launch(
                        new Intent(requireContext(), AdminNotificationCenterActivity.class)));

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminEventListAdapter(visible, this::openEditor);
        recycler.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.getDefault());
                applyFilter();
            }
        });

        User user = UserCache.get(requireContext());
        bindHeader(user);

        loadEvents(user != null ? user.getCollegeId() : null);
    }

    @Override
    public void onResume() {
        super.onResume();
        // A row may have been edited/deleted, or a new event added from the Add
        // tab — refresh silently so the list stays current.
        if (adapter != null) {
            User user = UserCache.get(requireContext());
            loadEvents(user != null ? user.getCollegeId() : null);
        }
    }

    private void bindHeader(User user) {
        String fullName = user != null && user.getName() != null ? user.getName() : "";
        String nickname = fullName.trim().isEmpty() ? "there" : fullName.trim().split(" ")[0];
        greeting.setText("Hi, " + nickname + " 👋");

        if (!fullName.trim().isEmpty()) {
            avatarInitial.setText(String.valueOf(fullName.trim().charAt(0)).toUpperCase());
        }

        String photo = user != null ? user.getPhotoUrl() : null;
        if (photo != null && !photo.isEmpty()) {
            avatarImage.setVisibility(View.VISIBLE);
            avatarInitial.setVisibility(View.GONE);
            Glide.with(this).load(photo).circleCrop().into(avatarImage);
        } else {
            avatarImage.setVisibility(View.GONE);
            avatarInitial.setVisibility(View.VISIBLE);
        }
    }

    private void loadEvents(String collegeId) {
        if (collegeId == null || collegeId.isEmpty()) {
            showEmpty("No College Linked",
                    "Your admin account isn't linked to a college yet.");
            return;
        }

        // Only show the spinner on a truly empty first load; otherwise refresh
        // silently to avoid a flash on every resume.
        if (allEvents.isEmpty()) showLoading();

        new EventRepository().getEventsByCollege(collegeId)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (!isAdded()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            allEvents.clear();
                            allEvents.addAll(response.body());
                            applyFilter();

                            if (allEvents.isEmpty()) {
                                showEmpty("No Events Yet",
                                        "Create your first event from the Add tab.");
                            }
                        } else if (allEvents.isEmpty()) {
                            showEmpty("Couldn't Load Events",
                                    "Please check your connection and try again.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        if (allEvents.isEmpty()) {
                            showEmpty("Couldn't Load Events",
                                    "Please check your connection and try again.");
                        }
                    }
                });
    }

    /** Rebuilds the visible list from the current search query and updates state. */
    private void applyFilter() {
        visible.clear();
        if (query.isEmpty()) {
            visible.addAll(allEvents);
        } else {
            for (Event e : allEvents) {
                if (matches(e, query)) visible.add(e);
            }
        }
        adapter.notifyDataSetChanged();

        if (allEvents.isEmpty()) {
            // The load callbacks own the "no events" / error copy.
            return;
        }
        if (visible.isEmpty()) {
            showEmpty("No Matches", "No events match \"" + searchBar.getText() + "\".");
        } else {
            showList();
        }
    }

    private boolean matches(Event e, String q) {
        return contains(e.getTitle(), q)
                || contains(e.getSubtitle(), q)
                || contains(e.getCategory(), q)
                || contains(e.getLocation(), q);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(q);
    }

    private void openEditor(Event event) {
        Intent intent = new Intent(requireContext(), AdminEventEditActivity.class);
        intent.putExtra(AdminEventEditActivity.EXTRA_EVENT, event);
        startActivity(intent);
    }

    /**
     * Opens the editor with the approvals sheet auto-expanded, for an event chosen
     * from the notification center. Uses the already-loaded list when possible;
     * otherwise fetches the single event by id first.
     */
    private void openApprovalsForEventId(String eventId) {
        for (Event e : allEvents) {
            if (eventId.equals(e.getId())) {
                openApprovals(e);
                return;
            }
        }

        new EventRepository().getEventsByIds("in.(" + eventId + ")")
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            openApprovals(response.body().get(0));
                        } else {
                            toast("Couldn't open that event");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call,
                                          @NonNull Throwable t) {
                        if (isAdded()) toast("Couldn't open that event");
                    }
                });
    }

    private void openApprovals(Event event) {
        Intent intent = new Intent(requireContext(), AdminEventEditActivity.class);
        intent.putExtra(AdminEventEditActivity.EXTRA_EVENT, event);
        intent.putExtra(AdminEventEditActivity.EXTRA_OPEN_APPROVALS, true);
        startActivity(intent);
    }

    // ---------- Helpers ----------

    private void toast(String msg) {
        if (isAdded()) android.widget.Toast.makeText(requireContext(), msg,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    // ---------- View state ----------

    private void showLoading() {
        loader.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showList() {
        loader.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty(String title, String message) {
        loader.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(title);
        emptyMessage.setText(message);
    }
}
