package com.example.munchai.backend;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager
{
    private static final String PREFS = "munchai_session";
    private static final String KEY_USER_ID = "user_id";
    private final SharedPreferences prefs;

    public SessionManager(Context ctx)
    {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setLoggedInUserId(int id)
    {
        prefs.edit().putInt(KEY_USER_ID, id).apply();
    }
    public int getLoggedInUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }
    public boolean isLoggedIn() {
        return getLoggedInUserId() > 0;
    }
    public void logout() {
        prefs.edit().remove(KEY_USER_ID).apply();
    }
}
