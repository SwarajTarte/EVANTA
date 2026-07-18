package com.example.evanta;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

public class AdminDashboard extends AppCompatActivity {

    private static final int COLOR_ACTIVE   = 0xFF7C4DFF;
    private static final int COLOR_INACTIVE = 0xFF8A93A6;

    private LinearLayout tabEvents, tabAddEvent, tabProfile;
    private ImageView iconEvents, iconAddEvent, iconProfile;
    private TextView labelEvents, labelAddEvent, labelProfile;

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_admin_dashboard);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        // Views
        tabEvents    = findViewById(R.id.admin_tab_events);
        tabAddEvent  = findViewById(R.id.admin_tab_add_event);
        tabProfile   = findViewById(R.id.admin_tab_profile);
        iconEvents   = findViewById(R.id.admin_icon_events);
        iconAddEvent = findViewById(R.id.admin_icon_add_event);
        iconProfile  = findViewById(R.id.admin_icon_profile);
        labelEvents  = findViewById(R.id.admin_label_events);
        labelAddEvent = findViewById(R.id.admin_label_add_event);
        labelProfile = findViewById(R.id.admin_label_profile);

        // Nav
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.admin_nav_host_fragment);
        navController = navHostFragment.getNavController();

        // Tab clicks
        tabEvents.setOnClickListener(v -> navigateTo(R.id.adminEventsFragment));
        tabAddEvent.setOnClickListener(v -> navigateTo(R.id.adminAddEventFragment));
        tabProfile.setOnClickListener(v -> navigateTo(R.id.adminProfileFragment));

        // Keep tab highlight in sync with actual destination
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            int id = destination.getId();
            setTabActive(tabEvents,   iconEvents,   labelEvents,
                    id == R.id.adminEventsFragment);
            setTabActive(tabAddEvent, iconAddEvent, labelAddEvent,
                    id == R.id.adminAddEventFragment);
            setTabActive(tabProfile,  iconProfile,  labelProfile,
                    id == R.id.adminProfileFragment);
        });

        // Insets — same pattern as StudentDashboard
        View main = findViewById(R.id.main);
        MaterialCardView navCard = findViewById(R.id.admin_bottom_nav_card);

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(0, systemBars.top, 0, 0);

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) navCard.getLayoutParams();
            params.bottomMargin = 0;
            navCard.setLayoutParams(params);

            navCard.setPadding(
                    navCard.getPaddingLeft(),
                    navCard.getPaddingTop(),
                    navCard.getPaddingRight(),
                    systemBars.bottom);

            return insets;
        });

        // Start on Events tab
        setTabActive(tabEvents, iconEvents, labelEvents, true);
    }

    private void navigateTo(int destinationId) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) return;

        navController.navigate(destinationId,
                null,
                new androidx.navigation.NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(R.id.admin_nav_graph, false, true)
                        .build());
    }

    private void setTabActive(LinearLayout tab, ImageView icon,
                              TextView label, boolean active) {
        int color = active ? COLOR_ACTIVE : COLOR_INACTIVE;
        icon.setColorFilter(color);
        label.setTextColor(color);
    }
}