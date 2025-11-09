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
    }
}