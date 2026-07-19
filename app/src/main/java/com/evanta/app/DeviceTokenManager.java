package com.evanta.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Registers this device's FCM token with Supabase so server-side functions can
 * push notifications to it.
 *
 * <p>The token is stored in a {@code device_tokens} table keyed by the token
 * itself (primary key) with the owning Firebase UID. Upserting on the token
 * means the same physical device never creates duplicate rows, and a token that
 * migrates to a new signed-in user simply has its uid updated.
 */
public final class DeviceTokenManager {

    private static final String TAG = "DeviceTokenManager";

    private DeviceTokenManager() {}

    /**
     * Fetches the current FCM token and registers it. Call this after a
     * successful login and on app start once a user is signed in.
     */
    public static void syncCurrentToken(Context context) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.w(TAG, "Fetching FCM token failed", task.getException());
                        return;
                    }
                    registerToken(context, task.getResult());
                });
    }

    /**
     * Upserts a known token for the currently signed-in user. No-ops when
     * signed out (there is no uid to attach the token to yet).
     */
    public static void registerToken(Context context, @NonNull String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        Map<String, Object> row = new HashMap<>();
        row.put("token", token);
        row.put("user_uid", user.getUid());
        row.put("platform", "android");
        row.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        api.upsertDeviceToken(row).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Device token registered");
                } else {
                    Log.w(TAG, "Token register HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Token register failed", t);
            }
        });
    }
}
