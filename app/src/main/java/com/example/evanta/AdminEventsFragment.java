package com.example.evanta;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

/**
 * Admin "Events" tab: a student-home-style header over a browse-style list of
 * the admin's own events, each an always-editable card ({@link AdminEventCardAdapter}).
 *
 * Each card banner carries two actions this fragment orchestrates:
 *   • Approvals   — approve / reject enrollment requests (updates status + notifies).
 *   • Certificates — upload a PDF/image certificate per approved student (+ notifies).
 */
public class AdminEventsFragment extends Fragment
        implements AdminEventCardAdapter.OnManageClick {

    private static final String CERT_BUCKET = "certificates";

    private TextView greeting;
    private TextView avatarInitial;
    private ImageView avatarImage;

    private RecyclerView recycler;
    private ProgressBar loader;
    private View emptyState;
    private TextView emptyTitle, emptyMessage;

    private final List<Event> events = new ArrayList<>();
    private AdminEventCardAdapter adapter;

    // ----- Pending certificate-upload target (a file pick is asynchronous) -----
    private RegistrationManageAdapter.RegRow pendingRow;
    private int pendingPosition = -1;
    private Event pendingEvent;

    private final ActivityResultLauncher<String[]> certificatePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onCertificateFilePicked(uri);
                else clearPendingUpload();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        greeting = view.findViewById(R.id.admin_greeting);
        avatarInitial = view.findViewById(R.id.admin_avatar_initial);
        avatarImage = view.findViewById(R.id.admin_avatar_image);
        recycler = view.findViewById(R.id.admin_events_recycler);
        loader = view.findViewById(R.id.admin_events_loader);
        emptyState = view.findViewById(R.id.admin_events_state);
        emptyTitle = view.findViewById(R.id.admin_events_state_title);
        emptyMessage = view.findViewById(R.id.admin_events_state_message);

        view.findViewById(R.id.admin_notification_icon).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationCenterActivity.class)));

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminEventCardAdapter(events, this::onEventDeleted, this);
        recycler.setAdapter(adapter);

        User user = UserCache.get(requireContext());
        bindHeader(user);

        loadEvents(user != null ? user.getCollegeId() : null);
    }

    @Override
    public void onResume() {
        super.onResume();
        // A row may have been edited/deleted, or a new event added from the Add
        // tab — refresh silently so the list stays current.
        if (adapter != null) {
            User user = UserCache.get(requireContext());
            loadEvents(user != null ? user.getCollegeId() : null);
        }
    }

    private void bindHeader(User user) {
        String fullName = user != null && user.getName() != null ? user.getName() : "";
        String nickname = fullName.trim().isEmpty() ? "there" : fullName.trim().split(" ")[0];
        greeting.setText("Hi, " + nickname + " 👋");

        if (!fullName.trim().isEmpty()) {
            avatarInitial.setText(String.valueOf(fullName.trim().charAt(0)).toUpperCase());
        }

        String photo = user != null ? user.getPhotoUrl() : null;
        if (photo != null && !photo.isEmpty()) {
            avatarImage.setVisibility(View.VISIBLE);
            avatarInitial.setVisibility(View.GONE);
            Glide.with(this).load(photo).circleCrop().into(avatarImage);
        } else {
            avatarImage.setVisibility(View.GONE);
            avatarInitial.setVisibility(View.VISIBLE);
        }
    }

    private void loadEvents(String collegeId) {
        if (collegeId == null || collegeId.isEmpty()) {
            showEmpty("No College Linked",
                    "Your admin account isn't linked to a college yet.");
            return;
        }

        // Only show the spinner on a truly empty first load; otherwise refresh
        // silently to avoid a flash on every resume.
        if (events.isEmpty()) showLoading();

        new EventRepository().getEventsByCollege(collegeId)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Event>> call,
                                           @NonNull Response<List<Event>> response) {
                        if (!isAdded()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            events.clear();
                            events.addAll(response.body());
                            adapter.notifyDataSetChanged();

                            if (events.isEmpty()) {
                                showEmpty("No Events Yet",
                                        "Create your first event from the Add tab.");
                            } else {
                                showList();
                            }
                        } else if (events.isEmpty()) {
                            showEmpty("Couldn't Load Events",
                                    "Please check your connection and try again.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        if (events.isEmpty()) {
                            showEmpty("Couldn't Load Events",
                                    "Please check your connection and try again.");
                        }
                    }
                });
    }

    private void onEventDeleted(int position) {
        if (position < 0 || position >= events.size()) return;
        events.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, events.size());
        if (events.isEmpty()) {
            showEmpty("No Events Yet", "Create your first event from the Add tab.");
        }
    }

    // ==================================================================
    //  Manage flows (invoked from the card banner buttons)
    // ==================================================================

    @Override
    public void onApprovals(Event event) {
        showManageDialog(event, RegistrationManageAdapter.MODE_APPROVAL);
    }

    @Override
    public void onCertificates(Event event) {
        showManageDialog(event, RegistrationManageAdapter.MODE_CERTIFICATE);
    }

    private void showManageDialog(Event event, int mode) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_manage_list, null, false);

        TextView title = content.findViewById(R.id.manage_title);
        TextView subtitle = content.findViewById(R.id.manage_subtitle);
        RecyclerView list = content.findViewById(R.id.manage_recycler);
        ProgressBar dialogLoader = content.findViewById(R.id.manage_loader);
        TextView empty = content.findViewById(R.id.manage_empty);

        boolean approval = mode == RegistrationManageAdapter.MODE_APPROVAL;
        title.setText(approval ? "Enrollment Requests" : "Certificates");
        subtitle.setText(approval
                ? "Approve or reject students who registered."
                : "Upload a certificate for approved students.");

        List<RegistrationManageAdapter.RegRow> rows = new ArrayList<>();
        RegistrationManageAdapter listAdapter =
                new RegistrationManageAdapter(rows, mode, new RegistrationManageAdapter.Listener() {
                    @Override
                    public void onApprove(RegistrationManageAdapter.RegRow row, int position) {
                        setStatus(event, row, position, Registration.STATUS_APPROVED);
                    }

                    @Override
                    public void onReject(RegistrationManageAdapter.RegRow row, int position) {
                        setStatus(event, row, position, Registration.STATUS_REJECTED);
                    }

                    @Override
                    public void onUpload(RegistrationManageAdapter.RegRow row, int position) {
                        startCertificateUpload(event, row, position);
                    }
                });

        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(listAdapter);
        // Only one management dialog is open at a time; approve/reject/upload all
        // mutate this adapter.
        activeManageAdapter = listAdapter;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(content);
        content.findViewById(R.id.manage_close).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (activeManageAdapter == listAdapter) activeManageAdapter = null;
        });

        dialog.show();

        loadRegistrations(event, mode, rows, listAdapter, dialogLoader, empty);
    }

    private void loadRegistrations(Event event, int mode,
                                   List<RegistrationManageAdapter.RegRow> rows,
                                   RegistrationManageAdapter listAdapter,
                                   ProgressBar dialogLoader, TextView empty) {
        dialogLoader.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.getRegistrationsByEventId("eq." + event.getId(), "registered_at.asc")
                .enqueue(new Callback<List<Registration>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Registration>> call,
                                           @NonNull Response<List<Registration>> response) {
                        if (!isAdded()) return;

                        List<Registration> all = response.isSuccessful() && response.body() != null
                                ? response.body() : new ArrayList<>();

                        // Certificates only make sense for approved students.
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
                        if (!isAdded()) return;
                        dialogLoader.setVisibility(View.GONE);
                        empty.setText("Couldn't load. Check your connection.");
                        empty.setVisibility(View.VISIBLE);
                    }
                });
    }

    /** Batch-fetch the student profiles for the given registrations, then bind. */
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

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.getUsersByUids(ids.toString()).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call,
                                   @NonNull Response<List<User>> response) {
                if (!isAdded()) return;

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
                if (!isAdded()) return;
                // Fall back to rows without resolved names rather than failing hard.
                rows.clear();
                for (Registration r : registrations) {
                    rows.add(new RegistrationManageAdapter.RegRow(r, null));
                }
                listAdapter.notifyDataSetChanged();
                dialogLoader.setVisibility(View.GONE);
            }
        });
    }

    // ---------- Approve / reject ----------

    private void setStatus(Event event, RegistrationManageAdapter.RegRow row,
                           int position, String status) {
        final RegistrationManageAdapter listAdapter = activeManageAdapter;
        if (listAdapter != null) listAdapter.setRowBusy(position, true);

        Map<String, Object> fields = new HashMap<>();
        fields.put("status", status);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.updateRegistrationStatus("eq." + row.registration.getId(), fields)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            row.registration.setStatus(status);
                            if (Registration.STATUS_APPROVED.equals(status)) {
                                notifyStudent(row.registration.getUserUid(), event.getId(),
                                        "Enrollment Approved",
                                        "You're approved for \"" + event.getTitle()
                                                + "\". See you there!",
                                        Notification.TYPE_GENERAL);
                            }
                        } else {
                            toast("Update failed (" + response.code() + ")");
                        }
                        if (listAdapter != null) {
                            listAdapter.setRowBusy(position, false);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        if (listAdapter != null) listAdapter.setRowBusy(position, false);
                        toast("Update failed");
                    }
                });
    }

    // ---------- Certificate upload ----------

    private void startCertificateUpload(Event event, RegistrationManageAdapter.RegRow row,
                                        int position) {
        pendingRow = row;
        pendingPosition = position;
        pendingEvent = event;
        // Accept both PDFs and images.
        certificatePicker.launch(new String[]{"application/pdf", "image/*"});
    }

    private void onCertificateFilePicked(Uri uri) {
        if (pendingRow == null || pendingEvent == null || activeManageAdapter == null) {
            clearPendingUpload();
            return;
        }

        final RegistrationManageAdapter listAdapter = activeManageAdapter;
        final RegistrationManageAdapter.RegRow row = pendingRow;
        final int position = pendingPosition;
        final Event event = pendingEvent;
        // Remember any existing certificate so a re-send can clean it up.
        final String previousUrl = row.registration.getCertificateUrl();

        listAdapter.setRowBusy(position, true);

        new Thread(() -> {
            String url = uploadCertificateBlocking(uri, event.getId(),
                    row.registration.getUserUid());
            // The new file is up — remove the stale one so the bucket doesn't
            // accumulate orphans on repeated re-uploads. Best-effort.
            if (url != null && previousUrl != null && !previousUrl.equals(url)) {
                deleteCertificateBlocking(previousUrl);
            }
            runOnUi(() -> {
                if (!isAdded()) return;
                if (url == null) {
                    listAdapter.setRowBusy(position, false);
                    return;
                }
                patchCertificateUrl(event, row, position, url, listAdapter);
            });
        }).start();

        clearPendingUpload();
    }

    private void patchCertificateUrl(Event event, RegistrationManageAdapter.RegRow row,
                                     int position, String url,
                                     RegistrationManageAdapter listAdapter) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("certificate_url", url);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.updateRegistrationStatus("eq." + row.registration.getId(), fields)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!isAdded()) return;
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
                        if (!isAdded()) return;
                        listAdapter.setRowBusy(position, false);
                        toast("Couldn't save certificate");
                    }
                });
    }

    /** Uploads the picked file as-is to the certificates bucket; returns its public URL. */
    private String uploadCertificateBlocking(Uri uri, String eventId, String userUid) {
        try {
            String mime = requireContext().getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            String ext = extensionFor(mime);

            byte[] bytes;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
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
                    .addHeader("Authorization", "Bearer " + SupabaseConfig.API_KEY)
                    .addHeader("x-upsert", "true")
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return SupabaseConfig.BASE_URL
                            + "storage/v1/object/public/" + CERT_BUCKET + "/" + fileName;
                }
                final int code = response.code();
                final String errBody = response.body() != null ? response.body().string() : "";
                android.util.Log.e("AdminEvents",
                        "Certificate upload failed " + code + ": " + errBody);
                runOnUi(() -> toast(certUploadError(code, errBody)));
                return null;
            }
        } catch (IOException e) {
            android.util.Log.e("AdminEvents", "Certificate upload error", e);
            runOnUi(() -> toast("Upload network error"));
            return null;
        }
    }

    /**
     * Deletes a previously-uploaded certificate object. Derives the storage path
     * from the public URL (…/object/public/<bucket>/<path>). Best-effort — failure
     * only leaves an orphan, it never blocks the re-upload that already succeeded.
     */
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
                    .addHeader("Authorization", "Bearer " + SupabaseConfig.API_KEY)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    android.util.Log.w("AdminEvents",
                            "Old certificate delete failed " + response.code());
                }
            }
        } catch (Exception e) {
            android.util.Log.w("AdminEvents", "Old certificate delete error", e);
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

    // ---------- Notifications ----------

    private void notifyStudent(String userUid, String eventId, String title,
                               String bodyText, String type) {
        if (userUid == null) return;
        Map<String, Object> fields = new HashMap<>();
        fields.put("user_uid", userUid);
        fields.put("title", title);
        fields.put("body", bodyText);
        fields.put("type", type);
        fields.put("event_id", eventId);
        fields.put("is_read", false);

        RetrofitClient.getClient().create(SupabaseApi.class)
                .pushNotification(fields).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        // Best-effort — a failed notification shouldn't block the flow.
                        if (!response.isSuccessful()) {
                            android.util.Log.w("AdminEvents",
                                    "Notification push failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        android.util.Log.w("AdminEvents", "Notification push error", t);
                    }
                });
    }

    // ---------- Helpers ----------

    // The adapter of the currently-open management dialog (approve/reject/upload
    // all mutate this one). Set when a dialog is shown, cleared on dismiss.
    private RegistrationManageAdapter activeManageAdapter;

    private void clearPendingUpload() {
        pendingRow = null;
        pendingPosition = -1;
        pendingEvent = null;
    }

    private void runOnUi(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- View state ----------

    private void showLoading() {
        loader.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showList() {
        loader.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty(String title, String message) {
        loader.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(title);
        emptyMessage.setText(message);
    }
}
