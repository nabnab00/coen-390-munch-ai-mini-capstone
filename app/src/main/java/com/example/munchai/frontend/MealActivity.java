package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.example.munchai.backend.database.AppDatabaseHelper;
import com.example.munchai.backend.SessionManager;
import com.example.munchai.backend.media.PhotoCaptureManager;
import com.example.munchai.backend.media.PhotoStore;
import com.example.munchai.model.NutritionFacts;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MealActivity extends AppCompatActivity
{
    private EditText nameEt, weightEt, caloriesEt, fatEt, proteinEt, carbsEt;
    private Spinner mealSp;
    private TextView dateTv;
    private ImageView photoIv;
    private Button retakeBtn;
    private AppDatabaseHelper db;
    private SessionManager session;
    private int selYear, selMonth, selDay;

    private PhotoCaptureManager photoMgr;

    private ActivityResultLauncher<Intent> weightScaleLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_screen);

        db = new AppDatabaseHelper(this);
        session = new SessionManager(this);

        photoIv = findViewById(R.id.photo_preview);
        retakeBtn = findViewById(R.id.retake_button);
        Button toWeight = findViewById(R.id.to_weight);

        nameEt = findViewById(R.id.input_food_name);
        weightEt = findViewById(R.id.input_weight);
        caloriesEt = findViewById(R.id.input_calories);
        fatEt = findViewById(R.id.input_fat);
        proteinEt = findViewById(R.id.input_protein);
        carbsEt = findViewById(R.id.input_carbohydrates);
        mealSp = findViewById(R.id.spinner_meal);
        dateTv = findViewById(R.id.text_date_value);

        enableForm(false);

        ArrayAdapter<CharSequence> mealAd = ArrayAdapter.createFromResource(
                this, R.array.meals_array, android.R.layout.simple_spinner_item);
        mealAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mealSp.setAdapter(mealAd);

        Calendar now = Calendar.getInstance();
        selYear = now.get(Calendar.YEAR);
        selMonth = now.get(Calendar.MONTH);
        selDay = now.get(Calendar.DAY_OF_MONTH);
        dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", selDay, selMonth + 1, selYear));

        dateTv.setOnClickListener(v -> showDatePicker());

        Button saveBtn = findViewById(R.id.save_button);
        Button cancelBtn = findViewById(R.id.cancel_button);
        saveBtn.setOnClickListener(v -> saveLog());
        cancelBtn.setOnClickListener(v -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Log Meal");
        }

        weightScaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String weight = data.getStringExtra("weight");
                        String nutritionJson = data.getStringExtra("nutrition_facts");

                        weightEt.setText(weight);

                        if (nutritionJson != null) {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                NutritionFacts facts = mapper.readValue(nutritionJson, NutritionFacts.class);
                                populateNutritionFields(facts);
                            } catch (Exception e) {
                                Toast.makeText(this, "Failed to parse nutrition data.", Toast.LENGTH_SHORT).show();
                            }
                        }
                        enableForm(true);
                    } else {
                        // Handle cancellation or failure
                        Toast.makeText(this, "Could not get weight and nutrition data.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });

        toWeight.setOnClickListener(v -> {
            Intent intent = new Intent(MealActivity.this, WeightScaleActivity.class);
            weightScaleLauncher.launch(intent);
        });

        photoMgr = new PhotoCaptureManager(
                this,
                photoIv,
                new PhotoStore(this),
                new PhotoCaptureManager.Callbacks() {
                    @Override
                    public void onPhotoReady(android.net.Uri uri) {
                        Intent intent = new Intent(MealActivity.this, WeightScaleActivity.class);
                        intent.setData(uri);
                        weightScaleLauncher.launch(intent);
                    }
                    @Override
                    public void onCaptureCanceled() {
                        Toast.makeText(MealActivity.this,
                                "Photo required to log a meal", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );
        photoMgr.register();
        retakeBtn.setOnClickListener(v -> photoMgr.retake());
        photoMgr.startCapture();
    }

    private void populateNutritionFields(NutritionFacts facts) {
        if (facts == null) return;
        nameEt.setText(facts.name.toString());
        caloriesEt.setText(String.valueOf(facts.calories));
        fatEt.setText(String.valueOf(facts.totalFatG));
        proteinEt.setText(String.valueOf(facts.proteinG));
        carbsEt.setText(String.valueOf(facts.totalCarbG));
    }


    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.set(selYear, selMonth, selDay);

        DatePickerDialog dlg = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    selYear = y;
                    selMonth = m;
                    selDay = d;
                    dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dlg.show();
    }

    private void enableForm(boolean enabled) {
        nameEt.setEnabled(enabled);
        weightEt.setEnabled(enabled);
        caloriesEt.setEnabled(enabled);
        fatEt.setEnabled(enabled);
        proteinEt.setEnabled(enabled);
        carbsEt.setEnabled(enabled);
        mealSp.setEnabled(enabled);
        dateTv.setEnabled(enabled);
        findViewById(R.id.save_button).setEnabled(enabled);
    }

    private void saveLog() {
        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!photoMgr.hasPhoto()) {
            Toast.makeText(this, "Please take a photo first", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEt.getText().toString().trim();
        String weightStr = weightEt.getText().toString().trim();
        String caloriesStr = caloriesEt.getText().toString().trim();
        String fatStr = fatEt.getText().toString().trim();
        String proteinStr = proteinEt.getText().toString().trim();
        String carbsStr = carbsEt.getText().toString().trim();
        String meal = (String) mealSp.getSelectedItem();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(weightStr) || TextUtils.isEmpty(caloriesStr) ||
                TextUtils.isEmpty(fatStr) || TextUtils.isEmpty(proteinStr) || TextUtils.isEmpty(carbsStr)) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double weight, calories, fat, protein, carbs;
        try {
            weight = Double.parseDouble(weightStr);
            calories = Double.parseDouble(caloriesStr);
            fat = Double.parseDouble(fatStr);
            protein = Double.parseDouble(proteinStr);
            carbs = Double.parseDouble(carbsStr);
            if (weight <= 0 || calories < 0 || fat < 0 || protein < 0 || carbs < 0) {
                throw new NumberFormatException();
            }
        }
        catch (NumberFormatException nfe) {
            Toast.makeText(this, "All numeric fields must be positive numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        String tsIso = selectedDateMidnightIsoUtc();

        long id = db.insertLog(
                name, weight, calories, fat, protein, carbs, meal, tsIso
        );

        if (id > 0) {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MealActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private String selectedDateMidnightIsoUtc() {
        Calendar local = Calendar.getInstance();
        local.set(Calendar.YEAR, selYear);
        local.set(Calendar.MONTH, selMonth);
        local.set(Calendar.DAY_OF_MONTH, selDay);
        local.set(Calendar.HOUR_OF_DAY, 0);
        local.set(Calendar.MINUTE, 0);
        local.set(Calendar.SECOND, 0);
        local.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return iso.format(new Date(local.getTimeInMillis()));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
