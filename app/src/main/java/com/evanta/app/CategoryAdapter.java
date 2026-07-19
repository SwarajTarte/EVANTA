package com.evanta.app;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    public interface OnCategoryClick {
        void onCategoryClick(Category category);
    }

    private final List<Category> categories;
    private final OnCategoryClick listener;
    private int selectedPosition = 0;

    public CategoryAdapter(List<Category> categories, OnCategoryClick listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Category category = categories.get(position);
        boolean selected = position == selectedPosition;

        holder.label.setText(category.getLabel());
        holder.icon.setImageResource(category.getIconRes());

        GradientDrawable background = (GradientDrawable) holder.root.getBackground().mutate();

        if (selected) {
            background.setColor(category.getColor());
            holder.icon.setColorFilter(0xFFFFFFFF);
            holder.label.setTextColor(0xFFFFFFFF);
        } else {
            background.setColor(0xFF1B2033);
            holder.icon.setColorFilter(category.getColor());
            holder.label.setTextColor(0xFFB8C0D4);
        }

        holder.root.setOnClickListener(v -> {
            int previous = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);
            listener.onCategoryClick(category);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout root;
        ImageView icon;
        TextView label;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = (LinearLayout) itemView;
            icon = itemView.findViewById(R.id.chip_icon);
            label = itemView.findViewById(R.id.chip_label);
        }
    }
}