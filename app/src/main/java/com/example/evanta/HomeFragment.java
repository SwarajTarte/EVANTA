package com.example.evanta;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

public class HomeFragment extends Fragment {

    private TextView greetingText;
    private TextView avatarInitial;
    private ImageView avatarImage;

    private View featuredCard1, featuredCard2, featuredCard3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        greetingText = view.findViewById(R.id.home_greeting);
        avatarInitial = view.findViewById(R.id.home_avatar_initial);
        avatarImage = view.findViewById(R.id.home_avatar_image);

        featuredCard1 = view.findViewById(R.id.featured_card_1);
        featuredCard2 = view.findViewById(R.id.featured_card_2);
        featuredCard3 = view.findViewById(R.id.featured_card_3);

        View viewAllEvents = view.findViewById(R.id.view_all_events);
        viewAllEvents.setOnClickListener(v -> {

            NavController nav = Navigation.findNavController(v);

            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(nav.getGraph().getStartDestinationId(), false, true)
                    .build();

            nav.navigate(R.id.browseFragment, null, options);
        });

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            loadUser(currentUser.getUid());
        }

        fetchFeaturedEvents();
    }

    // ---------- Greeting + avatar ----------

    private void loadUser(String uid) {

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getUserByUid("eq." + uid).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {

                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    bindUser(response.body().get(0));
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                // keep defaults on failure
            }
        });
    }

    private void bindUser(User user) {

        String fullName = user.getName() != null ? user.getName() : "";
        String nickname = fullName.trim().isEmpty() ? "there" : fullName.trim().split(" ")[0];

        greetingText.setText("Hi, " + nickname + " \uD83D\uDC4B");

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

    // ---------- Featured events ----------

    private void fetchFeaturedEvents() {

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getFeaturedEvents("eq.true", "date_start.asc", 3).enqueue(new Callback<List<Event>>() {
            @Override
            public void onResponse(Call<List<Event>> call, Response<List<Event>> response) {

                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    bindFeaturedEvents(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<Event>> call, Throwable t) {
                // leave cards as-is on failure
            }
        });
    }

    private void bindFeaturedEvents(List<Event> events) {

        View[] cards = { featuredCard1, featuredCard2, featuredCard3 };

        for (int i = 0; i < cards.length; i++) {
            if (i < events.size()) {
                bindFeaturedCard(cards[i], events.get(i));
                cards[i].setVisibility(View.VISIBLE);
            } else {
                cards[i].setVisibility(View.GONE);
            }
        }
    }

    private void bindFeaturedCard(View card, Event event) {
        ((TextView) card.findViewById(R.id.featured_title)).setText(event.getTitle());
        ((TextView) card.findViewById(R.id.featured_subtitle)).setText(event.getSubtitle());
        ((TextView) card.findViewById(R.id.featured_date))
                .setText(formatDateRange(event.getDateStart(), event.getDateEnd()));
        ((TextView) card.findViewById(R.id.featured_location)).setText(event.getLocation());
        // image_url wiring comes once the admin upload flow exists — cards keep
        // the placeholder @drawable/logo illustration for now
    }

    private String formatDateRange(String start, String end) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat dayOnly = new SimpleDateFormat("d", Locale.getDefault());
            SimpleDateFormat full = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());

            Date startDate = input.parse(start);

            if (end == null || end.isEmpty() || end.equals(start)) {
                return full.format(startDate);
            }

            Date endDate = input.parse(end);
            return dayOnly.format(startDate) + " - " + full.format(endDate);

        } catch (Exception e) {
            return start;
        }
    }
}