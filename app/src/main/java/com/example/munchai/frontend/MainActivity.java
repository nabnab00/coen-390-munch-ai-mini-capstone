package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.munchai.R;
import com.example.munchai.backend.database.SettingsDatabaseHelper;
import com.example.munchai.model.CircularProgressView;
import com.google.android.material.button.MaterialButton;

import android.database.Cursor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AuthedActivity {

    // Circular Rings
    private CircularProgressView ringCalories;
    private CircularProgressView ringProtein;
    private CircularProgressView ringCarbs;
    private CircularProgressView ringFat;

    // Center Progress Text
    private TextView tvProgressCenter;

    // Stats
    private TextView tvGoalValue;
    private TextView tvConsumedValue;
    private TextView tvRemainingValue;

    // Date
    private TextView currentDateText;

    // Macros
    private TextView tvProteinValue;
    private TextView tvCarbsValue;
    private TextView tvFatsValue;

    // Buttons
    private MaterialButton btnLogFood;
    private MaterialButton btnHistory;
    private MaterialButton btnTest;
    private ImageButton btnSettings;

    // Goals (you can load these from settings)
    private int goalCaloriesValue = 2000;
    private int goalProteinValue = 150;
    private int goalCarbsValue = 200;
    private int goalFatValue = 65;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.mainpage);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        setupListeners();
        setCurrentDate();
    }

    private void initializeViews() {
        // Rings
        ringCalories = findViewById(R.id.ring_calories);
        ringProtein = findViewById(R.id.ring_protein);
        ringCarbs = findViewById(R.id.ring_carbs);
        ringFat = findViewById(R.id.ring_fat);

        // Center text
        tvProgressCenter = findViewById(R.id.tv_progress_center);

        // Stats
        tvGoalValue = findViewById(R.id.tv_goal_value);
        tvConsumedValue = findViewById(R.id.tv_consumed_value);
        tvRemainingValue = findViewById(R.id.tv_remaining_value);

        // Date
        currentDateText = findViewById(R.id.current_date);

        // Macros
        tvProteinValue = findViewById(R.id.tv_protein_value);
        tvCarbsValue = findViewById(R.id.tv_carbs_value);
        tvFatsValue = findViewById(R.id.tv_fats_value);

        // Buttons
        btnLogFood = findViewById(R.id.to_foodlog);
        btnHistory = findViewById(R.id.to_history);
        btnTest = findViewById(R.id.to_test);
        btnSettings = findViewById(R.id.to_settings);
    }

    private void setupListeners() {
        // Settings
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Log meal
        btnLogFood.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MealActivity.class);
            startActivity(intent);
        });

        // Food logs
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LogHistoryActivity.class);
            startActivity(intent);
        });

        // API test
        btnTest.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TestActivity.class);
            startActivity(intent);
        });
    }

    private void setCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        if (currentDateText != null) {
            currentDateText.setText(currentDate);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load settings for calorie goal
        SettingsDatabaseHelper settingsDb = new SettingsDatabaseHelper(this);
        Cursor settingsCursor = settingsDb.getSettings();

        if (settingsCursor != null && settingsCursor.moveToFirst()) {
            try {
                goalCaloriesValue = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow("calorie_limit")
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            settingsCursor.close();
        }

        updateUIWithDemoData();
    }

    private void updateUIWithDemoData() {
        // Demo percentages
        int calPercent = 65;
        int proteinPercent = 40;
        int carbsPercent = 80;
        int fatPercent = 30;

        // Calculate actual values based on percentages
        int consumedCalories = (goalCaloriesValue * calPercent) / 100;
        int consumedProtein = (goalProteinValue * proteinPercent) / 100;
        int consumedCarbs = (goalCarbsValue * carbsPercent) / 100;
        int consumedFat = (goalFatValue * fatPercent) / 100;

        // Update UI
        updateUI(consumedCalories, consumedProtein, consumedCarbs, consumedFat);
    }

    public void updateUI(int calories, int protein, int carbs, int fat) {
        // Calculate percentages
        int caloriesPercent = calculatePercentage(calories, goalCaloriesValue);
        int proteinPercent = calculatePercentage(protein, goalProteinValue);
        int carbsPercent = calculatePercentage(carbs, goalCarbsValue);
        int fatPercent = calculatePercentage(fat, goalFatValue);

        // Update center progress text
        if (tvProgressCenter != null) {
            tvProgressCenter.setText(caloriesPercent + "%");
        }

        // Update rings using setProgressPercent
        if (ringCalories != null) {
            ringCalories.setProgressPercent(caloriesPercent);
        }
        if (ringProtein != null) {
            ringProtein.setProgressPercent(proteinPercent);
        }
        if (ringCarbs != null) {
            ringCarbs.setProgressPercent(carbsPercent);
        }
        if (ringFat != null) {
            ringFat.setProgressPercent(fatPercent);
        }

        // Update stats
        if (tvGoalValue != null) {
            tvGoalValue.setText(String.valueOf(goalCaloriesValue));
        }
        if (tvConsumedValue != null) {
            tvConsumedValue.setText(String.valueOf(calories));
        }
        if (tvRemainingValue != null) {
            int remaining = Math.max(0, goalCaloriesValue - calories);
            tvRemainingValue.setText(String.valueOf(remaining));
        }

        // Update macro values and progress bars
        if (tvProteinValue != null) {
            tvProteinValue.setText(protein + " / " + goalProteinValue + "g");
        }

        if (tvCarbsValue != null) {
            tvCarbsValue.setText(carbs + " / " + goalCarbsValue + "g");
        }

        if (tvFatsValue != null) {
            tvFatsValue.setText(fat + " / " + goalFatValue + "g");
        }
    }

    private int calculatePercentage(int current, int goal) {
        if (goal <= 0) return 0;
        return Math.min(100, (int) ((current * 100.0f) / goal));
    }

    public void updateUIWithRealData(int totalCalories, int totalProtein, int totalCarbs, int totalFat) {
        updateUI(totalCalories, totalProtein, totalCarbs, totalFat);
    }
}
