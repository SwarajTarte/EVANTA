package com.evanta.app;

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

        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_UID, user.getUid())
                .putString(KEY_NAME, user.getName())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_WHATSAPP, user.getWhatsappno())
                .putString(KEY_PHOTO_URL, user.getPhotoUrl())
                .putString("college_id", user.getCollegeId())
                .putString("branch", user.getBranch())
                .putString("college_name", user.getCollegeName())
                .putString("role", user.getRole());

        if (user.getCollegeName() != null && !user.getCollegeName().isEmpty()) {
            editor.putString("college_name_resolved", user.getCollegeName());
        }

        editor.apply();
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

        user.setPhotoUrl(prefs.getString(KEY_PHOTO_URL, null));
        user.setCollegeId(prefs.getString("college_id", null));
        user.setBranch(prefs.getString("branch", null));
        user.setRole(prefs.getString("role", "student"));

        String collegeName = prefs.getString("college_name", null);
        String collegeNameResolved = prefs.getString("college_name_resolved", null);
        user.setCollegeName(collegeNameResolved != null ? collegeNameResolved : collegeName);

        return user;
    }

    public static void setCollegeNameResolved(Context context, String name) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("college_name_resolved", name)
                .apply();
    }

    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }
}