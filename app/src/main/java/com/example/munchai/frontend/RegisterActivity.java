package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.InputFilter;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.munchai.R;
import com.example.munchai.backend.validation.ProfileValidation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EditText emailEt, passwordEt, confirmEt;
    private EditText fullNameEt, ageEt, heightEt;
    private CheckBox consentCheckbox;
    private ProfileValidation validator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registerpage);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        validator = new ProfileValidation(this);
        fullNameEt = findViewById(R.id.register_fullname);
        ageEt = findViewById(R.id.register_age);
        heightEt = findViewById(R.id.register_height);
        emailEt = findViewById(R.id.register_username);
        passwordEt = findViewById(R.id.register_password);
        confirmEt = findViewById(R.id.register_confirmpassword);
        consentCheckbox = findViewById(R.id.consent_checkbox);
        Button registerBtn = findViewById(R.id.register_button);
        TextView toLogin = findViewById(R.id.to_login);

        fullNameEt.setFilters(new InputFilter[] {
                (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        if (!Character.isLetter(c) && !Character.isSpaceChar(c) && c != '-' && c != '\'') {
                            return ""; // ignore invalid character
                        }
                    }
                    return null; // accept valid input
                }
        });

        registerBtn.setOnClickListener(v -> doRegister());
        toLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        consentCheckbox = findViewById(R.id.consent_checkbox);
        TextView consentText = findViewById(R.id.consent_text);

        // Set text part of the checkbox
        String normalText = "I agree to the ";
        String clickableText = "Terms and Conditions";

        SpannableString spannable = new SpannableString(normalText + clickableText);

        // Make only the "Terms and Conditions" clickable
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showConsentDialog();
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(ContextCompat.getColor(RegisterActivity.this, R.color.macro_protein)); // blue
                ds.setUnderlineText(true);
                ds.bgColor = android.graphics.Color.TRANSPARENT;
            }
        };

        // Apply clickable span only to the link
        spannable.setSpan(clickableSpan, normalText.length(),
                normalText.length() + clickableText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Set the color of the normal part to black
        spannable.setSpan(new android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(this, R.color.black)),
                0, normalText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        consentText.setText(spannable);
        consentText.setMovementMethod(LinkMovementMethod.getInstance());
        consentText.setHighlightColor(android.graphics.Color.TRANSPARENT);
    }

    private void doRegister() {
        String fullName = fullNameEt.getText().toString().trim();
        String age = ageEt.getText().toString().trim();
        String height = heightEt.getText().toString().trim();
        String email = emailEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString();
        String cfm = confirmEt.getText().toString();

        // Info validation
        if (fullName.isEmpty() || age.isEmpty() || height.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Consent check
        if (!consentCheckbox.isChecked()) {
            Toast.makeText(this, "You must agree to the Terms and Conditions.", Toast.LENGTH_SHORT).show();
            return;
        }

        // validation
        if (!validator.validateEmail(email)
                || !validator.validatePassword(pwd)
                || !validator.validateConfirmPassword(pwd, cfm)) {
            return;
        }

        // Convert age and height to numbers
        int ageVal;
        double heightVal;

        try {
            ageVal = Integer.parseInt(age);
            heightVal = Double.parseDouble(height);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Age and height must be numbers.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Firebase registration
        auth.createUserWithEmailAndPassword(email, pwd).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this,
                        "Registration failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""),
                        Toast.LENGTH_LONG).show();
                return;
            }

            // get user and uid
            FirebaseUser user = auth.getCurrentUser();
            String uid = user.getUid();

            // Update Firebase Auth display name
            UserProfileChangeRequest profileUpdates =
                    new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName)
                            .build();
            user.updateProfile(profileUpdates);

            // Save profile to Firestore
            DocumentReference doc = db.collection("users").document(uid);

            Map<String, Object> profile = new HashMap<>();
            profile.put("fullName", fullName);
            profile.put("age", ageVal);
            profile.put("height", heightVal);
            profile.put("email", email);
            profile.put("createdAt", System.currentTimeMillis());

            doc.set(profile).addOnCompleteListener(aVoid -> {
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Saved auth but failed profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show());
        });
    }

    private void showConsentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Terms and Conditions");

        // Create a scrollable container
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(50, 40, 50, 40);

        // Create the text view for consent text
        TextView consentTextView = new TextView(this);
        consentTextView.setText(R.string.consent_form_text);
        consentTextView.setTextColor(ContextCompat.getColor(RegisterActivity.this, R.color.black));
        consentTextView.setLineSpacing(1.2f, 1.2f);

        scrollView.addView(consentTextView); // add text view to scroll view
        builder.setView(scrollView);

        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
