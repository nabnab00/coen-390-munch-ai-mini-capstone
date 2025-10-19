package com.example.munchai.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LogsStorage {
    private static final String PREFS = "munch_logs_prefs";
    private static final String KEY = "logs_json";

    private final SharedPreferences sp;

    public LogsStorage(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void add(JSONObject item) {
        try {
            JSONArray arr = getAll();
            arr.put(item);
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONArray getAll() {
        String s = sp.getString(KEY, "[]");
        try {
            return new JSONArray(s);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public void clear() {
        sp.edit().remove(KEY).apply();
    }
}

