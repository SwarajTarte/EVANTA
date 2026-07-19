package com.example.evanta;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import android.content.Intent;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private TextView greetingText;
    private TextView avatarInitial;
    private TextView statJoined;
    private TextView statUpcoming;
    private TextView statCertificates;
    private ImageView avatarImage;

    private View featuredCard1, featuredCard2, featuredCard3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        greetingText = view.findViewById(R.id.home_greeting);
        avatarInitial = view.findViewById(R.id.home_avatar_initial);
        avatarImage = view.findViewById(R.id.home_avatar_image);
        statJoined = view.findViewById(R.id.stat_joined);
        statUpcoming = view.findViewById(R.id.stat_upcoming);
        statCertificates = view.findViewById(R.id.stat_certificates);

        featuredCard1 = view.findViewById(R.id.featured_card_1);
        featuredCard2 = view.findViewById(R.id.featured_card_2);
        featuredCard3 = view.findViewById(R.id.featured_card_3);

        View viewAllEvents = view.findViewById(R.id.view_all_events);
        view.findViewById(R.id.home_notification_icon).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationCenterActivity.class)));

        viewAllEvents.setOnClickListener(v -> {
            NavController nav = Navigation.findNavController(v);
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(nav.getGraph().getStartDestinationId(), false, true)
                    .build();
            nav.navigate(R.id.browseFragment, null, options);
        });

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            User cached = UserCache.get(requireContext());
            if (cached != null) {
                bindUser(cached);
            }

            loadUser(currentUser.getUid());
            loadStudentStats(currentUser.getUid());
        }

        fetchFeaturedEvents();
    }

    private void loadUser(String uid) {
        UserRepository userRepository = new UserRepository();

        userRepository.getUserByUid(uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    UserCache.set(requireContext(), user);
                    bindUser(user);
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                // keep defaults on failure
            }
        });
    }

    private void bindUser(User user) {
        String fullName = user.getName() != null ? user.getName() : "";
        String nickname = fullName.trim().isEmpty() ? "there" : fullName.trim().split(" ")[0];

        greetingText.setText("Hi, " + nickname + " 👋");

        if (!fullName.trim().isEmpty()) {
            avatarInitial.setText(String.valueOf(fullName.trim().charAt(0)).toUpperCase());
        }

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            avatarImage.setVisibility(View.VISIBLE);
            avatarInitial.setVisibility(View.GONE);
            Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(avatarImage);
        } else {
            avatarImage.setVisibility(View.GONE);
            avatarInitial.setVisibility(View.VISIBLE);
        }
    }

    private void fetchFeaturedEvents() {
        List<Event> cached = EventCache.get();
        if (cached != null) {
            // Cache holds the raw featured list; hide any whose deadline has passed.
            bindFeaturedEvents(EventVisibility.filterOpen(cached));
        }

        User user = UserCache.get(requireContext());
        String collegeId = user != null ? user.getCollegeId() : null;

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        if (collegeId != null) {
            // Fetch college-specific featured events first
            api.getFeaturedEventsByCollege("eq.true", "eq." + collegeId, "date_start.asc", 3)
                    .enqueue(new Callback<List<Event>>() {
                        @Override
                        public void onResponse(Call<List<Event>> call,
                                               Response<List<Event>> response) {
                            if (!isAdded()) return;

                            List<Event> collegeEvents = new ArrayList<>();
                            if (response.isSuccessful() && response.body() != null) {
                                // Only consider events whose registration is still open.
                                collegeEvents.addAll(
                                        EventVisibility.filterOpen(response.body()));
                            }

                            if (collegeEvents.size() >= 3) {
                                // Enough college events — use them as-is
                                EventCache.set(collegeEvents);
                                bindFeaturedEvents(collegeEvents);
                            } else {
                                // Fewer than 3 — top up with platform-wide featured events
                                topUpWithPlatformWideFeaturedEvents(collegeEvents);
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Event>> call, Throwable t) {
                            if (!isAdded()) return;
                            topUpWithPlatformWideFeaturedEvents(new ArrayList<>());
                        }
                    });
        } else {
            topUpWithPlatformWideFeaturedEvents(new ArrayList<>());
        }
    }

    /**
     * Fills the featured section up to 3 events. Starts with the given
     * college-specific events, then appends other featured events from across
     * the platform (skipping any already present) until 3 slots are filled.
     */
    private void topUpWithPlatformWideFeaturedEvents(List<Event> collegeEvents) {
        // Request enough to still have 3 after removing the college events we already hold.
        int limit = 3 + collegeEvents.size();
        new EventRepository().getFeaturedEvents(limit).enqueue(new Callback<List<Event>>() {
            @Override
            public void onResponse(Call<List<Event>> call, Response<List<Event>> response) {
                if (!isAdded()) return;

                List<Event> combined = new ArrayList<>(collegeEvents);

                Set<String> seenIds = new HashSet<>();
                for (Event e : collegeEvents) {
                    if (e.getId() != null) seenIds.add(e.getId());
                }

                if (response.isSuccessful() && response.body() != null) {
                    for (Event e : response.body()) {
                        if (combined.size() >= 3) break;
                        if (!EventVisibility.isRegistrationOpen(e)) continue;
                        if (e.getId() != null && !seenIds.add(e.getId())) continue;
                        combined.add(e);
                    }
                }

                if (!combined.isEmpty()) {
                    EventCache.set(combined);
                    bindFeaturedEvents(combined);
                }
            }

            @Override
            public void onFailure(Call<List<Event>> call, Throwable t) {
                if (!isAdded()) return;
                // Network failure on top-up — still show whatever college events we have.
                if (!collegeEvents.isEmpty()) {
                    EventCache.set(collegeEvents);
                    bindFeaturedEvents(collegeEvents);
                }
            }
        });
    }

    private void bindFeaturedEvents(List<Event> events) {
        View[] cards = { featuredCard1, featuredCard2, featuredCard3 };

        for (int i = 0; i < cards.length; i++) {
            if (i < events.size()) {
                bindFeaturedCard(cards[i], events.get(i));
                cards[i].setVisibility(View.VISIBLE);
            } else {
                cards[i].setVisibility(View.GONE);
            }
        }
    }

    private void bindFeaturedCard(View card, Event event) {
        ImageView coverImage = card.findViewById(R.id.featured_background_image);

        ((TextView) card.findViewById(R.id.featured_title)).setText(event.getTitle());
        ((TextView) card.findViewById(R.id.featured_subtitle)).setText(event.getSubtitle());
        ((TextView) card.findViewById(R.id.featured_date))
                .setText(formatDateRange(event.getDateStart(), event.getDateEnd()));
        ((TextView) card.findViewById(R.id.featured_location)).setText(event.getLocation());

        com.bumptech.glide.load.resource.bitmap.RoundedCorners rounded =
                new com.bumptech.glide.load.resource.bitmap.RoundedCorners(56);

        if (event.getImageUrl() != null && !event.getImageUrl().trim().isEmpty()) {
            Glide.with(this)
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.launcher)
                    .error(R.drawable.launcher)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(), rounded)
                    .into(coverImage);
        } else {
            Glide.with(this)
                    .load(R.drawable.launcher)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(), rounded)
                    .into(coverImage);
        }

        card.findViewById(R.id.featured_view_details).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT, event);
            startActivity(intent);
        });
    }

    private void loadStudentStats(String uid) {
        // Paint instantly from cache (fresh or stale). If the cache is fresh we
        // stop here; if it was only stale we still refresh silently below.
        bindStatsFromPrefetch();
        if (PrefetchCache.hasFreshMyEventsData()) {
            return;
        }

        new RegistrationRepository().getRegistrationsForUser(uid)
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(Call<List<Registration>> call, Response<List<Registration>> response) {
                        if (!isAdded()) return;
                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                            bindStats(0, 0, 0);
                            return;
                        }
                        loadStatsEvents(response.body());
                    }

                    @Override
                    public void onFailure(Call<List<Registration>> call, Throwable t) {
                        if (!isAdded()) return;
                        bindStats(0, 0, 0);
                    }
                });
    }

    private boolean bindStatsFromPrefetch() {
        // Prefer fresh, fall back to stale so the stats paint instantly on a
        // cold start; a background refresh updates them silently afterwards.
        List<MyEventItem> cached = PrefetchCache.getMyEventItemsFresh();
        if (cached == null) {
            cached = PrefetchCache.getMyEventItemsStale();
        }
        if (cached == null) return false;

        int joined = cached.size();
        int upcoming = 0;
        int certificates = 0;

        for (MyEventItem item : cached) {
            if (!isCompleted(item.getEvent())) {
                upcoming++;
            }
            String certUrl = item.getRegistration().getCertificateUrl();
            if (certUrl != null && !certUrl.trim().isEmpty()) {
                certificates++;
            }
        }

        bindStats(joined, upcoming, certificates);
        return true;
    }

    private void loadStatsEvents(List<Registration> registrations) {
        Map<String, Registration> registrationMap = new HashMap<>();
        StringBuilder idsBuilder = new StringBuilder("in.(");
        int added = 0;

        for (Registration registration : registrations) {
            if (registration.getEventId() == null) continue;
            if (added > 0) {
                idsBuilder.append(",");
            }
            idsBuilder.append(registration.getEventId());
            registrationMap.put(registration.getEventId(), registration);
            added++;
        }
        idsBuilder.append(")");

        if (added == 0) {
            bindStats(0, 0, 0);
            return;
        }

        new EventRepository().getEventsByIds(idsBuilder.toString())
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(Call<List<Event>> call, Response<List<Event>> response) {
                        if (!isAdded()) return;
                        int upcoming = 0;
                        int certificates = 0;

                        if (response.isSuccessful() && response.body() != null) {
                            PrefetchCache.setMyEventsData(registrations, response.body());

                            for (Event event : response.body()) {
                                if (!isCompleted(event)) {
                                    upcoming++;
                                }
                                Registration registration = registrationMap.get(event.getId());
                                if (registration != null
                                        && registration.getCertificateUrl() != null
                                        && !registration.getCertificateUrl().trim().isEmpty()) {
                                    certificates++;
                                }
                            }
                        }
                        bindStats(registrations.size(), upcoming, certificates);
                    }

                    @Override
                    public void onFailure(Call<List<Event>> call, Throwable t) {
                        if (!isAdded()) return;
                        bindStats(registrations.size(), 0, 0);
                    }
                });
    }

    private void bindStats(int joined, int upcoming, int certificates) {
        statJoined.setText(String.valueOf(joined));
        statUpcoming.setText(String.valueOf(upcoming));
        statCertificates.setText(String.valueOf(certificates));
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

    private String formatDateRange(String start, String end) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat dayOnly = new SimpleDateFormat("d", Locale.getDefault());
            SimpleDateFormat full = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());

            Date startDate = input.parse(start);

            if (end == null || end.isEmpty() || end.equals(start)) {
                return full.format(startDate);
            }

            Date endDate = input.parse(end);
            return dayOnly.format(startDate) + " - " + full.format(endDate);

        } catch (Exception e) {
            return start;
        }
    }
}