package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.backend.SessionManager;

public abstract class AuthedActivity extends AppCompatActivity {

    @Override
    protected void onStart() {
        super.onStart();
        SessionManager sm = new SessionManager(this);
        if (!sm.isLoggedIn()) {
            // User is not logged in → force to Login screen and clear back stack
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }
    }
}
