package com.evanta.app;

import android.app.AlertDialog;
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
import java.util.ArrayList;
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

public class EditProfileActivity extends AppCompatActivity {

    private ImageView editAvatarImage;
    private TextView editAvatarInitial;
    private EditText nameInput, whatsappInput, collegeInput, branchInput;
    private MaterialButton saveBut;

    private String currentUid;
    private String pendingPhotoUrl = null;

    private List<College> collegeList = new ArrayList<>();
    private String selectedCollegeId = null;
    private String selectedCollegeName = null;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadPhoto(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_edit_profile);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        currentUid = currentUser.getUid();

        editAvatarImage = findViewById(R.id.edit_avatar_image);
        editAvatarInitial = findViewById(R.id.edit_avatar_initial);
        nameInput = findViewById(R.id.edit_name_input);
        whatsappInput = findViewById(R.id.edit_whatsapp_input);
        collegeInput = findViewById(R.id.edit_college_input);
        branchInput = findViewById(R.id.edit_branch_input);
        saveBut = findViewById(R.id.save_profile_but);

        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());
        findViewById(R.id.edit_avatar_camera_icon).setOnClickListener(v ->
                pickImageLauncher.launch("image/*"));
        collegeInput.setOnClickListener(v -> showCollegePicker());

        loadColleges();
        loadCurrentUser();

        saveBut.setOnClickListener(v -> saveChanges());
    }

    // ---------- Load colleges ----------

    private void loadColleges() {
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.getColleges().enqueue(new Callback<List<College>>() {
            @Override
            public void onResponse(Call<List<College>> call,
                                   retrofit2.Response<List<College>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    collegeList = response.body();
                }
            }

            @Override
            public void onFailure(Call<List<College>> call, Throwable t) {}
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

    // ---------- Load current user ----------

    private void loadCurrentUser() {
        new UserRepository().getUserByUid(currentUid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call,
                                   retrofit2.Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    bindUser(user);
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(EditProfileActivity.this,
                        t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUser(User user) {
        nameInput.setText(user.getName());
        whatsappInput.setText(user.getWhatsappno());
        branchInput.setText(user.getBranch());

        if (user.getName() != null && !user.getName().trim().isEmpty()) {
            editAvatarInitial.setText(
                    String.valueOf(user.getName().trim().charAt(0)).toUpperCase());
        }

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            pendingPhotoUrl = user.getPhotoUrl();
            showPhoto(user.getPhotoUrl());
        }

        // Pre-fill college
        if (user.getCollegeId() != null) {
            selectedCollegeId = user.getCollegeId();
            // Find the name from the loaded list if available, else fetch it
            for (College c : collegeList) {
                if (c.getId().equals(selectedCollegeId)) {
                    selectedCollegeName = c.getName();
                    collegeInput.setText(selectedCollegeName);
                    return;
                }
            }
            // List not loaded yet — fetch by ID
            SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
            api.getCollegeById("eq." + selectedCollegeId).enqueue(
                    new Callback<List<College>>() {
                        @Override
                        public void onResponse(Call<List<College>> call,
                                               retrofit2.Response<List<College>> r) {
                            if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                                selectedCollegeName = r.body().get(0).getName();
                                collegeInput.setText(selectedCollegeName);
                            }
                        }

                        @Override
                        public void onFailure(Call<List<College>> call, Throwable t) {}
                    });

        } else if (user.getCollegeName() != null && !user.getCollegeName().isEmpty()) {
            collegeInput.setText(user.getCollegeName());
            collegeInput.setFocusableInTouchMode(true);
            collegeInput.setFocusable(true);
        }
    }

    // ---------- Photo ----------

    private void showPhoto(String url) {
        editAvatarImage.setVisibility(ImageView.VISIBLE);
        editAvatarInitial.setVisibility(TextView.GONE);
        Glide.with(this).load(url).circleCrop().into(editAvatarImage);
    }

    private void uploadPhoto(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
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
                    String errorBody = response.body() != null
                            ? response.body().string() : "no body";
                    android.util.Log.e("UPLOAD_ERROR",
                            "Code: " + response.code() + " Body: " + errorBody);
                    runOnUiThread(() -> Toast.makeText(EditProfileActivity.this,
                            "Upload failed: " + response.code(),
                            Toast.LENGTH_SHORT).show());
                }
                response.close();
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(EditProfileActivity.this,
                        "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ---------- Save ----------

    private void saveChanges() {
        String newName = nameInput.getText().toString().trim();
        String newWhatsapp = whatsappInput.getText().toString().trim();
        String newBranch = branchInput.getText().toString().trim();
        String collegeText = collegeInput.getText().toString().trim();

        if (newName.isEmpty()) {
            nameInput.setError("Name cannot be empty");
            return;
        }

        if (collegeText.isEmpty()) {
            collegeInput.setError("College cannot be empty");
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("name", newName);
        fields.put("whatsappno", newWhatsapp);
        fields.put("branch", newBranch);

        if (selectedCollegeId != null) {
            fields.put("college_id", selectedCollegeId);
            fields.put("college_name", null);
        } else {
            fields.put("college_id", null);
            fields.put("college_name", collegeText);
        }

        if (pendingPhotoUrl != null) {
            fields.put("photo_url", pendingPhotoUrl);
        }

        RetrofitClient.getClientWithNulls().create(SupabaseApi.class)
                .updateUser("eq." + currentUid, fields).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(EditProfileActivity.this,
                            "Profile updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(EditProfileActivity.this,
                            "Update failed: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(EditProfileActivity.this,
                        t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}