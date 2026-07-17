package com.example.evanta;

import android.content.Context;
import android.content.SharedPreferences;

public class UserCache {

    private static final String PREFS_NAME = "user_cache";

    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_WHATSAPP = "whatsapp";
    private static final String KEY_PHOTO_URL = "photo_url";
    private static final String KEY_UID = "uid";

    private UserCache() {}

    public static void set(Context context, User user) {

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_UID, user.getUid())
                .putString(KEY_NAME, user.getName())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_WHATSAPP, user.getWhatsappno())
                .putString(KEY_PHOTO_URL, user.getPhotoUrl())
                .apply();
    }

    public static User get(Context context) {

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String uid = prefs.getString(KEY_UID, null);
        if (uid == null) {
            return null;
        }

        User user = new User(
                uid,
                prefs.getString(KEY_NAME, ""),
                prefs.getString(KEY_EMAIL, ""),
                prefs.getString(KEY_WHATSAPP, "")
        );

        // photo_url isn't part of the constructor since it was added later —
        // set it separately via the same mechanism Gson uses (reflection isn't
        // needed here, we just need a setter).
        user.setPhotoUrl(prefs.getString(KEY_PHOTO_URL, null));

        return user;
    }

    public static void clear(Context context) {

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit().clear().apply();
    }
}