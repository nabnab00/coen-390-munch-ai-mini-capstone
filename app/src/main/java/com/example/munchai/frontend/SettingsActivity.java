package com.example.munchai.frontend;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import com.example.munchai.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private Switch switchDarkMode;
    private EditText editCalories, editProtein, editCarbohydrates, editFats;
    private TextView saveSettings;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // 1) Apply current theme from SharedPreferences so Settings opens in the right mode
        SharedPreferences prefs = getSharedPreferences("munchai_prefs", MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.settingspage);

        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            uid = currentUser.getUid();
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        switchDarkMode      = findViewById(R.id.switch_darkmode);
        editCalories        = findViewById(R.id.settings_calories);
        editProtein         = findViewById(R.id.settings_protein);
        editCarbohydrates   = findViewById(R.id.settings_carbohydrates);
        editFats            = findViewById(R.id.settings_fats);
        saveSettings        = findViewById(R.id.settings_save);

        // Local cached state for switch until Firestore loads
        switchDarkMode.setChecked(darkMode);

        loadSettings();

        saveSettings.setOnClickListener(v -> saveSettingsToFirestore());
    }

    private void loadSettings() {
        // users/{uid}/settings/doc
        DocumentReference userSettingsRef = db.collection("users").document(uid)
                .collection("settings").document("doc");

        userSettingsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Boolean darkMode = document.getBoolean("dark_mode");
                    Long calorieLimit = document.getLong("calorie_limit");
                    Long proteinLimit = document.getLong("protein_limit");
                    Long carbLimit = document.getLong("carb_limit");
                    Long fatLimit = document.getLong("fat_limit");

                    if (darkMode != null) {
                        switchDarkMode.setChecked(darkMode);
                    }
                    if (calorieLimit != null) {
                        editCalories.setText(String.valueOf(calorieLimit));
                    }
                    if (proteinLimit != null) {
                        editProtein.setText(String.valueOf(proteinLimit));
                    }
                    if (carbLimit != null) {
                        editCarbohydrates.setText(String.valueOf(carbLimit));
                    }
                    if (fatLimit != null) {
                        editFats.setText(String.valueOf(fatLimit));
                    }
                }
            } else {
                Toast.makeText(SettingsActivity.this,
                        "Failed to load settings.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveSettingsToFirestore() {
        try {
            boolean darkMode = switchDarkMode.isChecked();

            Map<String, Object> settings = new HashMap<>();
            settings.put("dark_mode", darkMode);

            String calStr = editCalories.getText().toString().trim();
            if (!calStr.isEmpty()) settings.put("calorie_limit", Integer.parseInt(calStr));

            String protStr = editProtein.getText().toString().trim();
            if (!protStr.isEmpty()) settings.put("protein_limit", Integer.parseInt(protStr));

            String carbStr = editCarbohydrates.getText().toString().trim();
            if (!carbStr.isEmpty()) settings.put("carb_limit", Integer.parseInt(carbStr));

            String fatStr = editFats.getText().toString().trim();
            if (!fatStr.isEmpty()) settings.put("fat_limit", Integer.parseInt(fatStr));

            // users/{uid}/settings/doc
            DocumentReference userSettingsRef = db.collection("users").document(uid)
                    .collection("settings").document("doc");

            userSettingsRef.set(settings, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences prefs = getSharedPreferences("munchai_prefs", MODE_PRIVATE);
                        prefs.edit().putBoolean("dark_mode", darkMode).apply();

                        AppCompatDelegate.setDefaultNightMode(
                                darkMode ? AppCompatDelegate.MODE_NIGHT_YES
                                        : AppCompatDelegate.MODE_NIGHT_NO
                        );

                        Toast.makeText(SettingsActivity.this, "Settings saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(SettingsActivity.this,
                                "Error saving settings", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    });

        } catch (NumberFormatException nfe) {
            Toast.makeText(this,
                    "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
        }
    }
}

