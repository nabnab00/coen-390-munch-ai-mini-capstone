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

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EditText emailEt, passwordEt, confirmEt;
    private ProfileValidation validator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registerpage);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        validator = new ProfileValidation(this);
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
        String email = emailEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString();
        String cfm = confirmEt.getText().toString();

        // validation
        if (!validator.validateEmail(email)
                || !validator.validatePassword(pwd)
                || !validator.validateConfirmPassword(pwd, cfm)) {
            return;
        }

        // ✅ firebase registration
        auth.createUserWithEmailAndPassword(email, pwd).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this,
                        "Registration failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""),
                        Toast.LENGTH_LONG).show();
                return;
            }

            // create Firestore profile
            String uid = auth.getCurrentUser().getUid();
            DocumentReference doc = db.collection("users").document(uid);

            Map<String, Object> profile = new HashMap<>();
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
}
