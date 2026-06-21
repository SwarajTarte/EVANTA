package com.example.evanta;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudentDashboard extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    private TextView nameView, emailView, whatsappView;
    private MaterialButton logoutBut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN);

        nameView = findViewById(R.id.name);
        emailView = findViewById(R.id.email);
        whatsappView = findViewById(R.id.whatsapp);
        logoutBut = findViewById(R.id.logoutbut);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        // Show what we already have immediately, then refresh from Supabase
        nameView.setText(currentUser.getDisplayName());
        emailView.setText(currentUser.getEmail());

        loadUserFromSupabase(currentUser.getUid());

        logoutBut.setOnClickListener(v -> logout());
    }

    private void loadUserFromSupabase(String uid) {

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getUserByUid("eq." + uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {

                    User user = response.body().get(0);

                    nameView.setText(user.getName());
                    emailView.setText(user.getEmail());
                    whatsappView.setText(user.getWhatsappno());

                } else if (response.isSuccessful()) {

                    // Request succeeded but returned zero rows for this uid.
                    Log.d("StudentDashboard", "Supabase returned 0 rows for uid=" + uid);
                    Toast.makeText(StudentDashboard.this,
                            "No profile row found in Supabase for this account. " +
                                    "Check the row exists and that RLS allows SELECT.",
                            Toast.LENGTH_LONG).show();

                } else {
                    Toast.makeText(StudentDashboard.this,
                            "Could not load profile: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(StudentDashboard.this,
                        t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void logout() {

        mAuth.signOut();

        googleSignInClient.signOut().addOnCompleteListener(task -> {

            Intent intent = new Intent(StudentDashboard.this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
