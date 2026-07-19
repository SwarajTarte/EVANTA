package com.evanta.app;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Compact, browse-style list of the admin's events — the same row look as the
 * student Browse tab ({@link EventRowAdapter}). Tapping a row opens
 * {@link AdminEventEditActivity} for that event rather than a detail screen.
 */
public class AdminEventListAdapter extends RecyclerView.Adapter<AdminEventListAdapter.ViewHolder> {

    public interface OnEventClick {
        void onClick(Event event);
    }

    private final List<Event> events;
    private final OnEventClick clickListener;

    public AdminEventListAdapter(List<Event> events, OnEventClick clickListener) {
        this.events = events;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        int color = CategoryColors.forCategory(event.getCategory());

        holder.thumbnailText.setVisibility(View.GONE);
        holder.thumbnailImage.setVisibility(View.VISIBLE);

        if (event.getImageUrl() != null && !event.getImageUrl().trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
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

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(event);
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
        View thumbnail;
        ImageView thumbnailImage;
        TextView thumbnailText, title, categoryTag, date, location, price;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.event_thumbnail);
            thumbnailImage = itemView.findViewById(R.id.event_thumbnail_image);
            thumbnailText = itemView.findViewById(R.id.event_thumbnail_text);
            title = itemView.findViewById(R.id.event_title);
            categoryTag = itemView.findViewById(R.id.event_category_tag);
            date = itemView.findViewById(R.id.event_date);
            location = itemView.findViewById(R.id.event_location);
            price = itemView.findViewById(R.id.event_price);
        }
    }
}
