package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.example.munchai.backend.validation.ProfileValidation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EditText emailEt, passwordEt, confirmEt;
    private EditText fullNameEt, ageEt, heightEt, weightEt;
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
        weightEt = findViewById(R.id.register_weight); // Initialize weightEt
        emailEt = findViewById(R.id.register_username);
        passwordEt = findViewById(R.id.register_password);
        confirmEt = findViewById(R.id.register_confirmpassword);
        Button registerBtn = findViewById(R.id.register_button);
        TextView toLogin = findViewById(R.id.to_login);

        registerBtn.setOnClickListener(v -> doRegister());
        toLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void doRegister() {
        String fullName = fullNameEt.getText().toString().trim();
        String age = ageEt.getText().toString().trim();
        String height = heightEt.getText().toString().trim();
        String weight = weightEt.getText().toString().trim(); // Get weight string
        String email = emailEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString();
        String cfm = confirmEt.getText().toString();

        // Info validation
        if (fullName.isEmpty() || age.isEmpty() || height.isEmpty() || weight.isEmpty()) { // Added weight validation
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // validation
        if (!validator.validateEmail(email)
                || !validator.validatePassword(pwd)
                || !validator.validateConfirmPassword(pwd, cfm)) {
            return;
        }

        // Convert age, height and weight to numbers
        int ageVal;
        double heightVal;
        double weightVal;

        try {
            ageVal = Integer.parseInt(age);
            heightVal = Double.parseDouble(height);
            weightVal = Double.parseDouble(weight); // Parse weight
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Age, height, and weight must be numbers.", Toast.LENGTH_SHORT).show();
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
                // Now, log the initial weight in the subcollection
                Map<String, Object> weightLog = new HashMap<>();
                weightLog.put("weight", weightVal);
                weightLog.put("date", new Date()); // Use server timestamp for consistency

                doc.collection("personal_weight_logs").add(weightLog)
                        .addOnSuccessListener(documentReference -> {
                            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            // Profile was saved, but weight log failed.
                            Toast.makeText(this, "Account created, but failed to log initial weight: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        });

            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Saved auth but failed profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show());
        });
    }
}
