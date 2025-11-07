package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Button;
import android.database.Cursor;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.munchai.R;
import com.example.munchai.backend.database.SettingsDatabaseHelper;
import com.example.munchai.model.CircularProgressView;
import com.example.munchai.backend.database.AppDatabaseHelper;

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
        Button toTest = findViewById(R.id.to_test);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Load calorie target
        SettingsDatabaseHelper settingsDb = new SettingsDatabaseHelper(this);
        Cursor settingsCursor = settingsDb.getSettings();
        int targetCalories = 2000; // fallback
        if (settingsCursor != null) {
            if (settingsCursor.moveToFirst()) {
                try {
                    targetCalories = settingsCursor.getInt(settingsCursor.getColumnIndexOrThrow("calorie_limit"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            settingsCursor.close();
        }

        CircularProgressView calorieRing = findViewById(R.id.calorie_progress_ring);
        calorieRing.setMax(targetCalories);
        calorieRing.setProgress(0);

        TextView dailyCaloriesText = findViewById(R.id.dailycalories);
        dailyCaloriesText.setText("0/" + targetCalories);

        // Load today's calories and update display
        AppDatabaseHelper dbHelper = new AppDatabaseHelper(this);
        double todaysCalories = dbHelper.getTodaysCalories();

        calorieRing.setProgress((int) todaysCalories);
        dailyCaloriesText.setText((int) todaysCalories + "/" + targetCalories);

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
            Intent intent = new Intent(MainActivity.this, LogHistoryActivity.class);
            startActivity(intent);
        });

        //API test
        toTest.setOnClickListener(v ->
        {
            Intent intent = new Intent(MainActivity.this, TestActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        AppDatabaseHelper dbHelper = new AppDatabaseHelper(this);

        // Get today's total calories
        double todaysCalories = dbHelper.getTodaysCalories();

        // Get calorie target
        SettingsDatabaseHelper settingsDb = new SettingsDatabaseHelper(this);
        Cursor settingsCursor = settingsDb.getSettings();
        int targetCalories = 2000;
        if (settingsCursor != null && settingsCursor.moveToFirst()) {
            try {
                targetCalories = settingsCursor.getInt(settingsCursor.getColumnIndexOrThrow("calorie_limit"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            settingsCursor.close();
        }

        // Update the UI
        CircularProgressView calorieRing = findViewById(R.id.calorie_progress_ring);
        TextView dailyCaloriesText = findViewById(R.id.dailycalories);

        calorieRing.setMax(targetCalories);
        calorieRing.setProgress((int) todaysCalories);
        dailyCaloriesText.setText((int) todaysCalories + "/" + targetCalories);
    }
}
