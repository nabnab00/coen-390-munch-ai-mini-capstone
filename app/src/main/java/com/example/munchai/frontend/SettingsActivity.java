package com.example.munchai.frontend;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

        switchDarkMode = findViewById(R.id.switch_darkmode);
        editCalories = findViewById(R.id.settings_calories);
        editProtein = findViewById(R.id.settings_protein);
        editCarbohydrates = findViewById(R.id.settings_carbohydrates);
        editFats = findViewById(R.id.settings_fats);
        saveSettings = findViewById(R.id.settings_save);

        loadSettings();

        saveSettings.setOnClickListener(v -> saveSettingsToFirestore());
    }

    private void loadSettings() {
        // CHANGED: Use path users/{uid}/settings/doc for settings
        DocumentReference userSettingsRef = db.collection("users").document(uid)
                .collection("settings").document("doc");
        userSettingsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Boolean darkMode = document.getBoolean("dark_mode");
                    Long calorieLimit = document.getLong("calorie_limit");
                    Long proteinLimit = document.getLong("protein_limit");
                    Long carbLimit = document.getLong("carb_limit");
                    Long fatLimit = document.getLong("fat_limit");

                    if (darkMode != null) switchDarkMode.setChecked(darkMode);
                    if (calorieLimit != null) editCalories.setText(String.valueOf(calorieLimit));
                    if (proteinLimit != null) editProtein.setText(String.valueOf(proteinLimit));
                    if (carbLimit != null) editCarbohydrates.setText(String.valueOf(carbLimit));
                    if (fatLimit != null) editFats.setText(String.valueOf(fatLimit));
                }
            } else {
                Toast.makeText(SettingsActivity.this, "Failed to load settings.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveSettingsToFirestore() {
        try {
            Map<String, Object> settings = new HashMap<>();
            settings.put("dark_mode", switchDarkMode.isChecked());

            String calStr = editCalories.getText().toString().trim();
            if (!calStr.isEmpty()) settings.put("calorie_limit", Integer.parseInt(calStr));

            String protStr = editProtein.getText().toString().trim();
            if (!protStr.isEmpty()) settings.put("protein_limit", Integer.parseInt(protStr));

            String carbStr = editCarbohydrates.getText().toString().trim();
            if (!carbStr.isEmpty()) settings.put("carb_limit", Integer.parseInt(carbStr));

            String fatStr = editFats.getText().toString().trim();
            if (!fatStr.isEmpty()) settings.put("fat_limit", Integer.parseInt(fatStr));

            // CHANGED: Use path users/{uid}/settings/doc for settings
            DocumentReference userSettingsRef = db.collection("users").document(uid)
                    .collection("settings").document("doc");
            userSettingsRef.set(settings, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(SettingsActivity.this, "Settings saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(SettingsActivity.this, "Error saving settings", Toast.LENGTH_SHORT).show();
                        // Log the detailed error to help with debugging
                        e.printStackTrace();
                    });

        } catch (NumberFormatException nfe) {
            Toast.makeText(this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
        }
    }
}
