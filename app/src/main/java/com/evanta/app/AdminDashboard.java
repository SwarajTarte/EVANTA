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

public class AdminDashboard extends AppCompatActivity {

    private NavController navController;

    // Custom bottom nav views
    private LinearLayout tabEvents, tabAddEvent, tabProfile;
    private ImageView iconEvents, iconAddEvent, iconProfile;
    private TextView labelEvents, labelAddEvent, labelProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge
        EdgeToEdgeUtils.enableAlwaysDark(this);

        setContentView(R.layout.activity_admin_dashboard);

        // Transparent system bars
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Apply system bar insets
        View main = findViewById(R.id.main);
        MaterialCardView bottomNavCard = findViewById(R.id.admin_bottom_nav_card);
        final int floatingBottomMargin =
                (int) (16 * getResources().getDisplayMetrics().density);

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

        // Check login
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        // Ask for notification permission (Android 13+) and register this
        // device's FCM token so the backend can push to it.
        PushSetup.ensure(this);

        // Navigation setup
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.admin_nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        setupCustomBottomNav();

        if (navController != null) {

            navController.addOnDestinationChangedListener(
                    (controller, destination, arguments) ->
                            updateSelectedTab(destination.getId())
            );
        }
    }

    private void setupCustomBottomNav() {

        tabEvents = findViewById(R.id.admin_tab_events);
        tabAddEvent = findViewById(R.id.admin_tab_add_event);
        tabProfile = findViewById(R.id.admin_tab_profile);

        iconEvents = findViewById(R.id.admin_icon_events);
        iconAddEvent = findViewById(R.id.admin_icon_add_event);
        iconProfile = findViewById(R.id.admin_icon_profile);

        labelEvents = findViewById(R.id.admin_label_events);
        labelAddEvent = findViewById(R.id.admin_label_add_event);
        labelProfile = findViewById(R.id.admin_label_profile);

        tabEvents.setOnClickListener(v -> navigateTo(R.id.adminEventsFragment));
        tabAddEvent.setOnClickListener(v -> navigateTo(R.id.adminAddEventFragment));
        tabProfile.setOnClickListener(v -> navigateTo(R.id.adminProfileFragment));
    }

    private void navigateTo(int destinationId) {

        if (navController == null) return;

        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return;
        }

        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                        navController.getGraph().getStartDestinationId(),
                        false,
                        true
                )
                .build();

        navController.navigate(destinationId, null, options);
    }

    private void updateSelectedTab(int destinationId) {

        setTabState(iconEvents, labelEvents,
                destinationId == R.id.adminEventsFragment);

        setTabState(iconAddEvent, labelAddEvent,
                destinationId == R.id.adminAddEventFragment);

        setTabState(iconProfile, labelProfile,
                destinationId == R.id.adminProfileFragment);
    }

    private void setTabState(ImageView icon,
                             TextView label,
                             boolean selected) {

        int color = ContextCompat.getColor(
                this,
                selected ? R.color.nav_selected : R.color.nav_unselected
        );

        ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(color)
        );

        label.setTextColor(color);
    }
}