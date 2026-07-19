package com.evanta.app;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminEventEditActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "extra_event";
    public static final String EXTRA_OPEN_APPROVALS = "extra_open_approvals";

    private static final String CERT_BUCKET = "certificates";
    private static final String[] CATEGORIES =
            {"Tech", "Cultural", "Sports", "Workshop", "Music"};

    private Event event;
    private SupabaseApi api;

    private ImageView banner;
    private TextView category, startDateView, startTimeView, endDateView, regDeadlineView;
    private EditText title, subtitle, description, location, price, capacity;
    private MaterialSwitch switchFeatured;
    private MaterialButton saveButton, deleteButton;
    private ProgressBar progress;

    private String selectedCategory, startDate, endDate, regDeadline, startTime;

    private RegistrationManageAdapter.RegRow pendingRow;
    private int pendingPosition = -1;
    private RegistrationManageAdapter activeManageAdapter;

    private final ActivityResultLauncher<String[]> certificatePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onCertificateFilePicked(uri);
                else clearPendingUpload();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.enableAlwaysDark(this);
        setContentView(R.layout.activity_admin_event_edit);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        api = RetrofitClient.getClient().create(SupabaseApi.class);

        event = getIntent().getParcelableExtra(EXTRA_EVENT);
        if (event == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());
        bindViews();
        bindEvent(event);
        wirePickers();
        wireActions();

        if (getIntent().getBooleanExtra(EXTRA_OPEN_APPROVALS, false)) {
            showManageDialog(RegistrationManageAdapter.MODE_APPROVAL);
        }
    }

    private void bindViews() {
        banner = findViewById(R.id.card_banner);
        category = findViewById(R.id.card_category);
        title = findViewById(R.id.card_title);
        subtitle = findViewById(R.id.card_subtitle);
        description = findViewById(R.id.card_description);
        startDateView = findViewById(R.id.card_start_date);
        startTimeView = findViewById(R.id.card_start_time);
        endDateView = findViewById(R.id.card_end_date);
        regDeadlineView = findViewById(R.id.card_reg_deadline);
        location = findViewById(R.id.card_location);
        price = findViewById(R.id.card_price);
        capacity = findViewById(R.id.card_capacity);
        switchFeatured = findViewById(R.id.card_switch_featured);
        saveButton = findViewById(R.id.card_save_button);
        deleteButton = findViewById(R.id.card_delete_button);
        progress = findViewById(R.id.card_progress);
    }

    private void bindEvent(Event e) {
        if (e.getImageUrl() != null && !e.getImageUrl().trim().isEmpty()) {
            Glide.with(this)
                    .load(e.getImageUrl())
                    .placeholder(R.drawable.launcher)
                    .error(R.drawable.launcher)
                    .centerCrop()
                    .into(banner);
        } else {
            banner.setImageResource(R.drawable.launcher);
        }

        title.setText(e.getTitle());
        subtitle.setText(e.getSubtitle());
        description.setText(e.getDescription());
        location.setText(e.getLocation());
        price.setText(e.getPrice() > 0 ? String.valueOf((int) e.getPrice()) : "");
        capacity.setText(e.getCapacity() > 0 ? String.valueOf(e.getCapacity()) : "");
        switchFeatured.setChecked(e.isFeatured());

        selectedCategory = e.getCategory();
        category.setText(e.getCategory() != null ? e.getCategory() : "");

        startDate = e.getDateStart();
        endDate = e.getDateEnd();
        regDeadline = e.getRegistrationDeadline();
        startTime = e.getTimeStart();
        startDateView.setText(prettyDate(e.getDateStart()));
        endDateView.setText(prettyDate(e.getDateEnd()));
        regDeadlineView.setText(prettyDate(e.getRegistrationDeadline()));
        startTimeView.setText(e.getTimeStart());
    }

    private void wirePickers() {
        category.setOnClickListener(x -> new AlertDialog.Builder(this)
                .setTitle("Select Category")
                .setItems(CATEGORIES, (d, which) -> {
                    selectedCategory = CATEGORIES[which];
                    category.setText(selectedCategory);
                })
                .show());

        startDateView.setOnClickListener(x -> pickDate(startDate, iso -> {
            startDate = iso;
            startDateView.setText(prettyDate(iso));
        }));
        endDateView.setOnClickListener(x -> pickDate(endDate, iso -> {
            endDate = iso;
            endDateView.setText(prettyDate(iso));
        }));
        regDeadlineView.setOnClickListener(x -> pickDate(regDeadline, iso -> {
            regDeadline = iso;
            regDeadlineView.setText(prettyDate(iso));
        }));
        startTimeView.setOnClickListener(x -> pickTime(time -> {
            startTime = time;
            startTimeView.setText(time);
        }));
    }

    private void wireActions() {
        findViewById(R.id.card_btn_approvals).setOnClickListener(x ->
                showManageDialog(RegistrationManageAdapter.MODE_APPROVAL));
        findViewById(R.id.card_btn_certificates).setOnClickListener(x ->
                showManageDialog(RegistrationManageAdapter.MODE_CERTIFICATE));

        saveButton.setOnClickListener(x -> saveEvent());

        deleteButton.setOnClickListener(x -> new AlertDialog.Builder(this)
                .setTitle("Delete event?")
                .setMessage("\"" + event.getTitle() + "\" will be permanently removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> deleteEvent())
                .show());
    }

    private void saveEvent() {
        String t = title.getText().toString().trim();
        String sub = subtitle.getText().toString().trim();
        String desc = description.getText().toString().trim();
        String loc = location.getText().toString().trim();
        String priceStr = price.getText().toString().trim();
        String capacityStr = capacity.getText().toString().trim();

        if (t.isEmpty()) { title.setError("Required"); return; }
        if (selectedCategory == null || selectedCategory.isEmpty()) {
            toast("Please select a category"); return;
        }
        if (startDate == null || startDate.isEmpty()) {
            toast("Please select a start date"); return;
        }
        if (endDate != null && !endDate.isEmpty() && endDate.compareTo(startDate) < 0) {
            toast("End date can't be before start date"); return;
        }
        if (regDeadline != null && !regDeadline.isEmpty()
                && regDeadline.compareTo(startDate) > 0) {
            toast("Registration deadline can't be after the start date"); return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("title", t);
        fields.put("subtitle", sub);
        fields.put("description", desc);
        fields.put("category", selectedCategory);
        fields.put("price", priceStr.isEmpty() ? 0 : parseDouble(priceStr));
        fields.put("capacity", capacityStr.isEmpty() ? 0 : parseInt(capacityStr));
        fields.put("date_start", startDate);
        fields.put("date_end", endDate);
        fields.put("registration_deadline", regDeadline);
        fields.put("time_start", startTime);
        fields.put("location", loc);
        fields.put("is_featured", switchFeatured.isChecked());

        setBusy(true);
        api.updateEvent("eq." + event.getId(), fields).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (isFinishing()) return;
                setBusy(false);
                if (response.isSuccessful()) {
                    applyToModel(event, fields);
                    EventCache.clear();
                    toast("Saved");
                    finish();
                } else {
                    toast("Save failed (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                setBusy(false);
                toast("Save failed");
            }
        });
    }

    private void applyToModel(Event e, Map<String, Object> f) {
        e.setTitle((String) f.get("title"));
        e.setSubtitle((String) f.get("subtitle"));
        e.setDescription((String) f.get("description"));
        e.setCategory((String) f.get("category"));
        e.setPrice(((Number) f.get("price")).doubleValue());
        e.setCapacity(((Number) f.get("capacity")).intValue());
        e.setDateStart((String) f.get("date_start"));
        e.setDateEnd((String) f.get("date_end"));
        e.setRegistrationDeadline((String) f.get("registration_deadline"));
        e.setTimeStart((String) f.get("time_start"));
        e.setLocation((String) f.get("location"));
        e.setFeatured((Boolean) f.get("is_featured"));
    }

    private void deleteEvent() {
        setBusy(true);
        api.deleteEvent("eq." + event.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (isFinishing()) return;
                setBusy(false);
                if (response.isSuccessful()) {
                    EventCache.clear();
                    toast("Deleted");
                    finish();
                } else {
                    toast("Delete failed (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                setBusy(false);
                toast("Delete failed");
            }
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
    }

    private void showManageDialog(int mode) {
        View content = LayoutInflater.from(this)
                .inflate(R.layout.dialog_manage_list, null, false);

        TextView dTitle = content.findViewById(R.id.manage_title);
        TextView dSubtitle = content.findViewById(R.id.manage_subtitle);
        RecyclerView list = content.findViewById(R.id.manage_recycler);
        ProgressBar dialogLoader = content.findViewById(R.id.manage_loader);
        TextView empty = content.findViewById(R.id.manage_empty);

        boolean approval = mode == RegistrationManageAdapter.MODE_APPROVAL;
        dTitle.setText(approval ? "Enrollment Requests" : "Certificates");
        dSubtitle.setText(approval
                ? "Approve or reject students who registered."
                : "Upload a certificate for approved students.");

        List<RegistrationManageAdapter.RegRow> rows = new ArrayList<>();
        RegistrationManageAdapter listAdapter =
                new RegistrationManageAdapter(rows, mode, new RegistrationManageAdapter.Listener() {
                    @Override
                    public void onApprove(RegistrationManageAdapter.RegRow row, int position) {
                        setStatus(row, position, Registration.STATUS_APPROVED);
                    }

                    @Override
                    public void onReject(RegistrationManageAdapter.RegRow row, int position) {
                        setStatus(row, position, Registration.STATUS_REJECTED);
                    }

                    @Override
                    public void onUpload(RegistrationManageAdapter.RegRow row, int position) {
                        startCertificateUpload(row, position);
                    }
                });

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(listAdapter);
        activeManageAdapter = listAdapter;

        // ── KEY CHANGE: pass TransparentBottomSheet theme ──
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.TransparentBottomSheet);
        dialog.setContentView(content);
        content.findViewById(R.id.manage_close).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (activeManageAdapter == listAdapter) activeManageAdapter = null;
        });

        dialog.show();

        View sheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            window.setNavigationBarColor(0xFF15161C);
        }

        loadRegistrations(mode, rows, listAdapter, dialogLoader, empty);
    }

    private void loadRegistrations(int mode,
                                   List<RegistrationManageAdapter.RegRow> rows,
                                   RegistrationManageAdapter listAdapter,
                                   ProgressBar dialogLoader, TextView empty) {
        dialogLoader.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        api.getRegistrationsByEventId("eq." + event.getId(), "registered_at.asc")
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Registration>> call,
                                           @NonNull Response<List<Registration>> response) {
                        if (isFinishing()) return;

                        List<Registration> all = response.isSuccessful() && response.body() != null
                                ? response.body() : new ArrayList<>();

                        List<Registration> filtered = new ArrayList<>();
                        for (Registration r : all) {
                            if (mode == RegistrationManageAdapter.MODE_CERTIFICATE) {
                                if (r.isApproved()) filtered.add(r);
                            } else {
                                filtered.add(r);
                            }
                        }

                        if (filtered.isEmpty()) {
                            dialogLoader.setVisibility(View.GONE);
                            empty.setText(mode == RegistrationManageAdapter.MODE_CERTIFICATE
                                    ? "No approved students yet."
                                    : "No enrollment requests yet.");
                            empty.setVisibility(View.VISIBLE);
                            return;
                        }

                        resolveUsers(filtered, rows, listAdapter, dialogLoader, empty);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Registration>> call,
                                          @NonNull Throwable t) {
                        if (isFinishing()) return;
                        dialogLoader.setVisibility(View.GONE);
                        empty.setText("Couldn't load. Check your connection.");
                        empty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void resolveUsers(List<Registration> registrations,
                              List<RegistrationManageAdapter.RegRow> rows,
                              RegistrationManageAdapter listAdapter,
                              ProgressBar dialogLoader, TextView empty) {
        StringBuilder ids = new StringBuilder("in.(");
        for (int i = 0; i < registrations.size(); i++) {
            if (i > 0) ids.append(",");
            ids.append(registrations.get(i).getUserUid());
        }
        ids.append(")");

        api.getUsersByUids(ids.toString()).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call,
                                   @NonNull Response<List<User>> response) {
                if (isFinishing()) return;

                Map<String, User> byUid = new HashMap<>();
                if (response.isSuccessful() && response.body() != null) {
                    for (User u : response.body()) {
                        if (u.getUid() != null) byUid.put(u.getUid(), u);
                    }
                }

                rows.clear();
                for (Registration r : registrations) {
                    rows.add(new RegistrationManageAdapter.RegRow(r, byUid.get(r.getUserUid())));
                }
                listAdapter.notifyDataSetChanged();
                dialogLoader.setVisibility(View.GONE);
                empty.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                rows.clear();
                for (Registration r : registrations) {
                    rows.add(new RegistrationManageAdapter.RegRow(r, null));
                }
                listAdapter.notifyDataSetChanged();
                dialogLoader.setVisibility(View.GONE);
            }
        });
    }

    private void setStatus(RegistrationManageAdapter.RegRow row, int position, String status) {
        final RegistrationManageAdapter listAdapter = activeManageAdapter;
        if (listAdapter != null) listAdapter.setRowBusy(position, true);

        Map<String, Object> fields = new HashMap<>();
        fields.put("status", status);

        api.updateRegistrationStatus("eq." + row.registration.getId(), fields)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call,
                                           @NonNull Response<Void> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful()) {
                            row.registration.setStatus(status);
                            if (Registration.STATUS_APPROVED.equals(status)) {
                                notifyStudent(row.registration.getUserUid(), event.getId(),
                                        "Enrollment Approved",
                                        "You're approved for \"" + event.getTitle()
                                                + "\". See you there!",
                                        Notification.TYPE_GENERAL);
                            } else if (Registration.STATUS_REJECTED.equals(status)) {
                                boolean canRetry =
                                        row.registration.getAttempts() < Registration.MAX_ATTEMPTS;
                                String body = canRetry
                                        ? "Your enrollment for \"" + event.getTitle()
                                          + "\" was not approved. You can reapply."
                                        : "Your enrollment for \"" + event.getTitle()
                                          + "\" was not approved.";
                                notifyStudent(row.registration.getUserUid(), event.getId(),
                                        "Enrollment Not Approved", body,
                                        Notification.TYPE_GENERAL);
                            }
                        } else {
                            toast("Update failed (" + response.code() + ")");
                        }
                        if (listAdapter != null) listAdapter.setRowBusy(position, false);
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        if (isFinishing()) return;
                        if (listAdapter != null) listAdapter.setRowBusy(position, false);
                        toast("Update failed");
                    }
                });
    }

    private void startCertificateUpload(RegistrationManageAdapter.RegRow row, int position) {
        pendingRow = row;
        pendingPosition = position;
        certificatePicker.launch(new String[]{"application/pdf", "image/*"});
    }

    private void onCertificateFilePicked(Uri uri) {
        if (pendingRow == null || activeManageAdapter == null) {
            clearPendingUpload();
            return;
        }

        final RegistrationManageAdapter listAdapter = activeManageAdapter;
        final RegistrationManageAdapter.RegRow row = pendingRow;
        final int position = pendingPosition;
        final String previousUrl = row.registration.getCertificateUrl();

        listAdapter.setRowBusy(position, true);

        new Thread(() -> {
            String url = uploadCertificateBlocking(uri, event.getId(),
                    row.registration.getUserUid());
            if (url != null && previousUrl != null && !previousUrl.equals(url)) {
                deleteCertificateBlocking(previousUrl);
            }
            runOnUi(() -> {
                if (isFinishing()) return;
                if (url == null) {
                    listAdapter.setRowBusy(position, false);
                    return;
                }
                patchCertificateUrl(row, position, url, listAdapter);
            });
        }).start();

        clearPendingUpload();
    }

    private void patchCertificateUrl(RegistrationManageAdapter.RegRow row, int position,
                                     String url, RegistrationManageAdapter listAdapter) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("certificate_url", url);

        api.updateRegistrationStatus("eq." + row.registration.getId(), fields)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call,
                                           @NonNull Response<Void> response) {
                        if (isFinishing()) return;
                        listAdapter.setRowBusy(position, false);
                        if (response.isSuccessful()) {
                            row.registration.setCertificateUrl(url);
                            listAdapter.notifyRowChanged(position);
                            notifyStudent(row.registration.getUserUid(), event.getId(),
                                    "Certificate Ready",
                                    "Your certificate for \"" + event.getTitle()
                                            + "\" is now available.",
                                    Notification.TYPE_CERTIFICATE);
                            toast("Certificate sent");
                        } else {
                            toast("Couldn't save certificate (" + response.code() + ")");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        if (isFinishing()) return;
                        listAdapter.setRowBusy(position, false);
                        toast("Couldn't save certificate");
                    }
                });
    }

    private String uploadCertificateBlocking(Uri uri, String eventId, String userUid) {
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            String ext = extensionFor(mime);

            byte[] bytes;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
                bytes = buffer.toByteArray();
            }

            if (bytes.length == 0) return null;

            String fileName = eventId + "_" + userUid + "_" + UUID.randomUUID() + ext;
            String uploadUrl = SupabaseConfig.BASE_URL
                    + "storage/v1/object/" + CERT_BUCKET + "/" + fileName;

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            RequestBody body = RequestBody.create(bytes, MediaType.parse(mime));
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .put(body)
                    .addHeader("apikey", SupabaseConfig.API_KEY)
                    .addHeader("Authorization", "Bearer " + AuthTokens.bearer())
                    .addHeader("x-upsert", "true")
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return SupabaseConfig.BASE_URL
                            + "storage/v1/object/public/" + CERT_BUCKET + "/" + fileName;
                }
                final int code = response.code();
                final String errBody = response.body() != null ? response.body().string() : "";
                android.util.Log.e("AdminEventEdit",
                        "Certificate upload failed " + code + ": " + errBody);
                runOnUi(() -> toast(certUploadError(code, errBody)));
                return null;
            }
        } catch (IOException e) {
            android.util.Log.e("AdminEventEdit", "Certificate upload error", e);
            runOnUi(() -> toast("Upload network error"));
            return null;
        }
    }

    private void deleteCertificateBlocking(String publicUrl) {
        try {
            String marker = "/object/public/" + CERT_BUCKET + "/";
            int idx = publicUrl.indexOf(marker);
            if (idx < 0) return;
            String path = publicUrl.substring(idx + marker.length());
            if (path.isEmpty()) return;

            String deleteUrl = SupabaseConfig.BASE_URL
                    + "storage/v1/object/" + CERT_BUCKET + "/" + path;

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(deleteUrl)
                    .delete()
                    .addHeader("apikey", SupabaseConfig.API_KEY)
                    .addHeader("Authorization", "Bearer " + AuthTokens.bearer())
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    android.util.Log.w("AdminEventEdit",
                            "Old certificate delete failed " + response.code());
                }
            }
        } catch (Exception e) {
            android.util.Log.w("AdminEventEdit", "Old certificate delete error", e);
        }
    }

    private void notifyStudent(String userUid, String eventId, String titleText,
                               String bodyText, String type) {
        if (userUid == null) return;
        Map<String, Object> fields = new HashMap<>();
        fields.put("user_uid", userUid);
        fields.put("title", titleText);
        fields.put("body", bodyText);
        fields.put("type", type);
        fields.put("event_id", eventId);
        fields.put("is_read", false);

        api.pushNotification(fields).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    android.util.Log.w("AdminEventEdit",
                            "Notification push failed: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                android.util.Log.w("AdminEventEdit", "Notification push error", t);
            }
        });
    }

    private interface DateResult { void onPicked(String iso); }
    private interface TimeResult { void onPicked(String amPm); }

    private void pickDate(String currentIso, DateResult cb) {
        Calendar c = Calendar.getInstance();
        if (currentIso != null && currentIso.length() >= 10) {
            try {
                c.set(Integer.parseInt(currentIso.substring(0, 4)),
                        Integer.parseInt(currentIso.substring(5, 7)) - 1,
                        Integer.parseInt(currentIso.substring(8, 10)));
            } catch (Exception ignored) { }
        }
        new DatePickerDialog(this,
                (view, year, month, day) -> cb.onPicked(
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void pickTime(TimeResult cb) {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(this,
                (view, hour, minute) -> {
                    int h12 = hour % 12 == 0 ? 12 : hour % 12;
                    String ampm = hour < 12 ? "AM" : "PM";
                    cb.onPicked(String.format(Locale.US, "%d:%02d %s", h12, minute, ampm));
                },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
    }

    private String prettyDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        try {
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            int y = Integer.parseInt(iso.substring(0, 4));
            int m = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            return String.format(Locale.US, "%02d %s %04d", d, months[m - 1], y);
        } catch (Exception e) {
            return iso;
        }
    }

    private String extensionFor(String mime) {
        String m = mime.toLowerCase(Locale.US);
        if (m.contains("pdf")) return ".pdf";
        if (m.contains("png")) return ".png";
        if (m.contains("webp")) return ".webp";
        if (m.contains("jpeg") || m.contains("jpg")) return ".jpg";
        return ".bin";
    }

    private String certUploadError(int code, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.US);
        if (code == 404 || lower.contains("bucket not found")) return "Certificates bucket missing";
        if (code == 401 || code == 403 || lower.contains("policy")
                || lower.contains("row-level security")) return "Upload not allowed";
        if (code == 413 || lower.contains("too large")) return "File too large";
        return "Upload failed (" + code + ")";
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private void clearPendingUpload() {
        pendingRow = null;
        pendingPosition = -1;
    }

    private void runOnUi(Runnable r) { runOnUiThread(r); }

    private void toast(String msg) {
        if (!isFinishing()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}