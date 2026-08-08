package com.evanta.app;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Decides which value goes in the {@code Authorization: Bearer} header.
 *
 * <p>When {@link SupabaseConfig#USE_FIREBASE_AUTH} is false, or no user is signed
 * in, this returns the anon key and the app behaves exactly as it always has.
 *
 * <p>When the flag is true and a user is signed in, it returns that user's
 * Firebase ID token so Supabase can identify the caller in RLS policies via
 * {@code auth.jwt()->>'sub'} (the Firebase UID).
 *
 * <p>Token is refreshed asynchronously in the background — this method never
 * blocks, making it safe to call from OkHttp interceptors or any thread.
 */
public final class AuthTokens {

    private AuthTokens() {}

    // Refresh a bit before the real 1-hour expiry to avoid edge-of-expiry 401s.
    private static final long REFRESH_SKEW_MS = 5 * 60 * 1000L;

    private static volatile String cachedToken;
    private static volatile String cachedUid;
    private static volatile long cachedExpiryMs;

    /**
     * Returns the Bearer token for Supabase requests.
     * NEVER blocks — always returns immediately using cached value or anon key.
     * Safe to call from OkHttp interceptor threads.
     */
    public static String bearer() {
        if (!SupabaseConfig.USE_FIREBASE_AUTH) {
            return SupabaseConfig.API_KEY;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            clearCache();
            return SupabaseConfig.API_KEY;
        }

        String uid = user.getUid();
        long now = System.currentTimeMillis();

        // Return cached token if still valid
        String cached = cachedToken;
        if (cached != null
                && uid.equals(cachedUid)
                && now < cachedExpiryMs - REFRESH_SKEW_MS) {
            return cached;
        }

        // Cache is stale — kick off a background refresh, return best available now
        refreshTokenAsync(user);
        return cached != null ? cached : SupabaseConfig.API_KEY;
    }

    /**
     * Refreshes the Firebase ID token asynchronously in the background.
     * Safe to call from any thread including the main thread.
     */
    public static void refreshTokenAsync(@NonNull FirebaseUser user) {
        user.getIdToken(false)
            .addOnSuccessListener(result -> {
                String token = result.getToken();
                long expMs = result.getExpirationTimestamp() * 1000L;
                if (token != null) {
                    cachedToken = token;
                    cachedUid = user.getUid();
                    cachedExpiryMs = expMs;
                }
            })
            .addOnFailureListener(e ->
                android.util.Log.w("AuthTokens", "ID token refresh failed: " + e.getMessage())
            );
    }

    /**
     * Call this after sign-in to eagerly warm up the token cache
     * so the first API request has a valid token immediately.
     */
    public static void warmUp() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) refreshTokenAsync(user);
    }

    private static void clearCache() {
        cachedToken = null;
        cachedUid = null;
        cachedExpiryMs = 0L;
    }
}
