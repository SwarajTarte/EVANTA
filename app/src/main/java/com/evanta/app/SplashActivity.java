package com.evanta.app;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final int SAFETY_TIMEOUT_MS = 4000;
    private static final int MIN_SPLASH_MS = 1200;

    private boolean userLoaded = false;
    private boolean eventsLoaded = false;
    private boolean allEventsLoaded = false;
    private boolean myEventsLoaded = false;
    private boolean navigated = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        startTime = System.currentTimeMillis();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            handler.postDelayed(this::goToWelcome, MIN_SPLASH_MS);
            return;
        }

        handler.postDelayed(this::navigateToDashboardIfReady, SAFETY_TIMEOUT_MS);

        String uid = currentUser.getUid();

        // Warm start: if we have data cached from a previous run, don't block on
        // the network — show the dashboard after the minimum splash and let the
        // prefetch below refresh the caches silently in the background.
        if (hasWarmCache()) {
            handler.postDelayed(this::navigateToDashboardIfReady, MIN_SPLASH_MS);
        }

        prefetchUser(uid);
        prefetchFeaturedEvents();
        prefetchBrowseEvents();
        prefetchMyEventsData(uid);
    }

    /**
     * True when enough data survives from a previous run to render the dashboard
     * immediately. UserCache is disk-backed; EventCache and PrefetchCache now
     * fall back to their persisted copies on a cold start.
     */
    private boolean hasWarmCache() {
        boolean hasUser = UserCache.get(this) != null;
        boolean hasEvents = EventCache.get() != null || EventCache.getAllEvents() != null;
        return hasUser && hasEvents;
    }

    private void prefetchUser(String uid) {
        UserRepository userRepository = new UserRepository();

        userRepository.getUserByUid(uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    UserCache.set(SplashActivity.this, user);

                    // If admin, skip prefetching student-specific data
                    if ("admin".equals(user.getRole())) {
                        userLoaded = true;
                        eventsLoaded = true;
                        allEventsLoaded = true;
                        myEventsLoaded = true;
                        tryFinishEarly();
                        return;
                    }
                }
                userLoaded = true;
                tryFinishEarly();
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                userLoaded = true;
                tryFinishEarly();
            }
        });
    }

    private void prefetchFeaturedEvents() {
        EventRepository eventRepository = new EventRepository();

        eventRepository.getFeaturedEvents(3).enqueue(new Callback<List<Event>>() {
            @Override
            public void onResponse(Call<List<Event>> call, Response<List<Event>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EventCache.set(response.body());
                }
                eventsLoaded = true;
                tryFinishEarly();
            }

            @Override
            public void onFailure(Call<List<Event>> call, Throwable t) {
                eventsLoaded = true;
                tryFinishEarly();
            }
        });
    }

    private void prefetchBrowseEvents() {
        EventRepository eventRepository = new EventRepository();

        eventRepository.getAllEvents().enqueue(new Callback<List<Event>>() {
            @Override
            public void onResponse(Call<List<Event>> call, Response<List<Event>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EventCache.setAllEvents(response.body());
                }
                allEventsLoaded = true;
                tryFinishEarly();
            }

            @Override
            public void onFailure(Call<List<Event>> call, Throwable t) {
                allEventsLoaded = true;
                tryFinishEarly();
            }
        });
    }

    private void prefetchMyEventsData(String uid) {
        RegistrationRepository registrationRepository = new RegistrationRepository();

        registrationRepository.getRegistrationsForUser(uid).enqueue(new Callback<List<Registration>>() {
            @Override
            public void onResponse(Call<List<Registration>> call, Response<List<Registration>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    PrefetchCache.setMyEventsData(new ArrayList<>(), new ArrayList<>());
                    myEventsLoaded = true;
                    tryFinishEarly();
                    return;
                }

                List<Registration> registrations = response.body();

                StringBuilder idsBuilder = new StringBuilder("in.(");
                int added = 0;
                for (Registration registration : registrations) {
                    if (registration.getEventId() == null) continue;
                    if (added > 0) idsBuilder.append(",");
                    idsBuilder.append(registration.getEventId());
                    added++;
                }
                idsBuilder.append(")");

                if (added == 0) {
                    PrefetchCache.setMyEventsData(registrations, new ArrayList<>());
                    myEventsLoaded = true;
                    tryFinishEarly();
                    return;
                }

                new EventRepository().getEventsByIds(idsBuilder.toString())
                        .enqueue(new Callback<List<Event>>() {
                            @Override
                            public void onResponse(Call<List<Event>> call, Response<List<Event>> eventResponse) {
                                List<Event> events = (eventResponse.isSuccessful() && eventResponse.body() != null)
                                        ? eventResponse.body()
                                        : new ArrayList<>();
                                PrefetchCache.setMyEventsData(registrations, events);
                                myEventsLoaded = true;
                                tryFinishEarly();
                            }

                            @Override
                            public void onFailure(Call<List<Event>> call, Throwable t) {
                                PrefetchCache.setMyEventsData(registrations, new ArrayList<>());
                                myEventsLoaded = true;
                                tryFinishEarly();
                            }
                        });
            }

            @Override
            public void onFailure(Call<List<Registration>> call, Throwable t) {
                myEventsLoaded = true;
                tryFinishEarly();
            }
        });
    }

    private void tryFinishEarly() {
        if (userLoaded && eventsLoaded && allEventsLoaded && myEventsLoaded) {
            navigateToDashboardIfReady();
        }
    }

    private void navigateToDashboardIfReady() {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = MIN_SPLASH_MS - elapsed;

        if (remaining > 0) {
            handler.postDelayed(this::goToDashboard, remaining);
        } else {
            goToDashboard();
        }
    }

    private void goToDashboard() {
        if (navigated) return;
        navigated = true;

        User user = UserCache.get(SplashActivity.this);
        Class<?> destination = (user != null && "admin".equals(user.getRole()))
                ? AdminDashboard.class
                : StudentDashboard.class;

        startActivity(new Intent(SplashActivity.this, destination));
        finish();
    }

    private void goToWelcome() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(SplashActivity.this, WelcomeActivity.class));
        finish();
    }
}