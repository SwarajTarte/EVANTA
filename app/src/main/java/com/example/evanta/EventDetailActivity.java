package com.example.evanta;

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

    // Button state colours
    private static final int COLOR_REGISTERED = 0xFF27AE60;  // green
    private static final int COLOR_DEFAULT    = 0xFF7C4DFF;  // purple
    private static final int COLOR_DISABLED   = 0xFF555B66;  // grey

    private MaterialButton btnRegister;
    private MaterialButton btnDownloadCertificate;
    private Event currentEvent;
    private TextView seatsText;
    private TextView deadlineText;
    private View seatsRow;
    private View deadlineRow;
    private int registeredCount = -1;
    private boolean alreadyRegistered = false;

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
        seatsText = findViewById(R.id.detail_seats);
        deadlineText = findViewById(R.id.detail_deadline);

        currentEvent = getIntent().getParcelableExtra(EXTRA_EVENT);
        if (currentEvent != null) {
            bindEvent(currentEvent);
            loadRegistrationCount(currentEvent);
            checkIfAlreadyRegistered(currentEvent);
        }
    }

    // -------------------------------------------------------------------------
    //  Bind event data to the UI
    // -------------------------------------------------------------------------

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

        String priceLabel = event.getPrice() <= 0 ? "Free" : "₹" + (int) event.getPrice();

        // Set default button label (will be overridden by checkIfAlreadyRegistered if needed)
        btnRegister.setText("Enroll Now • " + priceLabel);
        bindDeadline(event);

        if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
            Glide.with(this).load(event.getImageUrl()).centerCrop().into(heroImage);
        } else {
            heroImage.setImageResource(R.drawable.launcher);
        }
    }

    // -------------------------------------------------------------------------
    //  Step 1 — Check if user is already registered (runs on screen open)
    // -------------------------------------------------------------------------

    private void checkIfAlreadyRegistered(Event event) {
        User user = UserCache.get(this);
        if (user == null || event.getId() == null) return;

        // Disable button while we check so the user can't tap during network call
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
                            // Already registered
                            alreadyRegistered = true;
                            setButtonRegistered();
                            // Update certificate button with fetched registration
                            updateCertificateButton(response.body().get(0));
                        } else {
                            // Not yet registered — enable the button
                            alreadyRegistered = false;
                            setButtonDefault(event);
                            btnDownloadCertificate.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Registration>> call, Throwable t) {
                        if (!isAdded()) return;
                        // On network failure still let them try to register
                        alreadyRegistered = false;
                        setButtonDefault(event);
                        btnDownloadCertificate.setVisibility(View.GONE);
                    }
                });
    }

    // -------------------------------------------------------------------------
    //  Step 2 — Perform registration when user taps "Enroll Now"
    // -------------------------------------------------------------------------

    private void registerForEvent(Event event) {
        User user = UserCache.get(this);
        if (user == null) {
            showSnackbar("Please log in to register.", false);
            return;
        }

        // Loading state — prevent double-taps
        setButtonLoading();

        RegistrationRepository registrationRepository = new RegistrationRepository();

        registrationRepository.registerForEvent(user.getUid(), event.getId()).enqueue(new Callback<List<Registration>>() {
            @Override
            public void onResponse(Call<List<Registration>> call,
                                   Response<List<Registration>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    alreadyRegistered = true;
                    if (registeredCount >= 0) {
                        registeredCount++;
                        bindSeats(event);
                    }
                    setButtonRegistered();
                    showSnackbar("🎉 You're enrolled in " + event.getTitle() + "!", true);
                    if (response.body() != null && !response.body().isEmpty()) {
                        updateCertificateButton(response.body().get(0));
                    }
                } else if (response.code() == 409) {
                    // Duplicate — treat as already enrolled
                    alreadyRegistered = true;
                    setButtonRegistered();
                    showSnackbar("You're already enrolled in this event.", true);
                } else {
                    // Log full error so we can diagnose in Logcat
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

    // -------------------------------------------------------------------------
    //  Button state helpers
    // -------------------------------------------------------------------------

    /** Active purple "Enroll Now • Free / ₹XXX" state. */
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

    /** Spinning/disabled "Enrolling…" state shown during the network call. */
    private void setButtonLoading() {
        btnRegister.setText("Enrolling…");
        btnRegister.setIcon(null);
        btnRegister.setEnabled(false);
    }

    /** Green "Already Enrolled ✓" state shown after a successful registration. */
    private void setButtonRegistered() {
        btnRegister.setText("Already Enrolled ✓");
        btnRegister.setIcon(null);
        setButtonColor(COLOR_REGISTERED);
        btnRegister.setEnabled(false);
        btnRegister.setOnClickListener(null);
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

    /** Manages the visibility and click state of the Certificate Download button */
    private void updateCertificateButton(Registration registration) {
        btnDownloadCertificate.setVisibility(View.VISIBLE);

        String certUrl = registration.getCertificateUrl();
        if (certUrl == null || certUrl.trim().isEmpty()) {
            // Certificate not issued yet
            btnDownloadCertificate.setText("Certificate Not Issued Yet");
            btnDownloadCertificate.setEnabled(false);

            // Apply grey color background
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_DISABLED);
            bg.setCornerRadius(56f);
            btnDownloadCertificate.setBackground(bg);
            btnDownloadCertificate.setOnClickListener(null);
        } else {
            // Certificate is issued! Make it active
            btnDownloadCertificate.setText("Download Certificate");
            btnDownloadCertificate.setEnabled(true);

            // Apply active purple color background
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_DEFAULT);
            bg.setCornerRadius(56f);
            btnDownloadCertificate.setBackground(bg);

            // Open certificate URL on tap
            btnDownloadCertificate.setOnClickListener(v -> {
                android.content.Intent browserIntent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(certUrl));
                startActivity(browserIntent);
            });
        }
    }

    /**
     * Changes the solid background colour of the button. We set a simple
     * GradientDrawable so we don't conflict with the btn_gradient drawable
     * used for the default state.
     */
    private void setButtonColor(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(56f); // matches btn_gradient rounded corners
        btnRegister.setBackground(bg);
    }

    // -------------------------------------------------------------------------
    //  Snackbar helper
    // -------------------------------------------------------------------------

    private void showSnackbar(String message, boolean success) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.main), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(success ? COLOR_REGISTERED : 0xFFE53935);
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    // -------------------------------------------------------------------------
    //  Guard: check fragment-like attachment (activity not finishing)
    // -------------------------------------------------------------------------

    private boolean isAdded() {
        return !isFinishing() && !isDestroyed();
    }

    // -------------------------------------------------------------------------
    //  Date formatting
    // -------------------------------------------------------------------------

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
