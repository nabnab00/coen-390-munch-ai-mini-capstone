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
    private EditText emailEt, passwordEt, confirmEt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registerpage);

        auth = FirebaseAuth.getInstance();  //handles authentication
        db = FirebaseFirestore.getInstance();   //handles database operations (writing user profiles)


        emailEt = findViewById(R.id.register_username);
        passwordEt = findViewById(R.id.register_password);
        confirmEt = findViewById(R.id.register_confirmpassword);
        Button registerBtn = findViewById(R.id.register_button);
        TextView toLogin = findViewById(R.id.to_login);

        registerBtn.setOnClickListener(v -> doRegister());  //register button triggers register method

        // to_login text sends user back to login page
        toLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish(); //finish activity so user can't go back to it
        });
    }

    private void doRegister() {
        // retrieve text from input fields and remove whitespace
        String email = emailEt.getText().toString().trim();
        String pwd = passwordEt.getText().toString();
        String cfm = confirmEt.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }   //check if email is valid


        if (TextUtils.isEmpty(pwd) || pwd.length() < 8) {
            Toast.makeText(this, "Use a password with 8+ chars", Toast.LENGTH_SHORT).show();
            return;
        }   //check if password is valid


        if (!pwd.equals(cfm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }   //check if passwords match

        //attempt registration (create Firebase user)
        auth.createUserWithEmailAndPassword(email, pwd).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this,
                        "Registration failed: " + (task.getException() != null ? task.getException().getMessage() : ""),
                        Toast.LENGTH_LONG).show();
                return;
            }

            //create user profile document in Firestore
            String uid = auth.getCurrentUser().getUid();   // get the new user's unique ID
            DocumentReference doc = db.collection("users").document(uid);   //create reference to a new document in the "users" collection with the UID as the document ID

            //create map to hold user's profile data
            Map<String, Object> profile = new HashMap<>();
            profile.put("email", email);    //store email in profile
            profile.put("createdAt", System.currentTimeMillis());   //store registration timestamp

            // write profile data to Firestore document
            doc.set(profile).addOnCompleteListener(aVoid -> {
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));   //go to login
                finish();
            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Saved auth but failed profile: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );  //listener called if Firestore profile creation fails
        });
    }
}