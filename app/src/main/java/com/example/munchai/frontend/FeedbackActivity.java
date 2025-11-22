package com.example.munchai.frontend;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.example.munchai.backend.GeminiRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class FeedbackActivity extends AppCompatActivity {

    private static final String TAG = "FeedbackActivity";

    private TextView feedbackTextView;
    private ProgressBar progressBar;
    private Button generateButton;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feedbackpage);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        feedbackTextView = findViewById(R.id.feedback_text_view);
        progressBar = findViewById(R.id.feedback_progress_bar);
        generateButton = findViewById(R.id.generate_feedback_button);
        ImageButton backButton = findViewById(R.id.feedback_back_button);

        backButton.setOnClickListener(v -> finish());
        generateButton.setOnClickListener(v -> generateFeedback());
    }

    private void generateFeedback() {
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true);
        fetchDataAndBuildPrompt();
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            generateButton.setEnabled(false);
            feedbackTextView.setText("");
        } else {
            progressBar.setVisibility(View.GONE);
            generateButton.setEnabled(true);
        }
    }

    private void fetchDataAndBuildPrompt() {
        String uid = currentUser.getUid();
        StringBuilder promptBuilder = new StringBuilder();

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            if (!userDoc.exists()) {
                handleFailure(new Exception("User profile not found."));
                return;
            }

            String name = currentUser.getDisplayName();
            promptBuilder.append("Hello ").append(name != null ? name : "User").append("!\n\n");
            promptBuilder.append("Here is your current profile:\n");
            promptBuilder.append("- Age: ").append(userDoc.contains("age") ? userDoc.getLong("age") : "Not set").append("\n");
            promptBuilder.append("- Height: ").append(userDoc.contains("height") ? userDoc.getLong("height") : "Not set").append("\n");
            promptBuilder.append("- BMI: ").append(calculateBmi(userDoc)).append("\n\n");

            fetchWeightLogs(uid, promptBuilder);

        }).addOnFailureListener(this::handleFailure);
    }

    private void fetchWeightLogs(String uid, StringBuilder promptBuilder) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        Date oneWeekAgo = cal.getTime();

        db.collection("users").document(uid).collection("personal_weight_logs")
                .whereGreaterThanOrEqualTo("date", oneWeekAgo)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(weightLogs -> {
                    promptBuilder.append("Your weight progress for the past 7 days:\n");
                    if (weightLogs.isEmpty()) {
                        promptBuilder.append("- No weights logged this week.\n\n");
                    } else {
                        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                        for (QueryDocumentSnapshot doc : weightLogs) {
                            promptBuilder.append("- ").append(sdf.format(doc.getDate("date")))
                                    .append(": ").append(doc.getDouble("weight")).append(" kg\n");
                        }
                        promptBuilder.append("\n");
                    }
                    // 3. Fetch Food Logs (Past Week)
                    fetchFoodLogs(uid, oneWeekAgo, promptBuilder);
                }).addOnFailureListener(this::handleFailure);
    }

    private void fetchFoodLogs(String uid, Date oneWeekAgo, StringBuilder promptBuilder) {


        //why tf is food logs date saved as a string? to fix in the near future
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        String oneWeekAgoString = isoFormat.format(oneWeekAgo);

        db.collection("users").document(uid).collection("food_logs")
                .whereGreaterThanOrEqualTo("logged_at", oneWeekAgoString)
                .orderBy("logged_at", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(foodLogs -> {
                    Log.d(TAG, "Food logs query completed. Found " + foodLogs.size() + " documents.");
                    promptBuilder.append("Your recent meals (last 7 days):\n");
                    if (foodLogs.isEmpty()) {
                        promptBuilder.append("- No meals logged in this period.\n\n");
                    }
                    else {
                        for (QueryDocumentSnapshot doc : foodLogs) {
                            String mealName = doc.contains("name") ? doc.getString("name") : "Unnamed Meal";
                            String mealType = doc.contains("meal") ? doc.getString("meal") : "Unknown";
                            Object caloriesObj = doc.get("calories");
                            String calories = (caloriesObj != null) ? caloriesObj.toString() : "N/A";

                            promptBuilder.append("- ").append(mealName)
                                    .append(" (Meal: ").append(mealType)
                                    .append(", Calories: ").append(calories).append(")\n");
                        }
                        promptBuilder.append("\n");
                    }

                    promptBuilder.append("\nBased on all this data, provide constructive and encouraging health advice.\n")
                            .append("Give 2 sentences about the user's age and BMI (calculate using the height and most recent weight logged), with advice related to that.\n")
                            .append("Detail analysis about the user's recent food logs, with advice to improve their health.\n")
                            .append("Detail analysis (3-4 sentences) about the user's weight progress.\n")
                            .append("Keep the tone positive and motivating.");


                    String finalPrompt = promptBuilder.toString();
                    Log.d(TAG, "Final Prompt: " + finalPrompt);

                    GeminiRequest.generateFeedback(finalPrompt, new GeminiRequest.FeedbackCallback() {
                        @Override
                        public void onSuccess(String feedback) {
                            runOnUiThread(() -> {
                                setLoadingState(false);
                                feedbackTextView.setText(feedback);
                            });
                        }

                        @Override
                        public void onFailure(Exception e) {
                            runOnUiThread(() -> handleFailure(e));
                        }
                    });

                }).addOnFailureListener(e -> {
                    Log.e(TAG, "error fetching food logs", e);
                    handleFailure(e);
                });
    }


    private String calculateBmi(DocumentSnapshot userDoc) {
        if (!userDoc.contains("height") || !userDoc.contains("latestWeight")) {
            return "Incomplete data for BMI";
        }
        try {
            double heightCm = userDoc.getDouble("height");
            double weightKg = userDoc.getDouble("latestWeight");

            if (heightCm <= 0 || weightKg <= 0) return "Invalid data for BMI";

            double heightM = heightCm / 100.0;
            double bmi = weightKg / (heightM * heightM);
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(bmi);
        } catch (Exception e) {
            Log.e(TAG, "Could not calculate BMI", e);
            return "Could not calculate BMI";
        }
    }

    private void handleFailure(Exception e) {
        setLoadingState(false);
        Log.e(TAG, "Failed to generate feedback", e);
        feedbackTextView.setText("An error occurred while generating feedback. Please try again.");
        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }


}
