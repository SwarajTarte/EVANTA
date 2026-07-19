package com.evanta.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

public class StudentDashboard extends AppCompatActivity {

    private NavController navController;

    // Custom bottom nav views
    private LinearLayout navHome, navBrowse, navMyEvents, navProfile;
    private ImageView navHomeIcon, navBrowseIcon, navMyEventsIcon, navProfileIcon;
    private TextView navHomeLabel, navBrowseLabel, navMyEventsLabel, navProfileLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge
        EdgeToEdgeUtils.enableAlwaysDark(this);

        setContentView(R.layout.activity_student_dashboard);

        // Transparent system bars
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Apply system bar insets.
        // Top inset becomes padding on the root (so content clears the status bar)
        // while the gradient background still paints all the way behind it.
        // Bottom inset is applied as extra bottom margin on the floating bottom
        // nav bar so it floats just above the gesture/nav bar instead of being
        // overlapped by it — the gradient itself still extends fully behind it.
        View main = findViewById(R.id.main);
        MaterialCardView bottomNavCard = findViewById(R.id.bottom_nav);
        final int floatingBottomMargin = (int) (16 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {

            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets systemGestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
            int bottomInset = Math.max(systemBars.bottom, systemGestures.bottom);
            v.setPadding(
                    0,
                    systemBars.top,
                    0,
                    0
            );

            ViewGroup.MarginLayoutParams navParams =
                    (ViewGroup.MarginLayoutParams) bottomNavCard.getLayoutParams();
            navParams.bottomMargin = bottomInset + floatingBottomMargin;
            bottomNavCard.setLayoutParams(navParams);

            return insets;
        });

        // Check if user is logged in
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        // Navigation setup
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        setupCustomBottomNav();

        if (navController != null) {
            // Fires immediately with the current destination, keeping the
            // custom tab bar in sync with whatever NavController is showing.
            navController.addOnDestinationChangedListener(
                    (controller, destination, arguments) -> updateSelectedTab(destination.getId()));
        }
    }

    private void setupCustomBottomNav() {

        navHome = findViewById(R.id.nav_home);
        navBrowse = findViewById(R.id.nav_browse);
        navMyEvents = findViewById(R.id.nav_my_events);
        navProfile = findViewById(R.id.nav_profile);

        navHomeIcon = findViewById(R.id.nav_home_icon);
        navBrowseIcon = findViewById(R.id.nav_browse_icon);
        navMyEventsIcon = findViewById(R.id.nav_my_events_icon);
        navProfileIcon = findViewById(R.id.nav_profile_icon);

        navHomeLabel = findViewById(R.id.nav_home_label);
        navBrowseLabel = findViewById(R.id.nav_browse_label);
        navMyEventsLabel = findViewById(R.id.nav_my_events_label);
        navProfileLabel = findViewById(R.id.nav_profile_label);

        navHome.setOnClickListener(v -> navigateTo(R.id.homeFragment));
        navBrowse.setOnClickListener(v -> navigateTo(R.id.browseFragment));
        navMyEvents.setOnClickListener(v -> navigateTo(R.id.myEventsFragment));
        navProfile.setOnClickListener(v -> navigateTo(R.id.profileFragment));
    }

    private void navigateTo(int destinationId) {

        if (navController == null) return;

        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return;
        }

        // Mirrors what NavigationUI.setupWithNavController does for a bottom
        // nav bar: single top, restore previously saved state per tab, and
        // pop back to the graph's start destination so tabs don't stack up.
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                .build();

        navController.navigate(destinationId, null, options);
    }

    private void updateSelectedTab(int destinationId) {
        setTabState(navHomeIcon, navHomeLabel, destinationId == R.id.homeFragment);
        setTabState(navBrowseIcon, navBrowseLabel, destinationId == R.id.browseFragment);
        setTabState(navMyEventsIcon, navMyEventsLabel, destinationId == R.id.myEventsFragment);
        setTabState(navProfileIcon, navProfileLabel, destinationId == R.id.profileFragment);
    }

    private void setTabState(ImageView icon, TextView label, boolean selected) {
        int color = ContextCompat.getColor(this, selected ? R.color.nav_selected : R.color.nav_unselected);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
        label.setTextColor(color);
    }
}
