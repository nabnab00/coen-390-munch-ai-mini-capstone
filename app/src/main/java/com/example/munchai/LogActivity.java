package com.example.munchai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.data.LogsStorage;

import org.json.JSONObject;

public class LogActivity extends AppCompatActivity {

    private EditText inputFoodName, inputWeight;
    private Spinner spinnerUnit;
    private RadioGroup groupMeal;
    private Button btnSave, btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // Action bar back arrow (optional)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manual Log");
        }

        // 1) Hook up views
        inputFoodName = findViewById(R.id.inputFoodName);
        inputWeight   = findViewById(R.id.inputWeight);
        spinnerUnit   = findViewById(R.id.spinnerUnit);
        groupMeal     = findViewById(R.id.groupMeal);
        btnSave       = findViewById(R.id.btnSave);
        btnCancel     = findViewById(R.id.backButton);

        // 2) Units in spinner
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"g", "oz", "ml"}
        );
        spinnerUnit.setAdapter(unitAdapter);

        // 3) Save handler
        btnSave.setOnClickListener(v -> saveEntry());

        // 4) Cancel handler (just go back)
        btnCancel.setOnClickListener(v -> {
            finish();
        });
    }

    private void saveEntry() {
        String name = inputFoodName.getText().toString().trim();
        String weightStr = inputWeight.getText().toString().trim();
        String unit = spinnerUnit.getSelectedItem().toString();

        // Meal from radio group
        int checkedId = groupMeal.getCheckedRadioButtonId();
        String meal = null;
        if (checkedId != -1) {
            RadioButton rb = findViewById(checkedId);
            meal = rb.getText().toString();
        }

        // Basic validation
        if (TextUtils.isEmpty(name)) {
            inputFoodName.setError("Enter food name");
            return;
        }
        if (TextUtils.isEmpty(weightStr)) {
            inputWeight.setError("Enter a weight");
            return;
        }

        double weight;
        try {
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            inputWeight.setError("Invalid number");
            return;
        }
        if (weight <= 0) {
            inputWeight.setError("Weight must be > 0");
            return;
        }
        if (meal == null) {
            Toast.makeText(this, "Select a meal", Toast.LENGTH_SHORT).show();
            return;
        }

        // Optional: convert to grams internally if you want a single canonical unit
        double weightInGrams = convertToGrams(weight, unit);

        try {
            JSONObject item = new JSONObject();
            item.put("foodName", name);
            item.put("weight", weight);                // original value
            item.put("unit", unit);
            item.put("weight_g", weightInGrams);       // normalized grams
            item.put("meal", meal);
            item.put("timestamp", System.currentTimeMillis());

            new LogsStorage(this).add(item);

            Toast.makeText(this, "Saved log ✅", Toast.LENGTH_SHORT).show();
            finish();  // go back to MainActivity
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
        }
    }

    private double convertToGrams(double value, String unit) {
        switch (unit) {
            case "g":  return value;
            case "oz": return value * 28.349523125; // 1 oz = 28.3495 g
            case "ml": return value;                // assume ml ~ g for water-like density
            default:   return value;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}




