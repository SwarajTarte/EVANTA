package com.example.evanta;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignUpActivity extends AppCompatActivity {

    TextView signin;
    TextInputEditText nameIn, emailin, passwIn, confpasswordin, whatsappno;
    MaterialButton createac;

    private FirebaseAuth mAuth;
    private SupabaseApi api;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mAuth = FirebaseAuth.getInstance();
        api = RetrofitClient.getClient().create(SupabaseApi.class);

        signin = findViewById(R.id.signin);
        nameIn = findViewById(R.id.nameIn);
        emailin = findViewById(R.id.emailin);
        passwIn = findViewById(R.id.passwIn);
        confpasswordin = findViewById(R.id.confpasswordin);
        createac = findViewById(R.id.createac);
        whatsappno = findViewById(R.id.whatsappno);

        signin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });

        createac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createaccount();
            }
        });
    }

    private void createaccount(){
        String email = emailin.getText().toString().trim();
        String password = passwIn.getText().toString().trim();
        String confpassword = confpasswordin.getText().toString().trim();
        String name = nameIn.getText().toString().trim();
        String whatsno = whatsappno.getText().toString().trim();

        if(name.isEmpty()){
            nameIn.setError("Name is required");
            nameIn.requestFocus();
            return;
        }

        if(email.isEmpty()){
            emailin.setError("Email is required");
            emailin.requestFocus();
            return;
        }

        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailin.setError("Please provide a valid email");
            emailin.requestFocus();
            return;
        }

        if(password.isEmpty()){
            passwIn.setError("Password is required");
            passwIn.requestFocus();
            return;
        }

        if(password.length() < 6){
            passwIn.setError("Password must be at least 6 characters");
            passwIn.requestFocus();
            return;
        }

        if(confpassword.isEmpty()){
            confpasswordin.setError("Password is required");
            confpasswordin.requestFocus();
            return;
        }

        if(!password.equals(confpassword)){
            confpasswordin.setError("Password does not match");
            confpasswordin.requestFocus();
            return;
        }

        if(whatsno.isEmpty()){
            whatsappno.setError("WhatsApp number is required");
            whatsappno.requestFocus();
            return;
        }

        if(!whatsno.matches("\\d{10}")){
            whatsappno.setError("Enter a valid 10-digit number");
            whatsappno.requestFocus();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        String uid = mAuth.getCurrentUser().getUid();

                        User user = new User(
                                uid,
                                name,
                                email,
                                whatsno
                        );

                        api.insertUser(user).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {

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
                                        t.getMessage(),
                                        Toast.LENGTH_LONG).show();
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