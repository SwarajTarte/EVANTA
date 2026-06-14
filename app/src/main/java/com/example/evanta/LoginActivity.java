package com.example.evanta;

import static android.app.ProgressDialog.show;

import android.content.Intent;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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

import java.lang.reflect.Parameter;
import java.security.Policy;

public class LoginActivity extends AppCompatActivity {

    TextView signup;
    TextInputEditText emailin, passwIn;
    MaterialButton contbut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        signup = findViewById(R.id.signup);
        emailin = findViewById(R.id.emailin);
        passwIn = findViewById(R.id.passwIn);
        contbut = findViewById(R.id.contbut);

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


    }
    private void loginuser() {
        String email = emailin.getText().toString().trim();
        String password = passwIn.getText().toString().trim();

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

        String emaileg = "swaraj@gmail.com";
        String passeg = "123456";

        if(email.equals(emaileg) && password.equals(passeg)){
            Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
        }
    }
}