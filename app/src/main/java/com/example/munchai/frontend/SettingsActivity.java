package com.example.munchai.frontend;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.Button;
import android.database.Cursor;
import android.widget.Toast;

import com.example.munchai.R;
import com.example.munchai.backend.DatabaseHelper;

public class SettingsActivity extends AppCompatActivity
{
    private DatabaseHelper db;
    private Switch switchDarkMode;
    private EditText editCalories, editProtein, editCarbohydrates, editFats;
    private Button saveSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settingspage);

        db = new DatabaseHelper(this);

        switchDarkMode = findViewById(R.id.switch_darkmode);
        editCalories = findViewById(R.id.settings_calories);
        editProtein = findViewById(R.id.settings_protein);
        editCarbohydrates = findViewById(R.id.settings_carbohydrates);
        editFats = findViewById(R.id.settings_fats);
        saveSettings = findViewById(R.id.settings_save);

        loadSettings();

        saveSettings.setOnClickListener(v ->
        {
            int mode = switchDarkMode.isChecked() ? 1 : 0;
            int cal = Integer.parseInt(editCalories.getText().toString());
            int prot = Integer.parseInt(editProtein.getText().toString());
            int carbs = Integer.parseInt(editCarbohydrates.getText().toString());
            int fat = Integer.parseInt(editFats.getText().toString());

            db.updateSettings(mode, cal, prot, carbs, fat);
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
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
