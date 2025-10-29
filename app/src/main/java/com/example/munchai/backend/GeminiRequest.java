package com.example.munchai.backend;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.example.munchai.R;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import okhttp3.*;

public class GeminiRequest
{
    public static String getGemini(Context context)
    {
        //30s prompt time for Gemini
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String apiKey = "AIzaSyDw5_eiyWlF3d0es5J7SBv__Pan5T_XUj0";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        try
        {
            //Load image from drawable
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pizza);

            //Convert Bitmap → byte[] → Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

            //Build JSON request
            String jsonBody = "{"
                    + "\"contents\": [{"
                    + "  \"role\": \"user\","
                    + "  \"parts\": ["
                    + "    {\"text\": \"With the best of your knowledge estimate and use the web and try to Extract all nutritional values from this image and return it as a JSON object following the Quebec Nutrition Facts Table format, including: ' +\n" +
                    "                  'Name,Serving size, Calories, Total Fat (%), Saturated Fat (%), Cholesterol (mg), Sodium (mg), Total Carbohydrate (g), Dietary Fiber (g), Sugars (g), Protein (g), ' +\n" +
                    "                  'Vitamin A (%), Vitamin C (%), Calcium (%), Iron (%). Return only the raw JSON. No markdown, no explanation.\"},"
                    + "    {\"inline_data\": {"
                    + "       \"mime_type\": \"image/png\","
                    + "       \"data\": \"" + base64Image + "\""
                    + "    }}"
                    + "  ]"
                    + "}]"
                    + "}";

            //Send HTTP request
            RequestBody body = RequestBody.create(
                    jsonBody, MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            Response response = client.newCall(request).execute();

            //Error handling
            if (response.isSuccessful())
            {
                return response.body().string();
            }
            else
            {
                System.out.println("Error code: " + response.code());
                System.out.println("Error body: " + response.body().string());
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
