package com.example.munchai.frontend;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.example.munchai.backend.GeminiRequest;
import com.example.munchai.model.NutritionFacts;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightScaleActivity extends AppCompatActivity {

    private TextView weightText;
    private Button saveBtn;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.weightpage);

        weightText = findViewById(R.id.weightText);
        saveBtn = findViewById(R.id.weightsave); // Assuming you add a save button to weightpage.xml

        Uri photoUri = getIntent().getData();
        if (photoUri == null) {
            Toast.makeText(this, "No photo URI provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        saveBtn.setOnClickListener(v -> {
            String weight = weightText.getText().toString();
            if (weight.isEmpty()) {
                Toast.makeText(this, "Weight cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Disable button to prevent multiple clicks
            saveBtn.setEnabled(false);
            Toast.makeText(this, "Analyzing nutrition...", Toast.LENGTH_LONG).show();

            // Run Gemini request in the background
            exec.execute(() -> {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoUri);
                    NutritionFacts facts = GeminiRequest.fetchNutritionFacts(bitmap);

                    ObjectMapper mapper = new ObjectMapper();
                    String nutritionJson = mapper.writeValueAsString(facts);

                    runOnUiThread(() -> {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("weight", weight);
                        resultIntent.putExtra("nutrition_facts", nutritionJson);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });

                } catch (IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(WeightScaleActivity.this, "Failed to get nutrition data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        saveBtn.setEnabled(true);
                    });
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!exec.isShutdown()) {
            exec.shutdown();
        }
    }
}
