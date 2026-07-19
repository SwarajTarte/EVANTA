package com.evanta.app;

public class SupabaseConfig {
    public static final String BASE_URL =
            "https://npundzsqcoxsxtqyxosg.supabase.co/";

    // Anon key. This is ALWAYS sent as the `apikey` header (Supabase needs it to
    // route the request to this project). It is not a secret — anyone can extract
    // it from the APK. Real protection comes from Row-Level Security on the server.
    public static final String API_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5wdW5kenNxY294c3h0cXl4b3NnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE2MjMyMzUsImV4cCI6MjA5NzE5OTIzNX0.T-9dZIQ2LvOLgJTlCX-cDQgFfNZU5QG7nWxnUhxSXyk";

    // Master switch for the Firebase-token security path.
    //
    // false (default): every request authorizes with the anon key, exactly like
    //                  before. Safe to ship — the app behaves identically.
    // true:            when a Firebase user is signed in, requests authorize with
    //                  that user's Firebase ID token so Supabase RLS can identify
    //                  the caller via auth.jwt()->>'sub'. Falls back to the anon
    //                  key when signed out.
    //
    // FLIP TO true ONLY AFTER you have registered this Firebase project as a
    // Third-Party Auth provider in the Supabase dashboard AND verified the
    // pre-RLS check query. Before that, true would make every request 401.
    public static final boolean USE_FIREBASE_AUTH = true;
}
