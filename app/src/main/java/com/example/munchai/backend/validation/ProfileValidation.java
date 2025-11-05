package com.example.munchai.backend.validation;

import android.content.Context;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

public class ProfileValidation
{
    private final Context context;

    public ProfileValidation(Context context) {
        this.context = context;
    }

    // email
    public boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(context, "Email cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // password
    public boolean validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 8) {
            Toast.makeText(context, "Use a password with at least 8 characters", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // confirm password
    public boolean validateConfirmPassword(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
