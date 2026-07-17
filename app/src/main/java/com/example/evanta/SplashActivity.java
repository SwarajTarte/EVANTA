package com.example.evanta;

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

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final int SAFETY_TIMEOUT_MS = 4000;
    private static final int MIN_SPLASH_MS = 1200;

    private boolean userLoaded = false;
    private boolean eventsLoaded = false;
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
            // Nothing to prefetch — just enforce a minimum splash time, then go to Welcome.
            handler.postDelayed(this::goToWelcome, MIN_SPLASH_MS);
            return;
        }

        // Safety net: if network is slow/unreachable, don't trap the user on
        // the splash screen forever.
        handler.postDelayed(this::navigateToDashboardIfReady, SAFETY_TIMEOUT_MS);

        prefetchUser(currentUser.getUid());
        prefetchFeaturedEvents();
    }

    private void prefetchUser(String uid) {

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getUserByUid("eq." + uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    UserCache.set(SplashActivity.this, response.body().get(0));
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

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getFeaturedEvents("eq.true", "date_start.asc", 3).enqueue(new Callback<List<Event>>() {
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

    private void tryFinishEarly() {
        if (userLoaded && eventsLoaded) {
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
        startActivity(new Intent(SplashActivity.this, StudentDashboard.class));
        finish();
    }

    private void goToWelcome() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(SplashActivity.this, WelcomeActivity.class));
        finish();
    }
}