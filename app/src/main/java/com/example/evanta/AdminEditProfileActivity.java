package com.example.evanta;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;

public class AdminEditProfileActivity extends AppCompatActivity {

    private ImageView editAvatarImage;
    private TextView editAvatarInitial;
    private EditText nameInput, whatsappInput;
    private MaterialButton saveBut;

    private String currentUid;
    private String pendingPhotoUrl = null;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadPhoto(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_admin_edit_profile);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        currentUid = currentUser.getUid();

        editAvatarImage  = findViewById(R.id.edit_avatar_image);
        editAvatarInitial = findViewById(R.id.edit_avatar_initial);
        nameInput        = findViewById(R.id.edit_name_input);
        whatsappInput    = findViewById(R.id.edit_whatsapp_input);
        saveBut          = findViewById(R.id.save_profile_but);

        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());
        findViewById(R.id.edit_avatar_camera_icon).setOnClickListener(v ->
                pickImageLauncher.launch("image/*"));

        loadCurrentUser();
        saveBut.setOnClickListener(v -> saveChanges());
    }

    private void loadCurrentUser() {
        new UserRepository().getUserByUid(currentUid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call,
                                   retrofit2.Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    nameInput.setText(user.getName());
                    whatsappInput.setText(user.getWhatsappno());

                    if (user.getName() != null && !user.getName().trim().isEmpty()) {
                        editAvatarInitial.setText(
                                String.valueOf(user.getName().trim().charAt(0)).toUpperCase());
                    }

                    if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                        pendingPhotoUrl = user.getPhotoUrl();
                        showPhoto(user.getPhotoUrl());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(AdminEditProfileActivity.this,
                        t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPhoto(String url) {
        editAvatarImage.setVisibility(ImageView.VISIBLE);
        editAvatarInitial.setVisibility(TextView.GONE);
        Glide.with(this).load(url).circleCrop().into(editAvatarImage);
    }

    private void uploadPhoto(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), uri);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
                byte[] imageBytes = stream.toByteArray();

                String fileName = currentUid + ".jpg";
                String uploadUrl = SupabaseConfig.BASE_URL
                        + "storage/v1/object/avatars/" + fileName;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(imageBytes,
                        MediaType.parse("image/jpeg"));
                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .put(body)
                        .addHeader("apikey", SupabaseConfig.API_KEY)
                        .addHeader("Authorization", "Bearer " + AuthTokens.bearer())
                        .addHeader("x-upsert", "true")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String publicUrl = SupabaseConfig.BASE_URL
                            + "storage/v1/object/public/avatars/" + fileName;
                    pendingPhotoUrl = publicUrl;
                    runOnUiThread(() -> showPhoto(publicUrl));
                } else {
                    runOnUiThread(() -> Toast.makeText(AdminEditProfileActivity.this,
                            "Upload failed: " + response.code(),
                            Toast.LENGTH_SHORT).show());
                }
                response.close();
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(AdminEditProfileActivity.this,
                        "Upload error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveChanges() {
        String newName = nameInput.getText().toString().trim();
        String newWhatsapp = whatsappInput.getText().toString().trim();

        if (newName.isEmpty()) {
            nameInput.setError("Name cannot be empty");
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("name", newName);
        fields.put("whatsappno", newWhatsapp);

        if (pendingPhotoUrl != null) {
            fields.put("photo_url", pendingPhotoUrl);
        }

        new UserRepository().updateUser(currentUid, fields).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call,
                                   retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AdminEditProfileActivity.this,
                            "Profile updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(AdminEditProfileActivity.this,
                            "Update failed: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(AdminEditProfileActivity.this,
                        t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}