package com.example.munchai.frontend;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.munchai.R;
import com.example.munchai.backend.GeminiRequest;
import com.example.munchai.model.NutritionFacts;

public class TestActivity extends AppCompatActivity
    
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.testpage);
        TextView text = findViewById(R.id.textView);
        text.setText("Processing pizza image… ⏳");

        new Thread(() ->
        {
            try
            {
                NutritionFacts facts = GeminiRequest.fetchNutritionFactsFromDrawable(this, R.drawable.pizza);

                String summary = (facts != null)
                        ? facts.nutritionValuesToString()
                        : "❌ No data parsed.";
                runOnUiThread(() -> text.setText("✅ Parsed Nutrition Facts:\n\n" + summary));

            }
            catch (Exception e)
            {
                e.printStackTrace();
                runOnUiThread(() -> text.setText("❌ Error: " + e.getMessage()));
            }
        }).start();
    }
}