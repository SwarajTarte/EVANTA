package com.example.evanta;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Renders each of the admin's events as an always-editable card. Every field is
 * a live input; "Save Changes" PATCHes the row, "Delete" removes it (with a
 * confirm dialog). Category is picked from a fixed list; dates/time use pickers.
 */
public class AdminEventCardAdapter extends RecyclerView.Adapter<AdminEventCardAdapter.CardHolder> {

    private static final String[] CATEGORIES =
            {"Tech", "Cultural", "Sports", "Workshop", "Music"};

    public interface OnEventDeleted {
        void onDeleted(int position);
    }

    private final List<Event> events;
    private final OnEventDeleted deleteCallback;
    private final SupabaseApi api;

    public AdminEventCardAdapter(List<Event> events, OnEventDeleted deleteCallback) {
        this.events = events;
        this.deleteCallback = deleteCallback;
        this.api = RetrofitClient.getClient().create(SupabaseApi.class);
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_event_card, parent, false);
        return new CardHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder h, int position) {
        Event event = events.get(h.getBindingAdapterPosition() == RecyclerView.NO_POSITION
                ? position : h.getBindingAdapterPosition());

        // Banner
        if (event.getImageUrl() != null && !event.getImageUrl().trim().isEmpty()) {
            Glide.with(h.banner.getContext())
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.launcher)
                    .error(R.drawable.launcher)
                    .centerCrop()
                    .into(h.banner);
        } else {
            h.banner.setImageResource(R.drawable.launcher);
        }

        // Text fields
        h.title.setText(event.getTitle());
        h.subtitle.setText(event.getSubtitle());
        h.description.setText(event.getDescription());
        h.location.setText(event.getLocation());
        h.price.setText(event.getPrice() > 0 ? String.valueOf((int) event.getPrice()) : "");
        h.capacity.setText(event.getCapacity() > 0 ? String.valueOf(event.getCapacity()) : "");
        h.switchFeatured.setChecked(event.isFeatured());

        // Category
        h.selectedCategory = event.getCategory();
        h.category.setText(event.getCategory() != null ? event.getCategory() : "");

        // Dates / time (stored as-is; date fields are ISO yyyy-MM-dd)
        h.startDate = event.getDateStart();
        h.endDate = event.getDateEnd();
        h.regDeadline = event.getRegistrationDeadline();
        h.startTime = event.getTimeStart();
        h.startDateView.setText(prettyDate(event.getDateStart()));
        h.endDateView.setText(prettyDate(event.getDateEnd()));
        h.regDeadlineView.setText(prettyDate(event.getRegistrationDeadline()));
        h.startTimeView.setText(event.getTimeStart());

        // ----- Pickers -----
        h.category.setOnClickListener(x -> new AlertDialog.Builder(x.getContext())
                .setTitle("Select Category")
                .setItems(CATEGORIES, (d, which) -> {
                    h.selectedCategory = CATEGORIES[which];
                    h.category.setText(h.selectedCategory);
                })
                .show());

        h.startDateView.setOnClickListener(x -> pickDate(x, h.startDate, iso -> {
            h.startDate = iso;
            h.startDateView.setText(prettyDate(iso));
        }));
        h.endDateView.setOnClickListener(x -> pickDate(x, h.endDate, iso -> {
            h.endDate = iso;
            h.endDateView.setText(prettyDate(iso));
        }));
        h.regDeadlineView.setOnClickListener(x -> pickDate(x, h.regDeadline, iso -> {
            h.regDeadline = iso;
            h.regDeadlineView.setText(prettyDate(iso));
        }));
        h.startTimeView.setOnClickListener(x -> pickTime(x, time -> {
            h.startTime = time;
            h.startTimeView.setText(time);
        }));

        // ----- Save -----
        h.saveButton.setOnClickListener(x -> saveEvent(h, event));

        // ----- Delete -----
        h.deleteButton.setOnClickListener(x -> new AlertDialog.Builder(x.getContext())
                .setTitle("Delete event?")
                .setMessage("\"" + event.getTitle() + "\" will be permanently removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> deleteEvent(h, event))
                .show());
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    // ---------- Save ----------

    private void saveEvent(CardHolder h, Event event) {
        String title = h.title.getText().toString().trim();
        String subtitle = h.subtitle.getText().toString().trim();
        String description = h.description.getText().toString().trim();
        String location = h.location.getText().toString().trim();
        String priceStr = h.price.getText().toString().trim();
        String capacityStr = h.capacity.getText().toString().trim();

        if (title.isEmpty()) { h.title.setError("Required"); return; }
        if (h.selectedCategory == null || h.selectedCategory.isEmpty()) {
            toast(h, "Please select a category");
            return;
        }
        if (h.startDate == null || h.startDate.isEmpty()) {
            toast(h, "Please select a start date");
            return;
        }
        if (h.endDate != null && !h.endDate.isEmpty()
                && h.endDate.compareTo(h.startDate) < 0) {
            toast(h, "End date can't be before start date");
            return;
        }
        if (h.regDeadline != null && !h.regDeadline.isEmpty()
                && h.regDeadline.compareTo(h.startDate) > 0) {
            toast(h, "Registration deadline can't be after the start date");
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("title", title);
        fields.put("subtitle", subtitle);
        fields.put("description", description);
        fields.put("category", h.selectedCategory);
        fields.put("price", priceStr.isEmpty() ? 0 : parseDouble(priceStr));
        fields.put("capacity", capacityStr.isEmpty() ? 0 : parseInt(capacityStr));
        fields.put("date_start", h.startDate);
        fields.put("date_end", h.endDate);
        fields.put("registration_deadline", h.regDeadline);
        fields.put("time_start", h.startTime);
        fields.put("location", location);
        fields.put("is_featured", h.switchFeatured.isChecked());

        h.setBusy(true);
        api.updateEvent("eq." + event.getId(), fields).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                h.setBusy(false);
                if (response.isSuccessful()) {
                    // Reflect the edits in our local model so a rebind stays consistent.
                    applyToModel(event, fields);
                    EventCache.clear();
                    toast(h, "Saved");
                } else {
                    toast(h, "Save failed (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                h.setBusy(false);
                toast(h, "Save failed");
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

    // ---------- Delete ----------

    private void deleteEvent(CardHolder h, Event event) {
        h.setBusy(true);
        api.deleteEvent("eq." + event.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                h.setBusy(false);
                if (response.isSuccessful()) {
                    EventCache.clear();
                    int pos = h.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && deleteCallback != null) {
                        deleteCallback.onDeleted(pos);
                    }
                    toast(h, "Deleted");
                } else {
                    toast(h, "Delete failed (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                h.setBusy(false);
                toast(h, "Delete failed");
            }
        });
    }

    // ---------- Pickers ----------

    private interface DateResult { void onPicked(String iso); }
    private interface TimeResult { void onPicked(String amPm); }

    private void pickDate(View anchor, String currentIso, DateResult cb) {
        Calendar c = Calendar.getInstance();
        // Seed the dialog with the current value when present.
        if (currentIso != null && currentIso.length() >= 10) {
            try {
                c.set(Integer.parseInt(currentIso.substring(0, 4)),
                        Integer.parseInt(currentIso.substring(5, 7)) - 1,
                        Integer.parseInt(currentIso.substring(8, 10)));
            } catch (Exception ignored) { }
        }
        new DatePickerDialog(anchor.getContext(),
                (view, year, month, day) -> cb.onPicked(
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void pickTime(View anchor, TimeResult cb) {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(anchor.getContext(),
                (view, hour, minute) -> {
                    int h12 = hour % 12 == 0 ? 12 : hour % 12;
                    String ampm = hour < 12 ? "AM" : "PM";
                    cb.onPicked(String.format(Locale.US, "%d:%02d %s", h12, minute, ampm));
                },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
    }

    // ---------- Helpers ----------

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

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private void toast(CardHolder h, String msg) {
        Toast.makeText(h.itemView.getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- ViewHolder ----------

    static class CardHolder extends RecyclerView.ViewHolder {
        ImageView banner;
        TextView category, startDateView, startTimeView, endDateView, regDeadlineView;
        EditText title, subtitle, description, location, price, capacity;
        MaterialSwitch switchFeatured;
        MaterialButton saveButton, deleteButton;
        ProgressBar progress;

        // Transient edit state per card.
        String selectedCategory, startDate, endDate, regDeadline, startTime;

        CardHolder(@NonNull View v) {
            super(v);
            banner = v.findViewById(R.id.card_banner);
            category = v.findViewById(R.id.card_category);
            title = v.findViewById(R.id.card_title);
            subtitle = v.findViewById(R.id.card_subtitle);
            description = v.findViewById(R.id.card_description);
            startDateView = v.findViewById(R.id.card_start_date);
            startTimeView = v.findViewById(R.id.card_start_time);
            endDateView = v.findViewById(R.id.card_end_date);
            regDeadlineView = v.findViewById(R.id.card_reg_deadline);
            location = v.findViewById(R.id.card_location);
            price = v.findViewById(R.id.card_price);
            capacity = v.findViewById(R.id.card_capacity);
            switchFeatured = v.findViewById(R.id.card_switch_featured);
            saveButton = v.findViewById(R.id.card_save_button);
            deleteButton = v.findViewById(R.id.card_delete_button);
            progress = v.findViewById(R.id.card_progress);
        }

        void setBusy(boolean busy) {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            saveButton.setEnabled(!busy);
            deleteButton.setEnabled(!busy);
        }
    }
}
