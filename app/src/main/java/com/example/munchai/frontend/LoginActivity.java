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

public class LoginActivity extends AppCompatActivity {
    private EditText emailEt, passwordEt;
    private FirebaseAuth auth;
    private ProfileValidation validator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loginpage);

        auth = FirebaseAuth.getInstance();
        validator = new ProfileValidation(this);
        emailEt = findViewById(R.id.login_username);
        passwordEt = findViewById(R.id.login_password);
        Button loginBtn = findViewById(R.id.login_button);
        TextView toRegister = findViewById(R.id.to_register);

        loginBtn.setOnClickListener(v -> doLogin());
        toRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }

    private void doLogin() {
        String email = emailEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString().trim();

        // validation with firebase
        auth.signInWithEmailAndPassword(email, pwd).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
            else {
                Toast.makeText(this,
                        "Login failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
