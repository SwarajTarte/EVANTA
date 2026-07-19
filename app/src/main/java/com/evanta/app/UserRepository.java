package com.evanta.app;

import java.util.List;
import java.util.Map;

import retrofit2.Call;

public class UserRepository {

    private final SupabaseApi api;

    public UserRepository() {
        api = RetrofitClient.getClient().create(SupabaseApi.class);
    }

    public Call<List<User>> getUserByUid(String uid) {
        return api.getUserByUid("eq." + uid);
    }

    public Call<Void> upsertUser(User user) {
        return api.upsertUser(user);
    }

    public Call<Void> updateUser(String uid, Map<String, Object> fields) {
        return api.updateUser("eq." + uid, fields);
    }
}
