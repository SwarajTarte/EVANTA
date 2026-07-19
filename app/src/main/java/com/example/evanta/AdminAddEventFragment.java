package com.example.evanta;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;

public class AdminAddEventFragment extends Fragment {

    private static final long MAX_IMAGE_BYTES = 1024 * 1024; // 1MB
    private static final int MAX_FEATURED_PER_COLLEGE = 3;
    private static final String[] CATEGORIES =
            {"Tech", "Cultural", "Sports", "Workshop", "Music"};

    private EditText inputTitle, inputSubtitle, inputDescription, inputLocation, inputCity,
            inputPrice, inputCapacity;
    private TextView inputCategory, inputStartDate, inputStartTime, inputEndDate,
            inputRegDeadline, counterDescription;
    private ImageView bannerPreview;
    private MaterialSwitch switchFeatured;
    private MaterialButton createButton;
    private ProgressBar createProgress;

    private String selectedCategory = null;
    private Uri pendingImageUri = null;
    private boolean isUploading = false;

    // Values in "yyyy-MM-dd" form ready for Supabase; startTime is an AM/PM string.
    private String startDate, endDate, startTime, regDeadline;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handlePickedImage(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_add_event, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        inputTitle = v.findViewById(R.id.input_title);
        inputSubtitle = v.findViewById(R.id.input_subtitle);
        inputDescription = v.findViewById(R.id.input_description);
        inputLocation = v.findViewById(R.id.input_location);
        inputCity = v.findViewById(R.id.input_city);
        inputPrice = v.findViewById(R.id.input_price);
        inputCapacity = v.findViewById(R.id.input_capacity);
        inputCategory = v.findViewById(R.id.input_category);
        inputStartDate = v.findViewById(R.id.input_start_date);
        inputStartTime = v.findViewById(R.id.input_start_time);
        inputEndDate = v.findViewById(R.id.input_end_date);
        inputRegDeadline = v.findViewById(R.id.input_reg_deadline);
        counterDescription = v.findViewById(R.id.counter_description);
        bannerPreview = v.findViewById(R.id.banner_preview);
        switchFeatured = v.findViewById(R.id.switch_featured);
        createButton = v.findViewById(R.id.create_event_button);
        createProgress = v.findViewById(R.id.create_event_progress);

        v.findViewById(R.id.add_back_button).setOnClickListener(x -> handleBackNavigation());

        // Handle physical back clicks while an upload task runs
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        inputCategory.setOnClickListener(x -> showCategoryPicker());
        inputStartDate.setOnClickListener(x -> pickDate(DATE_START));
        inputEndDate.setOnClickListener(x -> pickDate(DATE_END));
        inputRegDeadline.setOnClickListener(x -> pickDate(DATE_REG_DEADLINE));
        inputStartTime.setOnClickListener(x -> pickTime());
        v.findViewById(R.id.choose_image_button).setOnClickListener(x ->
                pickImageLauncher.launch("image/*"));

        createButton.setOnClickListener(x -> submit());

        inputDescription.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                counterDescription.setText(s.length() + "/200");
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void handleBackNavigation() {
        if (isUploading) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Upload in progress")
                    .setMessage("An event creation task is currently running. Leave anyway?")
                    .setPositiveButton("Leave", (dialog, which) -> {
                        isUploading = false;
                        if (getActivity() != null) {
                            getActivity().getSupportFragmentManager().popBackStack();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        }
    }

    // ---------- Category picker ----------

    private void showCategoryPicker() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Select Category")
                .setItems(CATEGORIES, (d, which) -> {
                    selectedCategory = CATEGORIES[which];
                    inputCategory.setText(selectedCategory);
                    inputCategory.setTextColor(0xFFFFFFFF);
                })
                .show();
    }

    // ---------- Date & time pickers ----------

    private static final int DATE_START = 0;
    private static final int DATE_END = 1;
    private static final int DATE_REG_DEADLINE = 2;

    private void pickDate(int which) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    String iso = String.format(Locale.US, "%04d-%02d-%02d",
                            year, month + 1, day);
                    String pretty = String.format(Locale.US, "%02d %s %04d",
                            day, monthName(month), year);
                    if (which == DATE_START) {
                        startDate = iso;
                        inputStartDate.setText(pretty);
                        inputStartDate.setTextColor(0xFFFFFFFF);
                    } else if (which == DATE_END) {
                        endDate = iso;
                        inputEndDate.setText(pretty);
                        inputEndDate.setTextColor(0xFFFFFFFF);
                    } else {
                        regDeadline = iso;
                        inputRegDeadline.setText(pretty);
                        inputRegDeadline.setTextColor(0xFFFFFFFF);
                    }
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void pickTime() {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(requireContext(),
                (view, hour, minute) -> {
                    int h12 = hour % 12 == 0 ? 12 : hour % 12;
                    String ampm = hour < 12 ? "AM" : "PM";
                    startTime = String.format(Locale.US, "%d:%02d %s", h12, minute, ampm);
                    inputStartTime.setText(startTime);
                    inputStartTime.setTextColor(0xFFFFFFFF);
                },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
    }

    private String monthName(int month) {
        String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        return names[month];
    }

    // ---------- Image pick ----------

    private void handlePickedImage(Uri uri) {
        pendingImageUri = uri;
        bannerPreview.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).centerCrop().into(bannerPreview);
    }

    private OkHttpClient getClientWithTimeoutsAndRetry() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @NonNull
                    @Override
                    public Response intercept(@NonNull Chain chain) throws IOException {
                        Request request = chain.request();
                        Response response = null;
                        IOException exception = null;

                        try {
                            response = chain.proceed(request);
                        } catch (IOException e) {
                            exception = e;
                        }

                        if (response == null || !response.isSuccessful()) {
                            if (response != null) response.close();
                            try {
                                response = chain.proceed(request);
                            } catch (IOException e) {
                                throw exception != null ? exception : e;
                            }
                        }
                        return response;
                    }
                })
                .build();
    }

    private String uploadImageBlocking(Uri uri, String eventTitle) {
        try (InputStream stream = requireContext().getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) return null;

            byte[] imageBytes = compressUnder(bitmap, MAX_IMAGE_BYTES);
            if (imageBytes == null) {
                runOnUi(() -> Toast.makeText(requireContext(),
                        "Image processing failed or file too large", Toast.LENGTH_SHORT).show());
                return null;
            }

            // Names the object by its sanitized event name alongside a unique UUID marker
            String fileName = eventTitle + "_" + UUID.randomUUID().toString() + ".jpg";
            String uploadUrl = SupabaseConfig.BASE_URL
                    + "storage/v1/object/event-covers/" + fileName;

            OkHttpClient client = getClientWithTimeoutsAndRetry();
            RequestBody body = RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .put(body)
                    .addHeader("apikey", SupabaseConfig.API_KEY)
                    .addHeader("Authorization", "Bearer " + AuthTokens.bearer())
                    .addHeader("x-upsert", "true")
                    .build();

            Response response = client.newCall(request).execute();
            try {
                if (response.isSuccessful()) {
                    return SupabaseConfig.BASE_URL
                            + "storage/v1/object/public/event-covers/" + fileName;
                }
                final int code = response.code();
                final String errBody = response.body() != null ? response.body().string() : "";
                android.util.Log.e("AddEvent", "Image upload failed " + code + ": " + errBody);
                runOnUi(() -> Toast.makeText(requireContext(),
                        uploadErrorMessage(code, errBody), Toast.LENGTH_SHORT).show());
                return null;
            } finally {
                response.close();
            }
        } catch (IOException e) {
            android.util.Log.e("AddEvent", "Image upload error", e);
            runOnUi(() -> Toast.makeText(requireContext(),
                    "Upload network error encountered", Toast.LENGTH_SHORT).show());
            return null;
        }
    }

    private void deleteUploadedImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        new Thread(() -> {
            try {
                String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                String deleteUrl = SupabaseConfig.BASE_URL + "storage/v1/object/event-covers/" + fileName;

                OkHttpClient client = getClientWithTimeoutsAndRetry();
                Request request = new Request.Builder()
                        .url(deleteUrl)
                        .delete()
                        .addHeader("apikey", SupabaseConfig.API_KEY)
                        .addHeader("Authorization", "Bearer " + AuthTokens.bearer())
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        android.util.Log.e("AddEvent", "Failed to clear orphan image: " + response.code());
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("AddEvent", "Error executing orphan cleanup deletion", e);
            }
        }).start();
    }

    private String uploadErrorMessage(int code, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.US);
        if (code == 400 && lower.contains("already exists")) {
            return "File already exists";
        }
        if (code == 401 || code == 403 || lower.contains("row-level security")
                || lower.contains("unauthorized") || lower.contains("policy")) {
            return "Upload not allowed";
        }
        if (code == 404 || lower.contains("bucket not found")
                || lower.contains("not found")) {
            return "Storage bucket not found";
        }
        if (code == 413 || lower.contains("payload too large")
                || lower.contains("exceeded the maximum")) {
            return "Image too large";
        }
        return "Upload failed (" + code + ")";
    }

    private byte[] compressUnder(Bitmap bitmap, long maxBytes) {
        Bitmap scaled = bitmap;
        if (scaled.getWidth() > 1600 || scaled.getHeight() > 1200) {
            float aspectRatio = (float) scaled.getWidth() / (float) scaled.getHeight();
            int width = 1600;
            int height = Math.round(width / aspectRatio);
            scaled = Bitmap.createScaledBitmap(scaled, width, height, true);
        }

        int quality = 85;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream);

        while (stream.size() > maxBytes && quality > 40) {
            quality -= 10;
            stream.reset();
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        }

        while (stream.size() > maxBytes && scaled.getWidth() > 600 && scaled.getHeight() > 600) {
            scaled = Bitmap.createScaledBitmap(scaled,
                    (int) (scaled.getWidth() * 0.8),
                    (int) (scaled.getHeight() * 0.8), true);
            stream.reset();
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        }

        return stream.size() <= maxBytes ? stream.toByteArray() : null;
    }

    // ---------- Submit ----------

    private void submit() {
        String title = inputTitle.getText().toString().trim();
        String subtitle = inputSubtitle.getText().toString().trim();
        String description = inputDescription.getText().toString().trim();
        String venue = inputLocation.getText().toString().trim();
        String city = inputCity.getText().toString().trim();
        String priceStr = inputPrice.getText().toString().trim();
        String capacityStr = inputCapacity.getText().toString().trim();

        if (title.isEmpty()) { inputTitle.setError("Required"); return; }
        if (selectedCategory == null) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        if (subtitle.isEmpty()) { inputSubtitle.setError("Required"); return; }
        if (description.isEmpty()) { inputDescription.setError("Required"); return; }
        if (startDate == null) {
            Toast.makeText(requireContext(), "Please select a start date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (startTime == null) {
            Toast.makeText(requireContext(), "Please select a start time", Toast.LENGTH_SHORT).show();
            return;
        }
        if (endDate == null) {
            Toast.makeText(requireContext(), "Please select an end date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (endDate.compareTo(startDate) < 0) {
            Toast.makeText(requireContext(), "End date can't be before start date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (regDeadline == null) {
            Toast.makeText(requireContext(), "Please select a registration deadline", Toast.LENGTH_SHORT).show();
            return;
        }
        if (regDeadline.compareTo(startDate) > 0) {
            Toast.makeText(requireContext(), "Registration deadline can't be after the start date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (venue.isEmpty()) { inputLocation.setError("Required"); return; }
        if (city.isEmpty()) { inputCity.setError("Required"); return; }

        User admin = UserCache.get(requireContext());
        String collegeId = admin != null ? admin.getCollegeId() : null;
        if (collegeId == null || collegeId.isEmpty()) {
            Toast.makeText(requireContext(), "Your admin account isn't linked to a college.", Toast.LENGTH_LONG).show();
            return;
        }

        double price = priceStr.isEmpty() ? 0 : parseDoubleSafe(priceStr);
        int capacity = capacityStr.isEmpty() ? 0 : parseIntSafe(capacityStr);
        String location = venue + ", " + city;
        boolean wantsFeatured = switchFeatured.isChecked();

        Map<String, Object> fields = new HashMap<>();
        fields.put("title", title);
        fields.put("subtitle", subtitle);       // Pushes subtitle to subtitle column
        fields.put("description", description); // Pushes description to description column
        fields.put("category", selectedCategory);
        fields.put("price", price);
        fields.put("capacity", capacity);
        fields.put("date_start", startDate);
        fields.put("date_end", endDate);
        fields.put("registration_deadline", regDeadline);
        fields.put("time_start", startTime);
        fields.put("location", location);
        fields.put("college_id", collegeId);
        fields.put("is_featured", wantsFeatured);

        setLoading(true);

        if (wantsFeatured) {
            checkFeaturedCapThenCreate(collegeId, fields);
        } else {
            uploadImageAndCreate(fields);
        }
    }

    private void checkFeaturedCapThenCreate(String collegeId, Map<String, Object> fields) {
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.getFeaturedEventsByCollege("eq.true", "eq." + collegeId,
                        "date_start.asc", MAX_FEATURED_PER_COLLEGE + 1)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull retrofit2.Response<List<Event>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().size() >= MAX_FEATURED_PER_COLLEGE) {
                            setLoading(false);
                            Toast.makeText(requireContext(),
                                    "Your college already has " + MAX_FEATURED_PER_COLLEGE
                                            + " featured events. Un-feature one before featuring a new event.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        uploadImageAndCreate(fields);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(requireContext(),
                                "Couldn't verify featured-event limit: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadImageAndCreate(Map<String, Object> fields) {
        String eventTitle = inputTitle.getText().toString().trim();
        // Strip everything except alphanumeric characters, hyphens, and underscores
        String sanitizedTitle = eventTitle.replaceAll("[^a-zA-Z0-9-_]", "_");
        if (sanitizedTitle.isEmpty()) {
            sanitizedTitle = "event";
        }

        final String finalTitle = sanitizedTitle;

        new Thread(() -> {
            if (pendingImageUri != null) {
                String imageUrl = uploadImageBlocking(pendingImageUri, finalTitle);
                if (imageUrl == null) {
                    runOnUi(() -> setLoading(false));
                    return;
                }
                fields.put("image_url", imageUrl);
            }
            runOnUi(() -> createEvent(fields));
        }).start();
    }

    private void createEvent(Map<String, Object> fields) {
        final String uploadedImageUrl = (String) fields.get("image_url");
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.createEvent(fields).enqueue(new Callback<List<Event>>() {
            @Override
            public void onResponse(@NonNull Call<List<Event>> call,
                                   @NonNull retrofit2.Response<List<Event>> response) {
                if (!isAdded()) return;
                setLoading(false);
                if (response.isSuccessful()) {
                    EventCache.clear();
                    Toast.makeText(requireContext(), "Event created successfully!",
                            Toast.LENGTH_SHORT).show();
                    resetForm();
                } else {
                    deleteUploadedImage(uploadedImageUrl);
                    Toast.makeText(requireContext(),
                            errorMessageFor(response),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                setLoading(false);
                deleteUploadedImage(uploadedImageUrl);
                Toast.makeText(requireContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String errorMessageFor(retrofit2.Response<List<Event>> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                if (body != null) {
                    String lower = body.toLowerCase(Locale.US);
                    if (lower.contains("featured event")) {
                        return "Your college already has " + MAX_FEATURED_PER_COLLEGE
                                + " featured events. Un-feature one before featuring a new event.";
                    }
                    if (lower.contains("duplicate key") || lower.contains("already exists")) {
                        return "An event with this identical title and date already exists!";
                    }
                }
            }
        } catch (IOException ignored) {}
        return "Could not create event: " + response.code();
    }

    private void resetForm() {
        inputTitle.setText("");
        inputSubtitle.setText("");
        inputDescription.setText("");
        inputLocation.setText("");
        inputCity.setText("");
        inputPrice.setText("");
        inputCapacity.setText("");
        inputCategory.setText("");
        inputStartDate.setText("");
        inputStartTime.setText("");
        inputEndDate.setText("");
        inputRegDeadline.setText("");
        switchFeatured.setChecked(false);
        bannerPreview.setVisibility(View.GONE);
        bannerPreview.setImageDrawable(null);
        selectedCategory = null;
        startDate = endDate = startTime = regDeadline = null;
    }

    private void setInputsEnabled(boolean enabled) {
        inputTitle.setEnabled(enabled);
        inputSubtitle.setEnabled(enabled);
        inputDescription.setEnabled(enabled);
        inputLocation.setEnabled(enabled);
        inputCity.setEnabled(enabled);
        inputPrice.setEnabled(enabled);
        inputCapacity.setEnabled(enabled);
        inputCategory.setEnabled(enabled);
        inputStartDate.setEnabled(enabled);
        inputStartTime.setEnabled(enabled);
        inputEndDate.setEnabled(enabled);
        inputRegDeadline.setEnabled(enabled);
        switchFeatured.setEnabled(enabled);
    }

    private void setLoading(boolean loading) {
        isUploading = loading;
        createProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        createButton.setEnabled(!loading);
        setInputsEnabled(!loading);

        if (loading) {
            createButton.setText("");
            createButton.setIcon(null);
        } else {
            createButton.setText("Create Event");
            createButton.setIconResource(R.drawable.ic_admin_add_event);
        }
    }

    private double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private void runOnUi(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }
}