package com.example.evanta;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationTapListener {
        void onTap(Notification notification, int position);
    }

    private final List<Notification> items;
    private final OnNotificationTapListener listener;

    public NotificationAdapter(List<Notification> items, OnNotificationTapListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notif = items.get(position);

        holder.title.setText(notif.getTitle());
        holder.body.setText(notif.getBody());
        holder.time.setText(formatTime(notif.getCreatedAt()));

        holder.icon.setImageResource(iconForType(notif.getType()));

        if (notif.isRead()) {
            holder.itemView.setAlpha(0.55f);
            holder.unreadDot.setVisibility(View.GONE);
        } else {
            holder.itemView.setAlpha(1f);
            holder.unreadDot.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTap(notif, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int iconForType(String type) {
        if (type == null) return R.drawable.ic_notification_general;
        switch (type) {
            case Notification.TYPE_UPCOMING:          return R.drawable.ic_notification_upcoming;
            case Notification.TYPE_CERTIFICATE:       return R.drawable.ic_notification_certificate;
            case Notification.TYPE_REGISTRATION_CLOSED: return R.drawable.ic_notification_closed;
            case Notification.TYPE_NEW_EVENT:         return R.drawable.ic_notification_upcoming;
            default:                                  return R.drawable.ic_notification_general;
        }
    }

    private String formatTime(String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) return "";
        try {
            // Full timestamps (admin-pushed) include a time component.
            if (createdAt.length() > 10) {
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat output = new SimpleDateFormat("d MMM • h:mm a", Locale.getDefault());
                Date date = input.parse(createdAt.length() > 19 ? createdAt.substring(0, 19) : createdAt);
                return output.format(date);
            }
            // Date-only values (derived notifications) show just the day.
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
            Date date = input.parse(createdAt);
            return output.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, body, time;
        View unreadDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.notif_icon);
            title = itemView.findViewById(R.id.notif_title);
            body = itemView.findViewById(R.id.notif_body);
            time = itemView.findViewById(R.id.notif_time);
            unreadDot = itemView.findViewById(R.id.notif_unread_dot);
        }
    }
}