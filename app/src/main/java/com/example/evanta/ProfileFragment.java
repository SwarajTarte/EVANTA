package com.example.evanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.bumptech.glide.Glide;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    private TextView avatarInitial, fullNameHeader, nameRowValue, emailRowValue, whatsappRowValue;
    private MaterialButton logoutBut;
    private android.widget.ImageView avatarImage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }
    @Override
    public void onResume() {
        super.onResume();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadUserFromSupabase(currentUser.getUid());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        googleSignInClient = GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN);

        avatarInitial = view.findViewById(R.id.avatar_initial);
        fullNameHeader = view.findViewById(R.id.profile_full_name);
        nameRowValue = view.findViewById(R.id.value_name_row);
        emailRowValue = view.findViewById(R.id.value_email_row);
        whatsappRowValue = view.findViewById(R.id.value_whatsapp_row);
        avatarImage = view.findViewById(R.id.avatar_image);
        logoutBut = view.findViewById(R.id.logoutbut);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        loadUserFromSupabase(currentUser.getUid());

        logoutBut.setOnClickListener(v -> logout());

        View editAvatarIcon = view.findViewById(R.id.edit_avatar_icon);
        editAvatarIcon.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), EditProfileActivity.class));
        });
    }



    private void loadUserFromSupabase(String uid) {

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getUserByUid("eq." + uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {

                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {

                    User user = response.body().get(0);
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
                Toast.makeText(requireContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUser(User user) {

        String fullName = user.getName() != null ? user.getName() : "";
        String nickname = fullName.trim().isEmpty() ? "" : fullName.trim().split(" ")[0];

        fullNameHeader.setText(nickname);
        nameRowValue.setText(fullName);
        emailRowValue.setText(user.getEmail());
        whatsappRowValue.setText(user.getWhatsappno());

        if (!fullName.trim().isEmpty()) {
            avatarInitial.setText(String.valueOf(fullName.trim().charAt(0)).toUpperCase());
        }

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            avatarImage.setVisibility(View.VISIBLE);
            avatarInitial.setVisibility(View.GONE);
            Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(avatarImage);
        } else {
            avatarImage.setVisibility(View.GONE);
            avatarInitial.setVisibility(View.VISIBLE);
        }
    }

    private void logout() {

        mAuth.signOut();

        googleSignInClient.signOut().addOnCompleteListener(task -> {

            Intent intent = new Intent(requireContext(), WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }
}