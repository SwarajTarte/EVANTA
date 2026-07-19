package com.evanta.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Shared adapter for the two management dialogs launched from the admin event
 * card banner:
 *   • {@link #MODE_APPROVAL}   — approve / reject enrollment requests.
 *   • {@link #MODE_CERTIFICATE} — upload a certificate for approved students.
 *
 * The adapter is presentation-only: the hosting fragment performs the network
 * work and calls {@link #setRowBusy} / {@link #notifyRowChanged} to reflect
 * results. Rows pair a {@link Registration} with its resolved {@link User}.
 */
public class RegistrationManageAdapter
        extends RecyclerView.Adapter<RegistrationManageAdapter.RowHolder> {

    public static final int MODE_APPROVAL = 0;
    public static final int MODE_CERTIFICATE = 1;

    /** A registration paired with the student who made it (may be null if unresolved). */
    public static class RegRow {
        final Registration registration;
        final User user;
        boolean busy;

        RegRow(Registration registration, User user) {
            this.registration = registration;
            this.user = user;
        }
    }

    public interface Listener {
        void onApprove(RegRow row, int position);
        void onReject(RegRow row, int position);
        void onUpload(RegRow row, int position);
    }

    private final List<RegRow> rows;
    private final int mode;
    private final Listener listener;

    public RegistrationManageAdapter(List<RegRow> rows, int mode, Listener listener) {
        this.rows = rows;
        this.mode = mode;
        this.listener = listener;
    }

    public void setRowBusy(int position, boolean busy) {
        if (position < 0 || position >= rows.size()) return;
        rows.get(position).busy = busy;
        notifyItemChanged(position);
    }

    public void notifyRowChanged(int position) {
        if (position >= 0 && position < rows.size()) notifyItemChanged(position);
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_registration_manage, parent, false);
        return new RowHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder h, int position) {
        RegRow row = rows.get(position);
        Registration reg = row.registration;
        User user = row.user;

        // Identity — full name on top, college underneath.
        String name = user != null && user.getName() != null && !user.getName().trim().isEmpty()
                ? user.getName().trim() : "Student";
        h.name.setText(name);

        String college = user != null ? user.getCollegeName() : null;
        String branch = user != null ? user.getBranch() : null;
        String meta;
        if (college != null && !college.trim().isEmpty()) {
            meta = branch != null && !branch.trim().isEmpty()
                    ? college.trim() + " • " + branch.trim()
                    : college.trim();
        } else if (branch != null && !branch.trim().isEmpty()) {
            meta = branch.trim();
        } else {
            meta = user != null && user.getEmail() != null ? user.getEmail() : "";
        }
        h.meta.setText(meta);

        h.avatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
        String photo = user != null ? user.getPhotoUrl() : null;
        if (photo != null && !photo.isEmpty()) {
            h.avatarImage.setVisibility(View.VISIBLE);
            h.avatarInitial.setVisibility(View.GONE);
            Glide.with(h.avatarImage.getContext()).load(photo).circleCrop().into(h.avatarImage);
        } else {
            h.avatarImage.setVisibility(View.GONE);
            h.avatarInitial.setVisibility(View.VISIBLE);
        }

        // Reset trailing controls
        h.actionGroup.setVisibility(View.GONE);
        h.upload.setVisibility(View.GONE);
        h.statusPill.setVisibility(View.GONE);
        h.progress.setVisibility(View.GONE);

        if (row.busy) {
            h.progress.setVisibility(View.VISIBLE);
            return;
        }

        if (mode == MODE_APPROVAL) {
            bindApproval(h, reg, position, row);
        } else {
            bindCertificate(h, reg, position, row);
        }
    }

    private void bindApproval(RowHolder h, Registration reg, int position, RegRow row) {
        if (reg.isPending()) {
            h.actionGroup.setVisibility(View.VISIBLE);
            h.approve.setOnClickListener(v -> listener.onApprove(row, h.getBindingAdapterPosition()));
            h.reject.setOnClickListener(v -> listener.onReject(row, h.getBindingAdapterPosition()));
        } else {
            h.statusPill.setVisibility(View.VISIBLE);
            if (reg.isApproved()) {
                h.statusPill.setText("Approved");
                h.statusPill.setBackgroundResource(R.drawable.bg_category_pill);
            } else {
                h.statusPill.setText("Rejected");
                h.statusPill.setBackgroundResource(R.drawable.bg_chip);
            }
        }
    }

    private void bindCertificate(RowHolder h, Registration reg, int position, RegRow row) {
        boolean sent = reg.getCertificateUrl() != null && !reg.getCertificateUrl().trim().isEmpty();
        if (sent) {
            // Allow re-upload but show it's already been sent.
            h.upload.setVisibility(View.VISIBLE);
            h.upload.setText("Re-send");
            h.statusPill.setVisibility(View.GONE);
        } else {
            h.upload.setVisibility(View.VISIBLE);
            h.upload.setText("Upload");
        }
        h.upload.setOnClickListener(v -> listener.onUpload(row, h.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final TextView avatarInitial, name, meta, statusPill;
        final ImageView avatarImage;
        final View actionGroup;
        final MaterialButton approve, reject, upload;
        final ProgressBar progress;

        RowHolder(@NonNull View v) {
            super(v);
            avatarInitial = v.findViewById(R.id.reg_avatar_initial);
            avatarImage = v.findViewById(R.id.reg_avatar_image);
            name = v.findViewById(R.id.reg_name);
            meta = v.findViewById(R.id.reg_meta);
            statusPill = v.findViewById(R.id.reg_status_pill);
            actionGroup = v.findViewById(R.id.reg_action_group);
            approve = v.findViewById(R.id.reg_approve);
            reject = v.findViewById(R.id.reg_reject);
            upload = v.findViewById(R.id.reg_upload);
            progress = v.findViewById(R.id.reg_progress);
        }
    }
}
