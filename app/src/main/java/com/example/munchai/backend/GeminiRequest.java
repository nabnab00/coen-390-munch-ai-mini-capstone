package com.example.munchai.backend;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.DrawableRes;

import com.example.munchai.model.NutritionFacts;
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

public class GeminiRequest {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String API_KEY = "BuildConfig.GEMINI_API_KEY";
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public static NutritionFacts fetchNutritionFactsFromDrawable(Context ctx, @DrawableRes int drawableId) throws IOException {
        // Load & downscale image
        Bitmap bmp = BitmapFactory.decodeResource(ctx.getResources(), drawableId);
        if (bmp == null) throw new IOException("Failed to decode resource " + drawableId);
        Bitmap resized = downscale(bmp, 1280);

        // Convert to Base64 JPEG
        String base64 = encodeToBase64Jpeg(resized, 85);

        // Prompt
        String prompt = "Extract all nutritional values from the provided Nutrition Facts image. " +
                "Use your best reading of the label; if a value is not explicitly present, return null (do not invent data). " +
                "Return a single JSON object with exactly these keys and units: " +
                "Name, Serving size, Calories, Total Fat (%), Saturated Fat (%), Cholesterol (mg), Sodium (mg), " +
                "Total Carbohydrate (g), Dietary Fiber (g), Sugars (g), Protein (g), " +
                "Vitamin A (%), Vitamin C (%), Calcium (%), Iron (%). " +
                "Output only the raw JSON object. No markdown, no extra text.";

        // Build JSON (no .parent() calls)
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

        // Send HTTP POST
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
                throw new IOException("Gemini returned no text field.");
            }

            String generated = textNode.asText().trim();

            // Strip markdown fences if present
            if (generated.startsWith("```")) {
                int first = generated.indexOf('{');
                int last = generated.lastIndexOf('}');
                if (first >= 0 && last > first) {
                    generated = generated.substring(first, last + 1);
                }
            }

            // Parse into NutritionFacts
            return MAPPER.readValue(generated, NutritionFacts.class);
        }
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
