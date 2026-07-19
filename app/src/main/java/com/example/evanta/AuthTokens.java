package com.example.evanta;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

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
 * <p>Firebase ID tokens live ~1 hour. We cache the last minted token and only ask
 * Firebase for a new one when ours is near expiry, so the blocking call almost
 * never actually hits the network.
 */
public final class AuthTokens {

    private AuthTokens() {}

    // Refresh a bit before the real 1-hour expiry to avoid edge-of-expiry 401s.
    private static final long REFRESH_SKEW_MS = 5 * 60 * 1000L;

    private static volatile String cachedToken;
    private static volatile String cachedUid;
    private static volatile long cachedExpiryMs;

    /**
     * The Bearer token to authorize a Supabase request with, right now.
     * Never returns null — falls back to the anon key.
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

        String token = idTokenFor(user);
        return token != null ? token : SupabaseConfig.API_KEY;
    }

    private static String idTokenFor(@NonNull FirebaseUser user) {
        String uid = user.getUid();
        long now = System.currentTimeMillis();

        String cached = cachedToken;
        if (cached != null
                && uid.equals(cachedUid)
                && now < cachedExpiryMs - REFRESH_SKEW_MS) {
            return cached;
        }

        try {
            // false = don't force-refresh; Firebase returns a cached valid token
            // when it has one, so this is usually instant and offline-safe.
            GetTokenResult result = Tasks.await(user.getIdToken(false));
            String token = result.getToken();
            long expMs = result.getExpirationTimestamp() * 1000L;
            if (token != null) {
                cachedToken = token;
                cachedUid = uid;
                cachedExpiryMs = expMs;
                return token;
            }
        } catch (Exception e) {
            // Network hiccup, cancelled task, etc. Fall back to the anon key so
            // the request still goes out rather than crashing.
            android.util.Log.w("AuthTokens", "ID token fetch failed: " + e.getMessage());
        }
        return null;
    }

    private static void clearCache() {
        cachedToken = null;
        cachedUid = null;
        cachedExpiryMs = 0L;
    }
}
