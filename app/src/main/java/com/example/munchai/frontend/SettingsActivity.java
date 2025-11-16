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
            finish(); // closes SettingsActivity so you return to MainActivity
        });

        switchDarkMode = findViewById(R.id.switch_darkmode);
        editCalories = findViewById(R.id.settings_calories);
        editProtein = findViewById(R.id.settings_protein);
        editCarbohydrates = findViewById(R.id.settings_carbohydrates);
        editFats = findViewById(R.id.settings_fats);
        saveSettings = findViewById(R.id.settings_save);

        // Prefill current settings
        try {
            Cursor c = db.getSettings();
            if (c != null && c.moveToFirst()) {
                switchDarkMode.setChecked(c.getInt(c.getColumnIndexOrThrow("dark_mode")) == 1);
                editCalories.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow("calorie_limit"))));
                editProtein.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow("protein_limit"))));
                editCarbohydrates.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow("carb_limit"))));
                editFats.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow("fat_limit"))));
                c.close();
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

                // Get existing DB values first
                Cursor cur = db.getSettings();
                int cal = 2000, prot = 50, carbs = 250, fat = 70;
                if (cur != null && cur.moveToFirst()) {
                    cal = cur.getInt(cur.getColumnIndexOrThrow("calorie_limit"));
                    prot = cur.getInt(cur.getColumnIndexOrThrow("protein_limit"));
                    carbs = cur.getInt(cur.getColumnIndexOrThrow("carb_limit"));
                    fat = cur.getInt(cur.getColumnIndexOrThrow("fat_limit"));
                    cur.close();
                }

                // Only override if field is filled in
                String s = editCalories.getText().toString().trim();
                if (!s.isEmpty()) cal = Integer.parseInt(s);

                s = editProtein.getText().toString().trim();
                if (!s.isEmpty()) prot = Integer.parseInt(s);

                s = editCarbohydrates.getText().toString().trim();
                if (!s.isEmpty()) carbs = Integer.parseInt(s);

                s = editFats.getText().toString().trim();
                if (!s.isEmpty()) fat = Integer.parseInt(s);

                // Save to DB
                db.updateSettings(mode, cal, prot, carbs, fat);
                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();

                // Go back to main and refresh values
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();

            } catch (NumberFormatException nfe) {
                Toast.makeText(this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error saving settings", Toast.LENGTH_SHORT).show();
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
