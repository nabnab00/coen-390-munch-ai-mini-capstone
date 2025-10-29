package com.example.munchai.frontend;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.munchai.R;
import com.example.munchai.backend.GeminiRequest;

import org.json.JSONObject;

public class TestActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.testpage);
        TextView text = findViewById(R.id.textView);
        text.setText("Processing... please wait ⏳");

        new Thread(() ->
        {
            String response = GeminiRequest.getGemini(TestActivity.this);

            runOnUiThread(() ->
            {
                if (response == null)
                {
                    text.setText("❌ Failed to get response. Check log for details.");
                    return;
                }

                try
                {
                    // Parse Gemini API response
                    JSONObject json = new JSONObject(response);

                    // Extract Gemini's generated text
                    String answer = json
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    //Display Gemini's text output
                    text.setText(answer);

                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    text.setText("⚠️ Error parsing response:\n" + e.getMessage() + "\n\nRaw:\n" + response);
                }
            });
        }).start();
    }
}
