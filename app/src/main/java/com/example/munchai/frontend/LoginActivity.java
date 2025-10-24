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
import com.example.munchai.backend.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameEt, passwordEt;
    private AppDatabaseHelper db;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loginpage);

        db = new AppDatabaseHelper(this);
        session = new SessionManager(this);

        usernameEt = findViewById(R.id.login_username);
        passwordEt = findViewById(R.id.login_password);
        Button loginBtn = findViewById(R.id.login_button);
        TextView toRegister = findViewById(R.id.to_register);

        loginBtn.setOnClickListener(v -> doLogin());
        toRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );
    }

    private void doLogin() {
        String u = usernameEt.getText().toString().trim();
        String p = passwordEt.getText().toString().trim();
        if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p)) {
            Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer userId = db.authenticate(u, p);
        if (userId != null) {
            new SessionManager(this).setLoggedInUserId(userId);
            Toast.makeText(this, "Welcome " + u + "!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
        }
    }
}

