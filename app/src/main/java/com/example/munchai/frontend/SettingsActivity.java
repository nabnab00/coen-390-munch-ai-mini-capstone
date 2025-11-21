package com.example.munchai.frontend;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.EditText;
import android.database.Cursor;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import com.example.munchai.R;
import com.example.munchai.backend.database.SettingsDatabaseHelper;

public class SettingsActivity extends AppCompatActivity
{
    private SettingsDatabaseHelper db;
    private Switch switchDarkMode;
    private EditText editCalories, editProtein, editCarbohydrates, editFats;
    private TextView saveSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settingspage);

        db = new SettingsDatabaseHelper(this);

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        switchDarkMode     = findViewById(R.id.switch_darkmode);
        editCalories       = findViewById(R.id.settings_calories);
        editProtein        = findViewById(R.id.settings_protein);
        editCarbohydrates  = findViewById(R.id.settings_carbohydrates);
        editFats           = findViewById(R.id.settings_fats);
        saveSettings       = findViewById(R.id.settings_save);

        loadSettings();

        saveSettings.setOnClickListener(v -> {
            try {
                int mode = switchDarkMode.isChecked() ? 1 : 0;

                Cursor cur = null;
                int cal = 2000, prot = 50, carbs = 250, fat = 70;
                try {
                    cur = db.getSettings();
                    if (cur != null && cur.moveToFirst()) {
                        cal   = cur.getInt(cur.getColumnIndexOrThrow("calorie_limit"));
                        prot  = cur.getInt(cur.getColumnIndexOrThrow("protein_limit"));
                        carbs = cur.getInt(cur.getColumnIndexOrThrow("carb_limit"));
                        fat   = cur.getInt(cur.getColumnIndexOrThrow("fat_limit"));
                    }
                } finally {
                    if (cur != null) cur.close();
                }

                String s = editCalories.getText().toString().trim();
                if (!s.isEmpty()) cal = Integer.parseInt(s);

                s = editProtein.getText().toString().trim();
                if (!s.isEmpty()) prot = Integer.parseInt(s);

                s = editCarbohydrates.getText().toString().trim();
                if (!s.isEmpty()) carbs = Integer.parseInt(s);

                s = editFats.getText().toString().trim();
                if (!s.isEmpty()) fat = Integer.parseInt(s);

                db.updateSettings(mode, cal, prot, carbs, fat);

                AppCompatDelegate.setDefaultNightMode(
                        mode == 1 ? AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO
                );

                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();

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
        Cursor cursor = null;
        try {
            cursor = db.getSettings();
            if (cursor != null && cursor.moveToFirst())
            {
                boolean dark = cursor.getInt(cursor.getColumnIndexOrThrow("dark_mode")) == 1;
                switchDarkMode.setChecked(dark);
                editCalories.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("calorie_limit"))));
                editProtein.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("protein_limit"))));
                editCarbohydrates.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("carb_limit"))));
                editFats.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow("fat_limit"))));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
