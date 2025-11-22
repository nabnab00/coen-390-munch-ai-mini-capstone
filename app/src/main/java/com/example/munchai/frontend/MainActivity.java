package com.example.munchai.frontend;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.munchai.R;
import com.example.munchai.backend.SessionManager;
import com.example.munchai.backend.database.SettingsDatabaseHelper;
import com.example.munchai.model.CircularProgressView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AuthedActivity {

    // Circular Rings
    private CircularProgressView ringCalories;   // big main ring only

    // Macro progress bars 
    private ProgressBar pbProtein;
    private ProgressBar pbCarbs;
    private ProgressBar pbFat;

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
    private ImageButton btnSettings;
    private Button btnLogFood;
    private Button btnHistory;
    private Button btnProfile;

    // Goals (loaded from settings)
    private int goalCaloriesValue = 2000;
    private int goalProteinValue = 100;
    private int goalCarbsValue   = 250;
    private int goalFatValue     = 70;

    private final SimpleDateFormat isoUtc =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

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

        Button logoutButton = findViewById(R.id.logout_button);
        SessionManager sessionManager = new SessionManager(this);

        logoutButton.setOnClickListener(v -> {
            sessionManager.logout();

            Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(MainActivity.this, StartActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finishAffinity();
        });
    }

    private void initializeViews() {
        // Rings
        ringCalories = findViewById(R.id.ring_calories);

        // Macro progress bars
        pbProtein = findViewById(R.id.pb_protein);
        pbCarbs   = findViewById(R.id.pb_carbs);
        pbFat     = findViewById(R.id.pb_fats);

        // Center text
        tvProgressCenter = findViewById(R.id.tv_progress_center);

        // Stats
        tvGoalValue      = findViewById(R.id.tv_goal_value);
        tvConsumedValue  = findViewById(R.id.tv_consumed_value);
        tvRemainingValue = findViewById(R.id.tv_remaining_value);

        // Date
        currentDateText = findViewById(R.id.current_date);

        // Macros text
        tvProteinValue = findViewById(R.id.tv_protein_value);
        tvCarbsValue   = findViewById(R.id.tv_carbs_value);
        tvFatsValue    = findViewById(R.id.tv_fats_value);

        // Buttons
        btnSettings = findViewById(R.id.to_settings);
        btnLogFood  = findViewById(R.id.to_foodlog);
        btnHistory  = findViewById(R.id.to_history);
        btnProfile = findViewById(R.id.to_profile);
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

        // Food logs / history
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        // Profile Section
        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DisplayWeightLogActivity.class);
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

        // Load settings (goals) from DB
        SettingsDatabaseHelper settingsDb = new SettingsDatabaseHelper(this);
        Cursor settingsCursor = settingsDb.getSettings();

        if (settingsCursor != null && settingsCursor.moveToFirst()) {
            try {
                goalCaloriesValue = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow("calorie_limit")
                );
                goalProteinValue = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow("protein_limit")
                );
                goalCarbsValue = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow("carb_limit")
                );
                goalFatValue = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow("fat_limit")
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            settingsCursor.close();
        }

        loadTodayTotals();
    }

    /**
     * Temporary demo values so the UI doesn't look empty.
     * Replace with real data once logging totals are available.
     */
    private void loadTodayTotals() {

        String uid = FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            updateUI(0,0,0,0);
            return;
        }

        isoUtc.setTimeZone(TimeZone.getTimeZone("UTC"));

        Calendar startLocal = Calendar.getInstance();
        startLocal.set(Calendar.HOUR_OF_DAY, 0);
        startLocal.set(Calendar.MINUTE, 0);
        startLocal.set(Calendar.SECOND, 0);
        startLocal.set(Calendar.MILLISECOND, 0);

        Calendar endLocal = (Calendar) startLocal.clone();
        endLocal.add(Calendar.DAY_OF_MONTH, 1);

        String startIso = isoUtc.format(new Date(startLocal.getTimeInMillis()));
        String endIso = isoUtc.format(new Date(endLocal.getTimeInMillis()));


        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("food_logs")
                .whereGreaterThanOrEqualTo("logged_at", startIso)
                .whereLessThan("logged_at", endIso)
                .get()
                .addOnSuccessListener(snap -> {
                    double totalCal=0;
                    double totalProtein=0;
                    double totalCarbs=0;
                    double totalFat=0;

                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Double c = d.getDouble("calories");
                            Double p = d.getDouble("protein_g");
                            Double cb = d.getDouble("carb_g");
                            Double f = d.getDouble("fat_g");

                            if (c != null) totalCal += c;
                            if (p != null) totalProtein += p;
                            if (cb != null) totalCarbs += cb;
                            if (f != null) totalFat += f;
                        }
                    }

                    int calInt = (int) Math.round(totalCal);
                    int proteinInt = (int) Math.round(totalProtein);
                    int carbsInt = (int) Math.round(totalCarbs);
                    int fatInt = (int) Math.round(totalFat);

                    updateUI(calInt, proteinInt, carbsInt, fatInt);
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    updateUI(0,0,0,0);
                });

    }



    private void updateUI(int calories, int protein, int carbs, int fat) {
        // Percentages relative to goals
        int caloriesPercent = calculatePercentage(calories, goalCaloriesValue);
        int proteinPercent  = calculatePercentage(protein, goalProteinValue);
        int carbsPercent    = calculatePercentage(carbs, goalCarbsValue);
        int fatPercent      = calculatePercentage(fat, goalFatValue);

        // Center text
        if (tvProgressCenter != null) {
            tvProgressCenter.setText(caloriesPercent + "%");
        }

        // Main ring
        if (ringCalories != null) {
            ringCalories.setMax(100);
            ringCalories.setProgress(caloriesPercent);
        }

        // Macro progress bars
        if (pbProtein != null) {
            pbProtein.setMax(100);
            pbProtein.setProgress(proteinPercent);
        }
        if (pbCarbs != null) {
            pbCarbs.setMax(100);
            pbCarbs.setProgress(carbsPercent);
        }
        if (pbFat != null) {
            pbFat.setMax(100);
            pbFat.setProgress(fatPercent);
        }

        // Stats under ring
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

        // Macros text
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
}

