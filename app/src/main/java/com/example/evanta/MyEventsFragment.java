package com.example.evanta;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyEventsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar loader;
    private View emptyState;
    private TextView tabUpcoming, tabCompleted, tabCertificates;

    private MyEventsAdapter adapter;
    private final List<MyEventItem> allMyEvents = new ArrayList<>();
    private final List<MyEventItem> myEventsList = new ArrayList<>();
    private Filter selectedFilter = Filter.UPCOMING;

    private enum Filter {
        UPCOMING,
        COMPLETED,
        CERTIFICATES
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_myevents, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.my_events_recycler);
        loader = view.findViewById(R.id.my_events_loader);
        emptyState = view.findViewById(R.id.my_events_empty);
        tabUpcoming = view.findViewById(R.id.tab_upcoming);
        tabCompleted = view.findViewById(R.id.tab_completed);
        tabCertificates = view.findViewById(R.id.tab_certificates);

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new MyEventsAdapter(myEventsList);
        recyclerView.setAdapter(adapter);

        tabUpcoming.setOnClickListener(v -> selectFilter(Filter.UPCOMING));
        tabCompleted.setOnClickListener(v -> selectFilter(Filter.COMPLETED));
        tabCertificates.setOnClickListener(v -> selectFilter(Filter.CERTIFICATES));

        updateTabs();
        bindFromPrefetchCache();

        if (!PrefetchCache.hasFreshMyEventsData()) {
            fetchMyEvents();
        }
    }

    private void fetchMyEvents() {
        User user = UserCache.get(requireContext());
        if (user == null) {
            showEmptyState(true);
            return;
        }

        showLoading(true);
        RegistrationRepository registrationRepository = new RegistrationRepository();

        registrationRepository.getRegistrationsForUser(user.getUid()).enqueue(new Callback<List<Registration>>() {
            @Override
            public void onResponse(@NonNull Call<List<Registration>> call, @NonNull Response<List<Registration>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<Registration> registrations = response.body();
                    fetchMatchingEvents(registrations);
                } else {
                    PrefetchCache.setMyEventsData(new ArrayList<>(), new ArrayList<>());
                    showEmptyState(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Registration>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                showEmptyState(true);
            }
        });
    }

    private void fetchMatchingEvents(List<Registration> registrations) {
        StringBuilder idsBuilder = new StringBuilder("in.(");
        int added = 0;

        for (Registration reg : registrations) {
            if (reg.getEventId() == null) continue;
            if (added > 0) idsBuilder.append(",");
            idsBuilder.append(reg.getEventId());
            added++;
        }
        idsBuilder.append(")");

        if (added == 0) {
            PrefetchCache.setMyEventsData(registrations, new ArrayList<>());
            allMyEvents.clear();
            applyFilter();
            return;
        }

        EventRepository eventRepository = new EventRepository();
        eventRepository.getEventsByIds(idsBuilder.toString()).enqueue(new Callback<List<Event>>() {
            @Override
            public void onResponse(@NonNull Call<List<Event>> call, @NonNull Response<List<Event>> response) {
                if (!isAdded()) return;

                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    PrefetchCache.setMyEventsData(registrations, response.body());
                    bindFromPrefetchCache();
                } else {
                    showEmptyState(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Event>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                showEmptyState(true);
            }
        });
    }

    private void showLoading(boolean isLoading) {
        loader.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        if (isLoading) {
            emptyState.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean isEmpty) {
        showLoading(false);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void selectFilter(Filter filter) {
        selectedFilter = filter;
        updateTabs();
        applyFilter();
    }

    private void updateTabs() {
        setTabState(tabUpcoming, selectedFilter == Filter.UPCOMING);
        setTabState(tabCompleted, selectedFilter == Filter.COMPLETED);
        setTabState(tabCertificates, selectedFilter == Filter.CERTIFICATES);
    }

    private void setTabState(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        tab.setTextColor(selected ? 0xFFFFFFFF : 0xFF8A93A6);
    }

    private void applyFilter() {
        myEventsList.clear();
        for (MyEventItem item : allMyEvents) {
            boolean include;
            if (selectedFilter == Filter.CERTIFICATES) {
                String certificateUrl = item.getRegistration().getCertificateUrl();
                include = certificateUrl != null && !certificateUrl.trim().isEmpty();
            } else if (selectedFilter == Filter.COMPLETED) {
                include = isCompleted(item.getEvent());
            } else {
                include = !isCompleted(item.getEvent());
            }

            if (include) {
                myEventsList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        showEmptyState(myEventsList.isEmpty());
    }

    private boolean bindFromPrefetchCache() {
        // Prefer fresh data, but fall back to stale so the grid paints instantly
        // on a cold start; a background refresh updates it silently afterwards.
        List<MyEventItem> cached = PrefetchCache.getMyEventItemsFresh();
        if (cached == null) {
            cached = PrefetchCache.getMyEventItemsStale();
        }
        if (cached == null) return false;

        allMyEvents.clear();
        allMyEvents.addAll(cached);
        applyFilter();
        return true;
    }

    private boolean isCompleted(Event event) {
        String dateValue = event.getDateEnd() != null && !event.getDateEnd().isEmpty()
                ? event.getDateEnd()
                : event.getDateStart();

        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date eventDate = input.parse(dateValue);
            Date today = input.parse(input.format(new Date()));
            return eventDate != null && eventDate.before(today);
        } catch (Exception e) {
            return false;
        }
    }
}