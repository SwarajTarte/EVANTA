package com.example.evanta;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getUsers().enqueue(new retrofit2.Callback<java.util.List<Object>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<Object>> call,
                                   retrofit2.Response<java.util.List<Object>> response) {

                if (response.isSuccessful()) {
                    android.util.Log.d("SUPABASE", "Connected!");
                    android.util.Log.d("SUPABASE", response.body().toString());
                } else {
                    android.util.Log.e("SUPABASE",
                            "Error Code: " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<Object>> call,
                                  Throwable t) {

                android.util.Log.e("SUPABASE",
                        "Connection Failed: " + t.getMessage());
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, WelcomeActivity.class);
            startActivity(intent);
            finish();
        }, 2000);
    }
}