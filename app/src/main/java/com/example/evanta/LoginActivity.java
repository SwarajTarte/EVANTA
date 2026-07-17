package com.example.evanta;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import com.google.android.gms.tasks.Task;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    TextView signup, forgot;
    TextInputEditText emailin, passwIn;
    MaterialButton contbut;
    AppCompatImageButton google, facebook;

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() == RESULT_OK) {

                            Task<GoogleSignInAccount> task =
                                    GoogleSignIn.getSignedInAccountFromIntent(result.getData());

                            try {

                                GoogleSignInAccount account =
                                        task.getResult(ApiException.class);

                                firebaseAuthWithGoogle(account.getIdToken());

                            } catch (ApiException e) {
                                Toast.makeText(this,
                                        e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("492602268309-vhhkifceamsa27d7lq5ias8kol79djbj.apps.googleusercontent.com")
                        .requestEmail()
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        signup = findViewById(R.id.signup);
        emailin = findViewById(R.id.emailin);
        passwIn = findViewById(R.id.passwIn);
        contbut = findViewById(R.id.contbut);
        forgot = findViewById(R.id.forgot);
        google = findViewById(R.id.google);
        facebook = findViewById(R.id.facebook);

        google.setOnClickListener(v -> signInWithGoogle());

        contbut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginuser();
            }
        });

        signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
                startActivity(intent);
            }
        });

        forgot.setOnClickListener(v -> {

            String email = emailin.getText().toString().trim();

            if (email.isEmpty()) {
                emailin.setError("Enter your email first");
                emailin.requestFocus();
                return;
            }

            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            Toast.makeText(LoginActivity.this,
                                    "Password reset link sent to your email.",
                                    Toast.LENGTH_LONG).show();

                        } else {

                            Toast.makeText(LoginActivity.this,
                                    task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }

                    });
        });


    }

    private void loginuser() {

        String email = emailin.getText().toString().trim();
        String password = passwIn.getText().toString().trim();

        if (email.isEmpty()) {
            emailin.setError("Email is required");
            emailin.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailin.setError("Please provide a valid email");
            emailin.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwIn.setError("Password is required");
            passwIn.requestFocus();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(LoginActivity.this,
                                "Login Successful",
                                Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(LoginActivity.this,
                                StudentDashboard.class);
                        startActivity(intent);
                        finish();

                    } else {

                        Toast.makeText(LoginActivity.this,
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();

                    }

                });
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {

        AuthCredential credential =
                GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser != null) {

                            String uid = firebaseUser.getUid();
                            String name = firebaseUser.getDisplayName();
                            String email = firebaseUser.getEmail();

                            User user = new User(uid, name, email, "");

                            UserRepository userRepository = new UserRepository();

                            userRepository.upsertUser(user).enqueue(new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {

                                    if (response.isSuccessful()) {
                                        Toast.makeText(LoginActivity.this,
                                                "User saved to Supabase",
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(LoginActivity.this,
                                                "Supabase Error: " + response.code(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    Toast.makeText(LoginActivity.this,
                                            t.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        Toast.makeText(this,
                                "Google Sign-In Successful",
                                Toast.LENGTH_SHORT).show();

                        startActivity(new Intent(LoginActivity.this,
                                StudentDashboard.class));

                        finish();

                    } else {

                        Toast.makeText(this,
                                "Authentication Failed",
                                Toast.LENGTH_SHORT).show();
                    }

                });
    }

}
