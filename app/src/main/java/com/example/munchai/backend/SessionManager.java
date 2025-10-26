package com.example.munchai.backend;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SessionManager {
    private final FirebaseAuth auth;

    public SessionManager(Context ctx) {
        auth = FirebaseAuth.getInstance();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return (u != null) ? u.getUid() : null;
    }

    public void logout() {
        auth.signOut();
    }
}

