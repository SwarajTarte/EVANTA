package com.evanta.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class MyEventsAdapter extends RecyclerView.Adapter<MyEventsAdapter.ViewHolder> {

    private final List<MyEventItem> items;
    private static final int COLOR_ACTIVE   = 0xFF7C4DFF; // Purple
    private static final int COLOR_DISABLED = 0xFF555B66; // Grey

    public MyEventsAdapter(List<MyEventItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MyEventItem item = items.get(position);
        Event event = item.getEvent();
        Registration reg = item.getRegistration();
        Context context = holder.itemView.getContext();

        holder.title.setText(event.getTitle());

        // Load cover image
        if (event.getImageUrl() != null && !event.getImageUrl().trim().isEmpty()) {
            Glide.with(context)
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.launcher)
                    .error(R.drawable.launcher)
                    .centerCrop()
                    .into(holder.cover);
        } else {
            holder.cover.setImageResource(R.drawable.launcher);
        }

        // Open details activity on cover image tap
        holder.cover.setOnClickListener(v -> {
            Intent intent = new Intent(context, EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT, event);
            context.startActivity(intent);
        });

        // Setup Certificate Button
        String certUrl = reg.getCertificateUrl();
        if (certUrl == null || certUrl.trim().isEmpty()) {
            // Certificate not issued
            holder.btnCert.setText("Not Issued");
            holder.btnCert.setEnabled(false);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_DISABLED);
            bg.setCornerRadius(44f);
            holder.btnCert.setBackground(bg);
            holder.btnCert.setOnClickListener(null);
        } else {
            // Certificate is issued
            holder.btnCert.setText("Download");
            holder.btnCert.setEnabled(true);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_ACTIVE);
            bg.setCornerRadius(44f);
            holder.btnCert.setBackground(bg);

            holder.btnCert.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(certUrl));
                context.startActivity(browserIntent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        MaterialButton btnCert;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.event_cover);
            title = itemView.findViewById(R.id.event_title);
            btnCert = itemView.findViewById(R.id.btn_download_certificate);
        }
    }
}
