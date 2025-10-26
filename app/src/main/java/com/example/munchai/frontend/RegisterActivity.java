package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EditText emailEt, idEt, passwordEt, confirmEt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registerpage);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailEt = findViewById(R.id.register_username);
        idEt = findViewById(R.id.register_id); // optional (not used in auth here)
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
        String sid = idEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString();
        String cfm = confirmEt.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(pwd) || pwd.length() < 8) {
            Toast.makeText(this, "Use a password with 8+ chars", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pwd.equals(cfm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, pwd).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this,
                        "Registration failed: " + (task.getException() != null ? task.getException().getMessage() : ""),
                        Toast.LENGTH_LONG).show();
                return;
            }

            //create user profile document in Firestore
            String uid = auth.getCurrentUser().getUid();
            DocumentReference doc = db.collection("users").document(uid);

            Map<String, Object> profile = new HashMap<>();
            profile.put("email", email);
            if (!TextUtils.isEmpty(sid)) profile.put("studentId", sid);
            profile.put("createdAt", System.currentTimeMillis());

            doc.set(profile).addOnCompleteListener(aVoid -> {
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                finish();
            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Saved auth but failed profile: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        });
    }
}