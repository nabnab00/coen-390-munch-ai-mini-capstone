package com.example.munchai.frontend;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.munchai.R;
import com.example.munchai.model.FoodLogRow;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class DisplayLogActivity extends AppCompatActivity {

    private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.displaylogpage);

        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        ImageButton backButton = findViewById(R.id.backDisplayButton);
        backButton.setOnClickListener(v -> finish());

        FoodLogRow foodLog = (FoodLogRow) getIntent().getSerializableExtra("food_log");

        ImageButton deleteButton = findViewById(R.id.deleteMealButton);

        deleteButton.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Meal")
                    .setMessage("Are you sure you want to delete this meal?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteMeal(foodLog))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        if (foodLog != null) {
            ImageView logImagePreview = findViewById(R.id.log_image_preview);
            if (foodLog.imageUrl != null && !foodLog.imageUrl.isEmpty()) {
                logImagePreview.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(foodLog.imageUrl)
                        .into(logImagePreview);
            }
            else {
                logImagePreview.setVisibility(View.GONE);
            }

            ((TextView) findViewById(R.id.value_food_name)).setText(foodLog.name);
            ((TextView) findViewById(R.id.value_meal_type)).setText(foodLog.meal);

            String formattedDate = foodLog.loggedAtIso;
            try {
                // Defensive check to avoid parsing an empty string
                if (foodLog.loggedAtIso != null && !foodLog.loggedAtIso.isEmpty()) {
                    formattedDate = out.format(iso.parse(foodLog.loggedAtIso));
                }
            }
            catch (ParseException | NullPointerException ignored) {
                formattedDate = foodLog.loggedAtIso;
            }
            ((TextView) findViewById(R.id.value_logged_at)).setText(formattedDate);
            ((TextView) findViewById(R.id.value_weight)).setText(String.format(Locale.getDefault(), "%.1f %s", foodLog.weight, foodLog.unit));

            if (foodLog.calories != null) {
                ((TextView) findViewById(R.id.value_calories)).setText(String.format(Locale.getDefault(), "%.1f", foodLog.calories));
            }
            if (foodLog.proteinG != null) {
                ((TextView) findViewById(R.id.value_protein)).setText(String.format(Locale.getDefault(), "%.1f", foodLog.proteinG));
            }
            if (foodLog.carbG != null) {
                ((TextView) findViewById(R.id.value_carbs)).setText(String.format(Locale.getDefault(), "%.1f", foodLog.carbG));
            }
            if (foodLog.fatG != null) {
                ((TextView) findViewById(R.id.value_fat)).setText(String.format(Locale.getDefault(), "%.1f", foodLog.fatG));
            }
        }
    }

    private void deleteMeal(FoodLogRow row) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null || row.documentId == null) {
            Toast.makeText(this, "Error: missing document ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Delete the Firestore document
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("food_logs").document(row.documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Delete photo
                    try {
                        com.google.firebase.storage.StorageReference photoRef =
                                com.google.firebase.storage.FirebaseStorage.getInstance()
                                        .getReferenceFromUrl(row.imageUrl);

                        photoRef.delete()
                                .addOnSuccessListener(aVoid2 -> {
                                    Toast.makeText(this, "Meal and photo deleted", Toast.LENGTH_SHORT).show();
                                    finish(); // go back to HistoryActivity
                                })
                                .addOnFailureListener(e -> {
                                    // Photo deletion failed
                                    Toast.makeText(this, "Failed to delete photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    finish();
                                });
                    } catch (IllegalArgumentException e) {
                        // URL invalid
                        Toast.makeText(this, "Meal deleted but photo could not be deleted (invalid URL)", Toast.LENGTH_LONG).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error deleting meal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
