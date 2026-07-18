package com.example.evanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin "Events" tab: a student-home-style header (logo + notification + greeting)
 * over a browse-style list of the admin's own events, each rendered as an
 * always-editable card (see {@link AdminEventCardAdapter}).
 */
public class AdminEventsFragment extends Fragment {

    private TextView greeting;
    private TextView avatarInitial;
    private ImageView avatarImage;

    private RecyclerView recycler;
    private ProgressBar loader;
    private View emptyState;
    private TextView emptyTitle, emptyMessage;

    private final List<Event> events = new ArrayList<>();
    private AdminEventCardAdapter adapter;

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
        recycler = view.findViewById(R.id.admin_events_recycler);
        loader = view.findViewById(R.id.admin_events_loader);
        emptyState = view.findViewById(R.id.admin_events_state);
        emptyTitle = view.findViewById(R.id.admin_events_state_title);
        emptyMessage = view.findViewById(R.id.admin_events_state_message);

        view.findViewById(R.id.admin_notification_icon).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationCenterActivity.class)));

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminEventCardAdapter(events, this::onEventDeleted);
        recycler.setAdapter(adapter);

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
        if (events.isEmpty()) showLoading();

        new EventRepository().getEventsByCollege(collegeId)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (!isAdded()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            events.clear();
                            events.addAll(response.body());
                            adapter.notifyDataSetChanged();

                            if (events.isEmpty()) {
                                showEmpty("No Events Yet",
                                        "Create your first event from the Add tab.");
                            } else {
                                showList();
                            }
                        } else if (events.isEmpty()) {
                            showEmpty("Couldn't Load Events",
                                    "Please check your connection and try again.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        if (events.isEmpty()) {
                            showEmpty("Couldn't Load Events",
                                    "Please check your connection and try again.");
                        }
                    }
                });
    }

    private void onEventDeleted(int position) {
        if (position < 0 || position >= events.size()) return;
        events.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, events.size());
        if (events.isEmpty()) {
            showEmpty("No Events Yet", "Create your first event from the Add tab.");
        }
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
