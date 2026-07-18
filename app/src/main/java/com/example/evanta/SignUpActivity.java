package com.example.evanta;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignUpActivity extends AppCompatActivity {

    TextView signin;
    TextInputEditText nameIn, emailin, passwIn, confpasswordin,
            whatsappno, collegeInput, branchInput;
    MaterialButton createac;

    private FirebaseAuth mAuth;
    private UserRepository userRepository;

    private List<College> collegeList = new ArrayList<>();
    private String selectedCollegeId = null;
    private String selectedCollegeName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mAuth = FirebaseAuth.getInstance();
        userRepository = new UserRepository();

        signin = findViewById(R.id.signin);
        nameIn = findViewById(R.id.nameIn);
        emailin = findViewById(R.id.emailin);
        passwIn = findViewById(R.id.passwIn);
        confpasswordin = findViewById(R.id.confpasswordin);
        createac = findViewById(R.id.createac);
        whatsappno = findViewById(R.id.whatsappno);
        collegeInput = findViewById(R.id.college_input);
        branchInput = findViewById(R.id.branch_input);

        signin.setOnClickListener(v ->
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class)));

        collegeInput.setOnClickListener(v -> showCollegePicker());

        createac.setOnClickListener(v -> createaccount());

        loadColleges();
    }

    private void loadColleges() {
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.getColleges().enqueue(new Callback<List<College>>() {
            @Override
            public void onResponse(Call<List<College>> call,
                                   Response<List<College>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    collegeList = response.body();
                }
            }

            @Override
            public void onFailure(Call<List<College>> call, Throwable t) {
                Toast.makeText(SignUpActivity.this,
                        "Could not load colleges", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCollegePicker() {
        if (collegeList.isEmpty()) {
            Toast.makeText(this, "Loading colleges, please wait...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[collegeList.size() + 1];
        for (int i = 0; i < collegeList.size(); i++) {
            names[i] = collegeList.get(i).getName();
        }
        names[collegeList.size()] = "Other (type manually)";

        new AlertDialog.Builder(this)
                .setTitle("Select Your College")
                .setItems(names, (dialog, which) -> {
                    if (which == collegeList.size()) {
                        selectedCollegeId = null;
                        selectedCollegeName = null;
                        collegeInput.setText("");
                        collegeInput.setFocusableInTouchMode(true);
                        collegeInput.setFocusable(true);
                        collegeInput.requestFocus();
                        collegeInput.setHint("Type your college name");
                    } else {
                        College selected = collegeList.get(which);
                        selectedCollegeId = selected.getId();
                        selectedCollegeName = selected.getName();
                        collegeInput.setText(selectedCollegeName);
                        collegeInput.setFocusable(false);
                        collegeInput.setFocusableInTouchMode(false);
                    }
                })
                .show();
    }

    private void createaccount() {
        String name = nameIn.getText().toString().trim();
        String email = emailin.getText().toString().trim();
        String whatsno = whatsappno.getText().toString().trim();
        String collegeText = collegeInput.getText().toString().trim();
        String branch = branchInput.getText().toString().trim();
        String password = passwIn.getText().toString().trim();
        String confpassword = confpasswordin.getText().toString().trim();

        if (name.isEmpty()) {
            nameIn.setError("Name is required");
            nameIn.requestFocus();
            return;
        }

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

        if (whatsno.isEmpty()) {
            whatsappno.setError("WhatsApp number is required");
            whatsappno.requestFocus();
            return;
        }

        if (!whatsno.matches("\\d{10}")) {
            whatsappno.setError("Enter a valid 10-digit number");
            whatsappno.requestFocus();
            return;
        }

        if (collegeText.isEmpty()) {
            collegeInput.setError("Please select or type your college");
            collegeInput.requestFocus();
            return;
        }

        if (selectedCollegeId == null) {
            selectedCollegeName = collegeText;
        }

        if (branch.isEmpty()) {
            branchInput.setError("Branch is required");
            branchInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwIn.setError("Password is required");
            passwIn.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwIn.setError("Password must be at least 6 characters");
            passwIn.requestFocus();
            return;
        }

        if (confpassword.isEmpty()) {
            confpasswordin.setError("Please confirm your password");
            confpasswordin.requestFocus();
            return;
        }

        if (!password.equals(confpassword)) {
            confpasswordin.setError("Password does not match");
            confpasswordin.requestFocus();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();

                        User user = new User(uid, name, email, whatsno);
                        user.setCollegeId(selectedCollegeId);
                        user.setBranch(branch);
                        if (selectedCollegeId == null) {
                            user.setCollegeName(selectedCollegeName);
                        }

                        userRepository.upsertUser(user).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call,
                                                   Response<Void> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(SignUpActivity.this,
                                            "Registration Successful!",
                                            Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(SignUpActivity.this,
                                            LoginActivity.class));
                                    finish();
                                } else {
                                    Toast.makeText(SignUpActivity.this,
                                            "Supabase Error: " + response.code(),
                                            Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(SignUpActivity.this,
                                        t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        Toast.makeText(SignUpActivity.this,
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}