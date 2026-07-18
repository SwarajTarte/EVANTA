package com.example.evanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProfileFragment extends Fragment {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    private TextView avatarInitial, fullNameHeader, nameRowValue,
            emailRowValue, whatsappRowValue, collegeRowValue;
    private ImageView avatarImage;
    private MaterialButton logoutBut;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        googleSignInClient = GoogleSignIn.getClient(requireContext(),
                GoogleSignInOptions.DEFAULT_SIGN_IN);

        avatarInitial   = view.findViewById(R.id.avatar_initial);
        avatarImage     = view.findViewById(R.id.avatar_image);
        fullNameHeader  = view.findViewById(R.id.profile_full_name);
        nameRowValue    = view.findViewById(R.id.value_name_row);
        emailRowValue   = view.findViewById(R.id.value_email_row);
        whatsappRowValue = view.findViewById(R.id.value_whatsapp_row);
        collegeRowValue = view.findViewById(R.id.value_college_row);
        logoutBut       = view.findViewById(R.id.logoutbut);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        User cached = UserCache.get(requireContext());
        if (cached != null) bindUser(cached);

        loadUserFromSupabase(currentUser.getUid());

        logoutBut.setOnClickListener(v -> logout());

        view.findViewById(R.id.edit_avatar_icon).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AdminEditProfileActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) loadUserFromSupabase(currentUser.getUid());
    }

    private void loadUserFromSupabase(String uid) {
        new UserRepository().getUserByUid(uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    UserCache.set(requireContext(), user);
                    bindUser(user);
                } else if (response.isSuccessful()) {
                    Toast.makeText(requireContext(),
                            "No profile row found for this account.",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(),
                            "Could not load profile: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUser(User user) {
        String fullName = user.getName() != null ? user.getName() : "";
        String nickname = fullName.trim().isEmpty() ? ""
                : fullName.trim().split(" ")[0];

        fullNameHeader.setText(nickname);
        nameRowValue.setText(fullName);
        emailRowValue.setText(user.getEmail());
        whatsappRowValue.setText(user.getWhatsappno());

        if (!fullName.trim().isEmpty()) {
            avatarInitial.setText(
                    String.valueOf(fullName.trim().charAt(0)).toUpperCase());
        }

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            avatarImage.setVisibility(View.VISIBLE);
            avatarInitial.setVisibility(View.GONE);
            Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(avatarImage);
        } else {
            avatarImage.setVisibility(View.GONE);
            avatarInitial.setVisibility(View.VISIBLE);
        }

        // Prefetches the resolved college data identically to the student logic
        if (user.getCollegeId() != null) {
            if (user.getCollegeName() != null && !user.getCollegeName().isEmpty()) {
                collegeRowValue.setText(user.getCollegeName());
            } else {
                // Instantly query the local cache to bypass network delay on tab switch
                String resolved = UserCache.get(requireContext()).getCollegeName();
                if (resolved != null && !resolved.isEmpty()) {
                    collegeRowValue.setText(resolved);
                } else {
                    loadCollegeName(user.getCollegeId());
                }
            }
        } else if (user.getCollegeName() != null && !user.getCollegeName().isEmpty()) {
            collegeRowValue.setText(user.getCollegeName());
        } else {
            collegeRowValue.setText("Not set");
        }
    }

    private void loadCollegeName(String collegeId) {
        RetrofitClient.getClient().create(SupabaseApi.class)
                .getCollegeById("eq." + collegeId)
                .enqueue(new Callback<List<College>>() {
                    @Override
                    public void onResponse(Call<List<College>> call,
                                           Response<List<College>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            String name = response.body().get(0).getName();
                            collegeRowValue.setText(name);
                            // Saves straight to cache so subsequent visits match perfectly
                            if (name != null && !name.isEmpty()) {
                                UserCache.setCollegeNameResolved(requireContext(), name);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<College>> call, Throwable t) {
                        if (!isAdded()) return;
                        collegeRowValue.setText("Could not load");
                    }
                });
    }

    private void logout() {
        mAuth.signOut();
        UserCache.clear(requireContext());
        EventCache.clear();
        PrefetchCache.clear();

        googleSignInClient.revokeAccess().addOnCompleteListener(task ->
                googleSignInClient.signOut().addOnCompleteListener(signOutTask -> {
                    Intent intent = new Intent(requireContext(), WelcomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                }));
    }
}