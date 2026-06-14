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

public class SignUpActivity extends AppCompatActivity {

    TextView signin;
    TextInputEditText nameIn, emailin, passwIn, confpasswordin;
    MaterialButton createac;


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

        signin = findViewById(R.id.signin);
        nameIn = findViewById(R.id.nameIn);
        emailin = findViewById(R.id.emailin);
        passwIn = findViewById(R.id.passwIn);
        confpasswordin = findViewById(R.id.confpasswordin);
        createac = findViewById(R.id.createac);

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

        Toast.makeText(this, "Account Created Successfully", Toast.LENGTH_SHORT).show();
    }
}