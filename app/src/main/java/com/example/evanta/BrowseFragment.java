package com.example.evanta;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BrowseFragment extends Fragment {

    private TextView sectionLabel;
    private TextView stateTitle;
    private TextView stateMessage;
    private ProgressBar loader;
    private View stateView;
    private RecyclerView eventsRecycler;
    private EventRowAdapter eventAdapter;
    private final List<Event> currentEvents = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private String selectedCategory = null; // null = All
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_browse, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sectionLabel = view.findViewById(R.id.section_label);
        stateTitle = view.findViewById(R.id.browse_state_title);
        stateMessage = view.findViewById(R.id.browse_state_message);
        loader = view.findViewById(R.id.browse_loader);
        stateView = view.findViewById(R.id.browse_state);
        EditText searchInput = view.findViewById(R.id.browse_search);
        eventsRecycler = view.findViewById(R.id.events_recycler);
        RecyclerView categoryRecycler = view.findViewById(R.id.category_recycler);

        eventAdapter = new EventRowAdapter(currentEvents);
        eventsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        eventsRecycler.setAdapter(eventAdapter);

        categoryRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryRecycler.setAdapter(new CategoryAdapter(buildCategories(), category -> {
            selectedCategory = category.getValue();
            sectionLabel.setText(category.getValue() == null ? "All Events" : category.getLabel() + " Events");
            fetchEvents();
        }));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                scheduleSearch();
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        // Load pre-fetched browse events from cache immediately for lag-free rendering
        List<Event> cached = EventCache.getAllEvents();
        if (cached != null) {
            currentEvents.clear();
            currentEvents.addAll(cached);
            eventAdapter.notifyDataSetChanged();
        }

        fetchEvents();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
    }

    private List<Category> buildCategories() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("All", null, R.drawable.ic_grid, 0xFF7C4DFF));
        list.add(new Category("Tech", "Tech", R.drawable.ic_code, 0xFF2F80ED));
        list.add(new Category("Cultural", "Cultural", R.drawable.ic_mood, 0xFFD6449C));
        list.add(new Category("Sports", "Sports", R.drawable.ic_trophy, 0xFF27AE60));
        list.add(new Category("Workshop", "Workshop", R.drawable.ic_book, 0xFFF2994A));
        list.add(new Category("Music", "Music", R.drawable.ic_music_note, 0xFF9B59F6));
        return list;
    }

    private void fetchEvents() {

        EventRepository eventRepository = new EventRepository();
        boolean isUnfilteredFetch = selectedCategory == null && searchQuery.isEmpty();
        showLoading(currentEvents.isEmpty());

        eventRepository.getEvents(selectedCategory, searchQuery)
                .enqueue(new Callback<List<Event>>() {
                    @Override
                    public void onResponse(Call<List<Event>> call, Response<List<Event>> response) {

                        if (!isAdded()) return;

                        currentEvents.clear();
                        if (response.isSuccessful() && response.body() != null) {
                            currentEvents.addAll(response.body());
                            // Update cache if we fetched the full list (unfiltered)
                            if (isUnfilteredFetch) {
                                EventCache.setAllEvents(response.body());
                            }
                            showEmptyState(currentEvents.isEmpty(), false);
                        } else {
                            showEmptyState(true, true);
                        }
                        eventAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(Call<List<Event>> call, Throwable t) {
                        // keep whatever is currently shown
                        if (!isAdded()) return;
                        showEmptyState(currentEvents.isEmpty(), true);
                    }
                });
    }

    private void scheduleSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = this::fetchEvents;
        searchHandler.postDelayed(pendingSearch, 350);
    }

    private void showLoading(boolean isLoading) {
        loader.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading) {
            stateView.setVisibility(View.GONE);
            eventsRecycler.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean showState, boolean isError) {
        loader.setVisibility(View.GONE);
        stateView.setVisibility(showState ? View.VISIBLE : View.GONE);
        eventsRecycler.setVisibility(showState ? View.GONE : View.VISIBLE);

        if (showState) {
            stateTitle.setText(isError ? "Could Not Load Events" : "No Events Found");
            stateMessage.setText(isError
                    ? "Check your connection and try again."
                    : "Try a different search or category.");
        }
    }
}
