package com.example.munchai.frontend;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.Button;
import android.database.Cursor;
import android.widget.Toast;
import android.widget.ImageButton;
import android.content.Intent;

import com.example.munchai.R;
import com.example.munchai.backend.database.SettingsDatabaseHelper;

import android.content.Intent;
import com.example.munchai.backend.SessionManager;

public class SettingsActivity extends AppCompatActivity
{
    private SettingsDatabaseHelper db;
    private Switch switchDarkMode;
    private EditText editCalories, editProtein, editCarbohydrates, editFats;
    private Button saveSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settingspage);

        db = new SettingsDatabaseHelper(this);

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // closes SettingsActivity so you return to MainActivity
        });

        switchDarkMode = findViewById(R.id.switch_darkmode);
        editCalories = findViewById(R.id.settings_calories);
        editProtein = findViewById(R.id.settings_protein);
        editCarbohydrates = findViewById(R.id.settings_carbohydrates);
        editFats = findViewById(R.id.settings_fats);
        saveSettings = findViewById(R.id.settings_save);

        // Pre-fill current settings
        try {
            Cursor c = db.getSettings();
            if (c != null && c.moveToFirst()) {
                try {
                    int mode = c.getInt(c.getColumnIndexOrThrow("dark_mode"));
                    int cal = c.getInt(c.getColumnIndexOrThrow("calorie_limit"));
                    int prot = c.getInt(c.getColumnIndexOrThrow("protein_limit"));
                    int carbs = c.getInt(c.getColumnIndexOrThrow("carb_limit"));
                    int fat = c.getInt(c.getColumnIndexOrThrow("fat_limit"));

                    switchDarkMode.setChecked(mode == 1);
                    editCalories.setText(String.valueOf(cal));
                    editProtein.setText(String.valueOf(prot));
                    editCarbohydrates.setText(String.valueOf(carbs));
                    editFats.setText(String.valueOf(fat));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    c.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        loadSettings();

        Button logoutButton = findViewById(R.id.settings_logout);
        SessionManager sessionManager = new SessionManager(this);

        logoutButton.setOnClickListener(v -> {
            sessionManager.logout();

            Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SettingsActivity.this, StartActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finishAffinity();
        });

        saveSettings.setOnClickListener(v -> {
            try {
                int mode = switchDarkMode.isChecked() ? 1 : 0;

                // Read existing values from DB to use as defaults when fields are empty
                int cal, prot, carbs, fat;
                Cursor cur = db.getSettings();
                if (cur != null && cur.moveToFirst()) {
                    try {
                        cal = cur.getInt(cur.getColumnIndexOrThrow("calorie_limit"));
                        prot = cur.getInt(cur.getColumnIndexOrThrow("protein_limit"));
                        carbs = cur.getInt(cur.getColumnIndexOrThrow("carb_limit"));
                        fat = cur.getInt(cur.getColumnIndexOrThrow("fat_limit"));
                    } catch (Exception e) {
                        // fallback defaults if something goes wrong
                        e.printStackTrace();
                        cal = 2000; prot = 50; carbs = 250; fat = 70;
                    } finally {
                        cur.close();
                    }
                } else {
                    // No row found — use safe defaults
                    cal = 2000; prot = 50; carbs = 250; fat = 70;
                }

                // Override only fields the user filled in
                String s = editCalories.getText().toString().trim();
                if (!s.isEmpty()) cal = Integer.parseInt(s);

                s = editProtein.getText().toString().trim();
                if (!s.isEmpty()) prot = Integer.parseInt(s);

                s = editCarbohydrates.getText().toString().trim();
                if (!s.isEmpty()) carbs = Integer.parseInt(s);

                s = editFats.getText().toString().trim();
                if (!s.isEmpty()) fat = Integer.parseInt(s);

                // Save all (with unchanged fields preserved)
                db.updateSettings(mode, cal, prot, carbs, fat);

                android.util.Log.d("SettingsActivity", "Saved calorie_limit = " + cal);

                Toast.makeText(SettingsActivity.this, "Settings saved!", Toast.LENGTH_SHORT).show();

                // Close settings so MainActivity.onResume() reloads the latest values
                finish();

            } catch (NumberFormatException nfe) {
                Toast.makeText(SettingsActivity.this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(SettingsActivity.this, "Error saving settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSettings()
    {
        Cursor cursor = db.getSettings();

        if (cursor.moveToFirst())
        {
            switchDarkMode.setChecked(cursor.getInt(cursor.getColumnIndexOrThrow("dark_mode")) == 1);
            editCalories.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("calorie_limit"))));
            editProtein.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("protein_limit"))));
            editCarbohydrates.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("carb_limit"))));
            editFats.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("fat_limit"))));
        }

        cursor.close();
    }
}
