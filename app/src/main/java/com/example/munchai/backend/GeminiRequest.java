package com.example.munchai.backend;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.example.munchai.model.NutritionFacts;

public class GeminiRequest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // It is highly recommended to not hardcode your API key.
    // Consider moving it to a secure place like local.properties.
    private static final String API_KEY = "AIzaSyDw5_eiyWlF3d0es5J7SBv__Pan5T_XUj0";
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * Overloaded method for fetching nutrition facts when weight is not provided.
     *
     * @param bmp The bitmap image of the food.
     * @return NutritionFacts object.
     * @throws IOException If the request fails.
     */
    public static NutritionFacts fetchNutritionFacts(@NonNull Bitmap bmp) throws IOException {
        return fetchNutritionFacts(bmp, null);
    }

    /**
     * Fetches nutrition facts from an image, optionally including the food's weight in the analysis.
     *
     * @param bmp         The bitmap image of the food.
     * @param weightGrams The weight of the food in grams. Can be null.
     * @return NutritionFacts object.
     * @throws IOException If the request fails.
     */
    public static NutritionFacts fetchNutritionFacts(@NonNull Bitmap bmp, String weightGrams) throws IOException {
        // downscale image
        Bitmap resized = downscale(bmp, 1280);

        // convert to Base64 JPEG
        String base64 = encodeToBase64Jpeg(resized, 85);

        // prompt
        String prompt = "You are analyzing a meal/food image. Carefully analyse the image. " +
                "The total weight of the food is " + (weightGrams != null ? weightGrams + " grams. " : "") +
                "Calculate the nutritional values for this specific weight. " +
                "Return a JSON object with these lowercase keys: " +
                "name, serving_size, calories, total_fat_g, saturated_fat_g, cholesterol_mg, sodium_mg, " +
                "total_carbohydrate_g, dietary_fiber_g, sugars_g, protein_g. " +
                "If a value is missing or unreadable, use null. " +
                "Do not include any text or markdown, output only the single JSON object.";

        // Build JSON request body
        ObjectNode inlineData = MAPPER.createObjectNode();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", base64);

        ObjectNode inlinePart = MAPPER.createObjectNode();
        inlinePart.set("inline_data", inlineData);

        ObjectNode textPart = MAPPER.createObjectNode();
        textPart.put("text", prompt);

        ArrayNode parts = MAPPER.createArrayNode();
        parts.add(inlinePart);
        parts.add(textPart);

        ObjectNode content = MAPPER.createObjectNode();
        content.put("role", "user");
        content.set("parts", parts);

        ArrayNode contents = MAPPER.createArrayNode();
        contents.add(content);

        ObjectNode root = MAPPER.createObjectNode();
        root.set("contents", contents);

        String requestJson = MAPPER.writeValueAsString(root);

        // send http post
        Request req = new Request.Builder()
                .url(URL + API_KEY)
                .post(RequestBody.create(requestJson, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + ": " + resp.body().string());
            }

            String respStr = resp.body().string();
            JsonNode rootNode = MAPPER.readTree(respStr);
            JsonNode textNode = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text");

            if (textNode.isMissingNode() || textNode.isNull()) {
                // Try to get a more descriptive error from the response if available
                JsonNode error = rootNode.path("error").path("message");
                if (!error.isMissingNode()) {
                    throw new IOException("Gemini API Error: " + error.asText());
                }
                throw new IOException("Gemini returned no text field in the response.");
            }

            String generated = textNode.asText().trim();

            // strip markdown fences if present
            if (generated.startsWith("")) {
                int first = generated.indexOf('{');
                int last = generated.lastIndexOf('}');
                if (first >= 0 && last > first) {
                    generated = generated.substring(first, last + 1);
                }
            } // parse response generated into the nutrition class for future use.
            return MAPPER.readValue(generated, NutritionFacts.class);
        }
    }

    public static NutritionFacts fetchNutritionFactsFromDrawable(Context ctx, @DrawableRes int drawableId) throws IOException {
        // load & downscale image
        Bitmap bmp = BitmapFactory.decodeResource(ctx.getResources(), drawableId);
        if (bmp == null) throw new IOException("Failed to decode resource " + drawableId);
        return fetchNutritionFacts(bmp, null);
    }

    private static Bitmap downscale(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxDim) return src;
        float scale = (float) maxDim / max;
        int nw = Math.round(w * scale), nh = Math.round(h * scale);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static String encodeToBase64Jpeg(Bitmap bmp, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }
}