package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.munchai.R;
import android.database.Cursor;
import com.example.munchai.backend.database.SettingsDatabaseHelper;
import com.example.munchai.model.CircularProgressView; // if not already present
import android.widget.TextView;

public class MainActivity extends AuthedActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.mainpage);

        // UI refs
        ImageButton toSetting = findViewById(R.id.to_settings);
        Button toFoodlog = findViewById(R.id.to_foodlog);
        Button toHistory = findViewById(R.id.to_history);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //settings
        toSetting.setOnClickListener(v ->
        {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        //log meal
        toFoodlog.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MealActivity.class);
            startActivity(intent);
        });

        //food logs
        toHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Get updated settings (like calorie limit)
        SettingsDatabaseHelper settingsDb = new SettingsDatabaseHelper(this);
        Cursor settingsCursor = settingsDb.getSettings();

        int targetCalories = 2000;  // Default value
        int targetProtein = 100;
        int targetCarbs = 250;
        int targetFat = 70;

        if (settingsCursor != null && settingsCursor.moveToFirst()) {
            try {
                targetCalories = settingsCursor.getInt(settingsCursor.getColumnIndexOrThrow("calorie_limit"));
                targetProtein = settingsCursor.getInt(settingsCursor.getColumnIndexOrThrow("protein_limit"));
                targetCarbs = settingsCursor.getInt(settingsCursor.getColumnIndexOrThrow("carb_limit"));
                targetFat = settingsCursor.getInt(settingsCursor.getColumnIndexOrThrow("fat_limit"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            settingsCursor.close();
        }

        // For now, we don't track daily calories — just display 0 / target
        int todaysCalories = 0;

        // Update the circular progress and text on the main page
        CircularProgressView calorieRing = findViewById(R.id.calorie_progress_ring);
        TextView dailyCaloriesText = findViewById(R.id.dailycalories);

        if (calorieRing != null) {
            calorieRing.setMax(targetCalories);
            calorieRing.setProgress(todaysCalories);
        }

        if (dailyCaloriesText != null) {
            dailyCaloriesText.setText(todaysCalories + "/" + targetCalories);
        }
    }

}
