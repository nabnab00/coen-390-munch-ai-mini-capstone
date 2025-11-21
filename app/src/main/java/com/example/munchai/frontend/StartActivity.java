package com.example.munchai.frontend;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.example.munchai.backend.database.SettingsDatabaseHelper;

public class StartActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        SettingsDatabaseHelper db = new SettingsDatabaseHelper(this);
        boolean dark = false;
        Cursor c = null;
        try {
            c = db.getSettings();
            if (c != null && c.moveToFirst()) {
                dark = c.getInt(c.getColumnIndexOrThrow("dark_mode")) == 1;
            }
        } catch (Exception e) {
        } finally {
            if (c != null) c.close();
        }
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
