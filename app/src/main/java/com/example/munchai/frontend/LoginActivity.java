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

public class LoginActivity extends AppCompatActivity {

    private EditText emailEt, passwordEt;   //UI elements
    private FirebaseAuth auth;  //Firebase instance for authentication


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loginpage);

        auth = FirebaseAuth.getInstance();

        emailEt = findViewById(R.id.login_username);
        passwordEt = findViewById(R.id.login_password);
        Button loginBtn = findViewById(R.id.login_button);
        TextView toRegister = findViewById(R.id.to_register);

        loginBtn.setOnClickListener(v -> doLogin());    //login button click triggers login method
        toRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );  //register button sends user to register page
    }

    @Override
    protected void onStart() {
        super.onStart();
        //if user is already logged in, send them to main page
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }


    private void doLogin() {
        String email = emailEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }   //check if email is valid


        if (TextUtils.isEmpty(pwd)) {
            Toast.makeText(this, "Enter your password", Toast.LENGTH_SHORT).show();
            return;
        }   //check if password is empty

        //attempt login
        auth.signInWithEmailAndPassword(email, pwd).addOnCompleteListener(task -> {
            //check if sign in was successful
            if (task.isSuccessful()) {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this,
                        "Login failed: " + (task.getException() != null ? task.getException().getMessage() : ""),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}

