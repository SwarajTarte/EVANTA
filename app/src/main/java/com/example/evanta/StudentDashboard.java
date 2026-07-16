package com.example.evanta;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class StudentDashboard extends AppCompatActivity {

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
        BottomNavigationView bottomNavForInsets = findViewById(R.id.bottom_nav);
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
                    (ViewGroup.MarginLayoutParams) bottomNavForInsets.getLayoutParams();
            navParams.bottomMargin = bottomInset + floatingBottomMargin;
            bottomNavForInsets.setLayoutParams(navParams);

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
            NavController navController = navHostFragment.getNavController();

            NavigationUI.setupWithNavController(bottomNavForInsets, navController);
        }
    }
}