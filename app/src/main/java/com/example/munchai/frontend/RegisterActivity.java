package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.example.munchai.backend.AppDatabaseHelper;

public class RegisterActivity extends AppCompatActivity {

    private EditText usernameEt, idEt, passwordEt, confirmEt;
    private AppDatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registerpage);

        db = new AppDatabaseHelper(this);

        usernameEt = findViewById(R.id.register_username);
        idEt       = findViewById(R.id.register_id); // optional (not used in auth here)
        passwordEt = findViewById(R.id.register_password);
        confirmEt  = findViewById(R.id.register_confirmpassword);
        Button registerBtn = findViewById(R.id.register_button);
        TextView toLogin = findViewById(R.id.to_login);

        registerBtn.setOnClickListener(v -> doRegister());
        toLogin.setOnClickListener(v ->
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class))
        );
    }

    private void doRegister() {
        String u = usernameEt.getText().toString().trim();
        String p = passwordEt.getText().toString().trim();
        String c = confirmEt.getText().toString().trim();

        if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p) || TextUtils.isEmpty(c)) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!p.equals(c)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (db.usernameExists(u)) {
            Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show();
            return;
        }
        long id = db.createUser(u, p);
        if (id > 0) {
            Toast.makeText(this, "Registered! Please log in.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Registration failed", Toast.LENGTH_SHORT).show();
        }
    }
}

