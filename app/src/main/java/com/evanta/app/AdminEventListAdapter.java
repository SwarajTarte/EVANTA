package com.evanta.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * List adapter for Admin events that includes an expandable AI Event Summary panel
 * below each event card, powered by {@link AiRepository}.
 */
public class AdminEventListAdapter extends RecyclerView.Adapter<AdminEventListAdapter.ViewHolder> {

    public interface OnEventClick {
        void onClick(Event event);
    }

    private final List<Event> events;
    private final OnEventClick clickListener;
    private final Set<String> expandedIds = new HashSet<>();
    private AiRepository aiRepository;

    public AdminEventListAdapter(List<Event> events, OnEventClick clickListener) {
        this.events = events;
        this.clickListener = clickListener;
    }

    private AiRepository getAiRepository() {
        if (aiRepository == null) {
            AiApiService apiService = RetrofitClient.getClient().create(AiApiService.class);
            aiRepository = new AiRepository(apiService);
        }
        return aiRepository;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_event_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        Context context = holder.itemView.getContext();
        int color = CategoryColors.forCategory(event.getCategory());

        holder.thumbnailText.setVisibility(View.GONE);
        holder.thumbnailImage.setVisibility(View.VISIBLE);

        if (event.getImageUrl() != null && !event.getImageUrl().trim().isEmpty()) {
            Glide.with(context)
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.launcher)
                    .error(R.drawable.launcher)
                    .centerCrop()
                    .into(holder.thumbnailImage);
        } else {
            holder.thumbnailImage.setImageResource(R.drawable.launcher);
        }

        holder.title.setText(event.getTitle());
        holder.categoryTag.setText(event.getCategory());
        holder.categoryTag.setTextColor(color);
        ((GradientDrawable) holder.categoryTag.getBackground().mutate())
                .setColor(adjustAlpha(color, 60));

        holder.date.setText(formatDate(event.getDateStart(), event.getTimeStart()));
        holder.location.setText(event.getLocation());

        if (event.getPrice() <= 0) {
            holder.price.setText("Free");
            holder.price.setTextColor(0xFF27AE60);
        } else {
            holder.price.setText("₹" + (int) event.getPrice());
            holder.price.setTextColor(0xFFE0568C);
        }

        // Handle Main Card Click (opens editor)
        holder.cardMainContent.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(event);
        });

        // Expand / Collapse AI Summary Panel
        boolean isExpanded = event.getId() != null && expandedIds.contains(event.getId());
        holder.layoutAiSummaryPanel.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.ivAiChevron.setRotation(isExpanded ? 180f : 0f);

        holder.btnToggleAiSummary.setOnClickListener(v -> {
            if (event.getId() == null) return;
            if (expandedIds.contains(event.getId())) {
                expandedIds.remove(event.getId());
                holder.layoutAiSummaryPanel.setVisibility(View.GONE);
                holder.ivAiChevron.setRotation(0f);
            } else {
                expandedIds.add(event.getId());
                holder.layoutAiSummaryPanel.setVisibility(View.VISIBLE);
                holder.ivAiChevron.setRotation(180f);
            }
        });

        // Generate AI Summary Button Action
        holder.btnGenerateSummary.setOnClickListener(v -> {
            String customNotes = holder.etAdminNotes.getText().toString().trim();
            String rawQuery;

            if (!customNotes.isEmpty()) {
                rawQuery = customNotes;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Title: ").append(event.getTitle() != null ? event.getTitle() : "").append("\n");
                if (event.getSubtitle() != null && !event.getSubtitle().isEmpty()) {
                    sb.append("Subtitle: ").append(event.getSubtitle()).append("\n");
                }
                if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                    sb.append("Description: ").append(event.getDescription()).append("\n");
                }
                if (event.getCategory() != null) {
                    sb.append("Category: ").append(event.getCategory()).append("\n");
                }
                if (event.getLocation() != null) {
                    sb.append("Location: ").append(event.getLocation()).append("\n");
                }
                rawQuery = sb.toString();
            }

            holder.pbAdminAiLoading.setVisibility(View.VISIBLE);
            holder.btnGenerateSummary.setEnabled(false);
            holder.layoutAiResultContainer.setVisibility(View.GONE);

            getAiRepository().generateEventSummary(rawQuery, new AiRepository.AiCallback() {
                @Override
                public void onSuccess(String result) {
                    holder.itemView.post(() -> {
                        holder.pbAdminAiLoading.setVisibility(View.GONE);
                        holder.btnGenerateSummary.setEnabled(true);
                        holder.tvAdminAiSummary.setText(result);
                        holder.layoutAiResultContainer.setVisibility(View.VISIBLE);
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    holder.itemView.post(() -> {
                        holder.pbAdminAiLoading.setVisibility(View.GONE);
                        holder.btnGenerateSummary.setEnabled(true);
                        Toast.makeText(context, "AI Error: " + errorMessage, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        // Copy Summary to Clipboard Action
        holder.btnCopySummary.setOnClickListener(v -> {
            String textToCopy = holder.tvAdminAiSummary.getText().toString();
            if (textToCopy.isEmpty()) return;

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("AI Event Summary", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Summary copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private String formatDate(String dateStart, String timeStart) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
            String datePart = output.format(input.parse(dateStart));
            return (timeStart != null && !timeStart.isEmpty()) ? datePart + " • " + timeStart : datePart;
        } catch (Exception e) {
            return dateStart;
        }
    }

    private int adjustAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View cardMainContent, btnToggleAiSummary, layoutAiSummaryPanel, layoutAiResultContainer;
        ImageView thumbnailImage, ivAiChevron;
        TextView thumbnailText, title, categoryTag, date, location, price, tvAdminAiSummary;
        EditText etAdminNotes;
        ProgressBar pbAdminAiLoading;
        MaterialButton btnGenerateSummary, btnCopySummary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardMainContent = itemView.findViewById(R.id.card_main_content);
            thumbnail = itemView.findViewById(R.id.event_thumbnail);
            thumbnailImage = itemView.findViewById(R.id.event_thumbnail_image);
            thumbnailText = itemView.findViewById(R.id.event_thumbnail_text);
            title = itemView.findViewById(R.id.event_title);
            categoryTag = itemView.findViewById(R.id.event_category_tag);
            date = itemView.findViewById(R.id.event_date);
            location = itemView.findViewById(R.id.event_location);
            price = itemView.findViewById(R.id.event_price);

            btnToggleAiSummary = itemView.findViewById(R.id.btn_toggle_ai_summary);
            layoutAiSummaryPanel = itemView.findViewById(R.id.layout_ai_summary_panel);
            layoutAiResultContainer = itemView.findViewById(R.id.layout_ai_result_container);
            ivAiChevron = itemView.findViewById(R.id.iv_ai_chevron);
            etAdminNotes = itemView.findViewById(R.id.et_admin_notes);
            pbAdminAiLoading = itemView.findViewById(R.id.pb_admin_ai_loading);
            btnGenerateSummary = itemView.findViewById(R.id.btn_generate_summary);
            tvAdminAiSummary = itemView.findViewById(R.id.tv_admin_ai_summary);
            btnCopySummary = itemView.findViewById(R.id.btn_copy_summary);
        }

        View thumbnail;
    }
}
