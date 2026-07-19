package com.evanta.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "extra_event";

    private static final int COLOR_REGISTERED = 0xFF27AE60;
    private static final int COLOR_DEFAULT    = 0xFF7C4DFF;
    private static final int COLOR_DISABLED   = 0xFF555B66;
    private static final int COLOR_PENDING    = 0xFFF2A93B;
    private static final int COLOR_REJECTED   = 0xFFE53935;

    private MaterialButton btnRegister;
    private MaterialButton btnDownloadCertificate;
    private Event currentEvent;
    private TextView seatsText;
    private TextView deadlineText;
    private View seatsRow;
    private View deadlineRow;
    private View collegeRow;
    private TextView collegeText;
    private int registeredCount = -1;
    private boolean alreadyRegistered = false;
    private Registration currentRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_event_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());

        btnRegister = findViewById(R.id.btnRegister);
        btnDownloadCertificate = findViewById(R.id.btnDownloadCertificate);
        seatsRow = findViewById(R.id.detail_seats_row);
        deadlineRow = findViewById(R.id.detail_deadline_row);
        collegeRow = findViewById(R.id.detail_college_row);
        collegeText = findViewById(R.id.detail_college);
        seatsText = findViewById(R.id.detail_seats);
        deadlineText = findViewById(R.id.detail_deadline);

        currentEvent = getIntent().getParcelableExtra(EXTRA_EVENT);
        if (currentEvent != null) {
            bindEvent(currentEvent);
            loadRegistrationCount(currentEvent);
            checkIfAlreadyRegistered(currentEvent);
        }
    }

    private void bindEvent(Event event) {
        ImageView heroImage         = findViewById(R.id.hero_image);
        TextView categoryTag        = findViewById(R.id.category_tag);
        TextView title              = findViewById(R.id.event_title);
        TextView subtitle           = findViewById(R.id.event_subtitle);
        TextView description        = findViewById(R.id.event_description);
        TextView dateText           = findViewById(R.id.detail_date);
        TextView locationText       = findViewById(R.id.detail_location);

        categoryTag.setText(event.getCategory());
        title.setText(event.getTitle());
        subtitle.setText(event.getSubtitle());

        description.setText(
                TextUtils.isEmpty(event.getDescription())
                        ? "No description available for this event yet."
                        : event.getDescription());

        dateText.setText(formatDateRange(event.getDateStart(), event.getDateEnd())
                + (event.getTimeStart() != null && !event.getTimeStart().isEmpty()
                ? " • " + event.getTimeStart() : ""));

        locationText.setText(event.getLocation());

        bindCollege(event);

        String priceLabel = event.getPrice() <= 0 ? "Free" : "₹" + (int) event.getPrice();
        btnRegister.setText("Enroll Now • " + priceLabel);
        bindDeadline(event);

        if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
            Glide.with(this).load(event.getImageUrl()).centerCrop().into(heroImage);
        } else {
            heroImage.setImageResource(R.drawable.launcher);
        }
    }

    private void bindCollege(Event event) {
        String collegeId = event.getCollegeId();
        if (collegeId == null || collegeId.trim().isEmpty()) {
            collegeRow.setVisibility(View.GONE);
            return;
        }

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.getCollegeById("eq." + collegeId).enqueue(new Callback<List<College>>() {
            @Override
            public void onResponse(Call<List<College>> call, Response<List<College>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    collegeText.setText(response.body().get(0).getName());
                    collegeRow.setVisibility(View.VISIBLE);
                } else {
                    collegeRow.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<List<College>> call, Throwable t) {
                if (!isAdded()) return;
                collegeRow.setVisibility(View.GONE);
            }
        });
    }

    private void checkIfAlreadyRegistered(Event event) {
        User user = UserCache.get(this);
        if (user == null || event.getId() == null) return;

        btnRegister.setEnabled(false);

        RegistrationRepository registrationRepository = new RegistrationRepository();
        registrationRepository.checkRegistration(user.getUid(), event.getId())
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(Call<List<Registration>> call,
                                           Response<List<Registration>> response) {
                        if (!isAdded()) return;

                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {
                            alreadyRegistered = true;
                            Registration reg = response.body().get(0);
                            currentRegistration = reg;
                            setButtonForRegistration(reg.getStatus(), reg);
                            updateCertificateButton(reg);
                        } else {
                            alreadyRegistered = false;
                            setButtonDefault(event);
                            btnDownloadCertificate.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Registration>> call, Throwable t) {
                        if (!isAdded()) return;
                        alreadyRegistered = false;
                        setButtonDefault(event);
                        btnDownloadCertificate.setVisibility(View.GONE);
                    }
                });
    }

    private void registerForEvent(Event event) {
        User user = UserCache.get(this);
        if (user == null) {
            showSnackbar("Please log in to register.", false);
            return;
        }

        setButtonLoading();

        RegistrationRepository registrationRepository = new RegistrationRepository();

        registrationRepository.registerForEvent(user.getUid(), event.getId()).enqueue(new Callback<List<Registration>>() {
            @Override
            public void onResponse(Call<List<Registration>> call,
                                   Response<List<Registration>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    alreadyRegistered = true;
                    PrefetchCache.clearMyEventsData();

                    if (registeredCount >= 0) {
                        registeredCount++;
                        bindSeats(event);
                    }
                    setButtonForStatus(Registration.STATUS_PENDING);
                    showSnackbar("🎉 You're enrolled in " + event.getTitle() + "! Awaiting admin approval.", true);
                    if (response.body() != null && !response.body().isEmpty()) {
                        updateCertificateButton(response.body().get(0));
                    }
                } else if (response.code() == 409) {
                    alreadyRegistered = true;
                    setButtonForStatus(Registration.STATUS_PENDING);
                    showSnackbar("You're already enrolled in this event.", true);
                } else {
                    String errorBody = "";
                    try { errorBody = response.errorBody() != null ? response.errorBody().string() : "null"; }
                    catch (Exception ignored) {}
                    Log.e("EVANTA_REG", "Registration failed: HTTP " + response.code() + " — " + errorBody);
                    setButtonDefault(event);
                    showSnackbar("Registration failed (HTTP " + response.code() + "). Check Logcat.", false);
                }
            }

            @Override
            public void onFailure(Call<List<Registration>> call, Throwable t) {
                if (!isAdded()) return;
                setButtonDefault(event);
                showSnackbar("Network error. Check your connection.", false);
            }
        });
    }

    private void setButtonDefault(Event event) {
        String priceLabel = event.getPrice() <= 0 ? "Free" : "₹" + (int) event.getPrice();
        if (isRegistrationClosed(event)) {
            btnRegister.setText("Registration Closed");
            btnRegister.setIcon(null);
            setButtonColor(COLOR_DISABLED);
            btnRegister.setEnabled(false);
            btnRegister.setOnClickListener(null);
            return;
        }
        if (isEventFull(event)) {
            btnRegister.setText("Event Full");
            btnRegister.setIcon(null);
            setButtonColor(COLOR_DISABLED);
            btnRegister.setEnabled(false);
            btnRegister.setOnClickListener(null);
            return;
        }
        btnRegister.setText("Enroll Now • " + priceLabel);
        btnRegister.setIcon(getDrawable(R.drawable.ic_ticket));
        setButtonColor(COLOR_DEFAULT);
        btnRegister.setEnabled(true);
        btnRegister.setOnClickListener(v -> registerForEvent(event));
    }

    private void setButtonLoading() {
        btnRegister.setText("Enrolling…");
        btnRegister.setIcon(null);
        btnRegister.setEnabled(false);
    }

    private void setButtonRegistered() {
        setButtonForStatus(null);
    }

    /** Back-compat: status-only entry point (fresh enroll / pending). */
    private void setButtonForStatus(String status) {
        setButtonForRegistration(status, null);
    }

    /**
     * Reflects the registration's approval status on the enroll button.
     * pending  → amber "Pending Approval"
     * approved → green "Enrolled ✓"
     * rejected + attempts left → amber, tappable "Reapply (n left)"
     * rejected + no attempts   → red, locked "Not Approved"
     * null/unknown status falls back to a neutral "Already Enrolled ✓".
     */
    private void setButtonForRegistration(String status, Registration reg) {
        btnRegister.setIcon(null);
        btnRegister.setEnabled(false);
        btnRegister.setOnClickListener(null);

        if (Registration.STATUS_APPROVED.equals(status)) {
            btnRegister.setText("Enrolled ✓");
            setButtonColor(COLOR_REGISTERED);
        } else if (Registration.STATUS_REJECTED.equals(status)) {
            int attempts = reg != null ? reg.getAttempts() : Registration.MAX_ATTEMPTS;
            int reappliesLeft = Registration.MAX_ATTEMPTS - attempts;
            if (reg != null && reappliesLeft > 0) {
                btnRegister.setText("Reapply (" + reappliesLeft + " left)");
                setButtonColor(COLOR_PENDING);
                btnRegister.setEnabled(true);
                btnRegister.setOnClickListener(v -> reapply(reg));
            } else {
                btnRegister.setText("Not Approved");
                setButtonColor(COLOR_REJECTED);
            }
        } else if (Registration.STATUS_PENDING.equals(status)) {
            btnRegister.setText("Pending Approval");
            setButtonColor(COLOR_PENDING);
        } else {
            btnRegister.setText("Already Enrolled ✓");
            setButtonColor(COLOR_REGISTERED);
        }
    }

    private void reapply(Registration reg) {
        if (reg == null || reg.getId() == null) return;
        btnRegister.setEnabled(false);
        btnRegister.setOnClickListener(null);
        btnRegister.setText("Reapplying…");

        int newAttempts = reg.getAttempts() + 1;
        new RegistrationRepository().reapply(reg.getId(), newAttempts)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            reg.setAttempts(newAttempts);
                            reg.setStatus(Registration.STATUS_PENDING);
                            currentRegistration = reg;
                            PrefetchCache.clearMyEventsData();
                            setButtonForStatus(Registration.STATUS_PENDING);
                            showSnackbar("Reapplied — awaiting admin approval.", true);
                        } else {
                            setButtonForRegistration(Registration.STATUS_REJECTED, reg);
                            showSnackbar("Reapply failed (HTTP " + response.code() + ").", false);
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (!isAdded()) return;
                        setButtonForRegistration(Registration.STATUS_REJECTED, reg);
                        showSnackbar("Network error. Try again.", false);
                    }
                });
    }

    private void loadRegistrationCount(Event event) {
        if (event.getId() == null) {
            bindSeats(event);
            return;
        }

        new RegistrationRepository().getRegistrationsForEvent(event.getId())
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(Call<List<Registration>> call,
                                           Response<List<Registration>> response) {
                        if (!isAdded()) return;

                        registeredCount = response.isSuccessful() && response.body() != null
                                ? response.body().size()
                                : -1;
                        bindSeats(event);
                        if (!alreadyRegistered) {
                            setButtonDefault(event);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Registration>> call, Throwable t) {
                        if (!isAdded()) return;
                        registeredCount = -1;
                        bindSeats(event);
                    }
                });
    }

    private void bindSeats(Event event) {
        if (event.getCapacity() <= 0) {
            seatsRow.setVisibility(View.GONE);
            return;
        }

        seatsRow.setVisibility(View.VISIBLE);
        if (registeredCount >= 0) {
            int left = Math.max(event.getCapacity() - registeredCount, 0);
            seatsText.setText(left + " seats left • " + registeredCount + "/" + event.getCapacity() + " enrolled");
        } else {
            seatsText.setText(event.getCapacity() + " total seats");
        }
    }

    private void bindDeadline(Event event) {
        if (event.getRegistrationDeadline() == null || event.getRegistrationDeadline().trim().isEmpty()) {
            deadlineRow.setVisibility(View.GONE);
            return;
        }

        deadlineRow.setVisibility(View.VISIBLE);
        String label = isRegistrationClosed(event) ? "Registration closed on " : "Register by ";
        deadlineText.setText(label + formatDateRange(event.getRegistrationDeadline(), null));
    }

    private boolean isEventFull(Event event) {
        return event.getCapacity() > 0 && registeredCount >= event.getCapacity();
    }

    private boolean isRegistrationClosed(Event event) {
        String deadline = event.getRegistrationDeadline();
        if (deadline == null || deadline.trim().isEmpty()) return false;

        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date deadlineDate = input.parse(deadline);
            Date today = input.parse(input.format(new Date()));
            return deadlineDate != null && deadlineDate.before(today);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Records that this event's certificate has been downloaded, so the
     * certificate notification is permanently dismissed. Uses the same
     * SharedPreferences store (notif_read / dismissed_keys) that
     * NotificationCenterActivity reads when deciding what to show.
     */
    private void markCertificateDownloaded(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) return;
        android.content.SharedPreferences prefs =
                getSharedPreferences("notif_read", MODE_PRIVATE);
        java.util.Set<String> keys = new java.util.HashSet<>(
                prefs.getStringSet("dismissed_keys", new java.util.HashSet<>()));
        keys.add("certificate_" + eventId);
        prefs.edit().putStringSet("dismissed_keys", keys).apply();
    }

    private void updateCertificateButton(Registration registration) {
        btnDownloadCertificate.setVisibility(View.VISIBLE);

        String certUrl = registration.getCertificateUrl();
        if (certUrl == null || certUrl.trim().isEmpty()) {
            btnDownloadCertificate.setText("Certificate Not Issued Yet");
            btnDownloadCertificate.setEnabled(false);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_DISABLED);
            bg.setCornerRadius(56f);
            btnDownloadCertificate.setBackground(bg);
            btnDownloadCertificate.setOnClickListener(null);
        } else {
            btnDownloadCertificate.setText("Download Certificate");
            btnDownloadCertificate.setEnabled(true);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_DEFAULT);
            bg.setCornerRadius(56f);
            btnDownloadCertificate.setBackground(bg);

            btnDownloadCertificate.setOnClickListener(v -> {
                markCertificateDownloaded(registration.getEventId());
                android.content.Intent browserIntent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(certUrl));
                startActivity(browserIntent);
            });
        }
    }

    private void setButtonColor(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(56f);
        btnRegister.setBackground(bg);
    }

    private void showSnackbar(String message, boolean success) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.main), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(success ? COLOR_REGISTERED : 0xFFE53935);
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    private boolean isAdded() {
        return !isFinishing() && !isDestroyed();
    }

    private String formatDateRange(String start, String end) {
        try {
            SimpleDateFormat input   = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat dayOnly = new SimpleDateFormat("d", Locale.getDefault());
            SimpleDateFormat full    = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());

            Date startDate = input.parse(start);

            if (end == null || end.isEmpty() || end.equals(start)) {
                return full.format(startDate);
            }

            Date endDate = input.parse(end);
            return dayOnly.format(startDate) + " - " + full.format(endDate);

        } catch (Exception e) {
            return start != null ? start : "";
        }
    }
}