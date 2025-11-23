// == START OF FILE: ProfileActivity.java ==
package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.munchai.R;
import com.example.munchai.backend.SessionManager;
import com.example.munchai.frontend.adapter.WeightLogAdapter;
import com.example.munchai.model.WeightLog;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private TextView profileTitle;
    private TextView personalEmailTextView; // TextView for email
    private EditText personalNameEditText;
    private EditText personalAgeEditText;
    private EditText personalHeightEditText;
    private EditText personalWeightEditText;
    private TextView bmiValueTextView;
    private TextView feedbackButton;
    private Button logWeightButton;
    private ImageButton backButton, logoutButton;
    private RecyclerView weightLogsRecyclerView; // Changed from ListView
    private LineChart weightChart; // Add LineChart variable
    private RadioButton unitKgRadio, unitLbRadio;

    private WeightLogAdapter weightLogAdapter;
    private ArrayList<WeightLog> weightLogList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profilepage);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // --- Initialize Views ---
        profileTitle = findViewById(R.id.profile_title);
        personalEmailTextView = findViewById(R.id.personal_email);
        personalNameEditText = findViewById(R.id.personal_name);
        personalAgeEditText = findViewById(R.id.personal_age);
        personalHeightEditText = findViewById(R.id.personal_height_info);
        personalWeightEditText = findViewById(R.id.personal_weight);
        bmiValueTextView = findViewById(R.id.bmi_value);

        logWeightButton = findViewById(R.id.profile_save);
        backButton = findViewById(R.id.profile_back_button);
        logoutButton = findViewById(R.id.logout_button);
        weightLogsRecyclerView = findViewById(R.id.weight_logs_list);
        weightChart = findViewById(R.id.weight_chart);
        weightLogList = new ArrayList<>();

        // Unit toggles (from profilepage.xml)
        unitKgRadio = findViewById(R.id.unit_kg);
        unitLbRadio = findViewById(R.id.unit_lb);
        if (!unitKgRadio.isChecked() && !unitLbRadio.isChecked()) {
            unitKgRadio.setChecked(true);
        }

        weightLogsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        weightLogAdapter = new WeightLogAdapter(ProfileActivity.this, weightLogList);
        weightLogsRecyclerView.setAdapter(weightLogAdapter);

        profileTitle.setText(R.string.profile_title);

        setupSwipeToDelete();

        if (currentUser != null) {
            personalEmailTextView.setText(currentUser.getEmail());
            loadUserProfile(); // Load user name, age, and height
            fetchWeightLogs();
        } else {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // --- Set Listeners ---
        logWeightButton.setOnClickListener(v -> logWeight());

        // Listener to save profile info when "Done" is pressed on keyboard
        TextView.OnEditorActionListener editorActionListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveUserProfile();
                v.clearFocus(); // Remove focus from the EditText
                return true;
            }
            return false;
        };

        personalNameEditText.setOnEditorActionListener(editorActionListener);
        personalAgeEditText.setOnEditorActionListener(editorActionListener);
        personalHeightEditText.setOnEditorActionListener(editorActionListener);

        backButton.setOnClickListener(v -> finish());

        logoutButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Log out")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        SessionManager sessionManager = new SessionManager(this);
                        sessionManager.logout();
                        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ProfileActivity.this, StartActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finishAffinity();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        feedbackButton = findViewById(R.id.to_ai_feedback_button);
        feedbackButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, FeedbackActivity.class);
            startActivity(intent);
        });
    }

    private void saveUserProfile() {
        if (currentUser == null) return;

        String name = personalNameEditText.getText().toString().trim();
        String ageStr = personalAgeEditText.getText().toString().trim();
        String heightStr = personalHeightEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name) && TextUtils.isEmpty(ageStr) && TextUtils.isEmpty(heightStr)) {
            return; // nothing to save
        }

        try {
            Integer age = TextUtils.isEmpty(ageStr) ? null : Integer.parseInt(ageStr);
            Double height = TextUtils.isEmpty(heightStr) ? null : Double.parseDouble(heightStr);

            UserProfileChangeRequest.Builder profileUpdatesBuilder = new UserProfileChangeRequest.Builder();
            if (!TextUtils.isEmpty(name)) {
                profileUpdatesBuilder.setDisplayName(name);
            }

            currentUser.updateProfile(profileUpdatesBuilder.build())
                    .addOnSuccessListener(aVoid -> {
                        // Save extra fields to Firestore
                        DocumentReference docRef = db.collection("users").document(currentUser.getUid());
                        // We store height in cm as typed
                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                        if (!TextUtils.isEmpty(name)) map.put("name", name);
                        if (age != null) map.put("age", age);
                        if (height != null) map.put("height_cm", height);

                        docRef.set(map, SetOptions.merge())
                                .addOnSuccessListener(v -> {
                                    Toast.makeText(ProfileActivity.this, "Profile updated.", Toast.LENGTH_SHORT).show();
                                    calculateAndDisplayBmi();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileActivity.this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "Profile save error", e);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to update auth profile.", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "Auth profile save error", e);
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid age/height.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadUserProfile() {
        if (currentUser == null) return;

        DocumentReference docRef = db.collection("users").document(currentUser.getUid());
        docRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String name = snapshot.getString("name");
                        Long age = snapshot.getLong("age");
                        Double heightCm = snapshot.getDouble("height_cm");

                        if (!TextUtils.isEmpty(name)) personalNameEditText.setText(name);
                        if (age != null) personalAgeEditText.setText(String.valueOf(age));
                        if (heightCm != null) personalHeightEditText.setText(String.valueOf(heightCm));
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "loadUserProfile: ", e));
    }

    /** Returns input weight normalized to kilograms, based on the selected unit. */
    private double toKg(double input) {
        if (unitLbRadio != null && unitLbRadio.isChecked()) {
            return input * 0.45359237;
        }
        return input;
    }

    void logWeight() {
        // Your implementation for logging weight
        String weightStr = personalWeightEditText.getText().toString().trim();

        if (TextUtils.isEmpty(weightStr)) {
            personalWeightEditText.setError("Weight cannot be empty.");
            return;
        }

        double weightValue;
        try {
            weightValue = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            personalWeightEditText.setError("Invalid number.");
            return;
        }

        // Normalize to kilograms regardless of selected unit
        double weightKg = toKg(weightValue);

        if (currentUser == null) {
            Toast.makeText(this, "Error: You must be logged in to log weight.", Toast.LENGTH_SHORT).show();
            return;
        }

        Date currentDate = new Date();
        WeightLog newLog = new WeightLog(currentDate, weightKg);

        CollectionReference weightLogsCollection = db.collection("users")
                .document(currentUser.getUid())
                .collection("personal_weight_logs");

        Calendar cal = Calendar.getInstance();
        cal.setTime(currentDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startDate = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date endDate = cal.getTime();

        weightLogsCollection
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThan("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {

                        // if log the same day already exists
                        DocumentReference docRef = queryDocumentSnapshots.getDocuments().get(0).getReference();
                        docRef.update("weight", weightKg)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(ProfileActivity.this, "Today's weight updated.", Toast.LENGTH_SHORT).show();
                                    fetchWeightLogs();
                                    calculateAndDisplayBmi();
                                    personalWeightEditText.getText().clear();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileActivity.this, "Failed to update weight.", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "Error updating document", e);
                                });
                    } else {
                        weightLogsCollection.add(newLog)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(ProfileActivity.this, "Weight logged.", Toast.LENGTH_SHORT).show();
                                    fetchWeightLogs();
                                    calculateAndDisplayBmi();
                                    personalWeightEditText.getText().clear();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileActivity.this, "Failed to log weight.", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "Error adding document", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "Failed to check for existing log.", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Error getting documents", e);
                });
    }

    private void fetchWeightLogs() {
        // Your implementation for fetching weight logs
        if (currentUser != null) {
            CollectionReference logsRef = db.collection("users").document(currentUser.getUid())
                    .collection("personal_weight_logs");

            logsRef.orderBy("date", Query.Direction.DESCENDING).get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        weightLogList.clear();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            WeightLog log = document.toObject(WeightLog.class);
                            weightLogList.add(log);
                        }
                        weightLogAdapter.notifyDataSetChanged();
                        updateWeightChart();
                        calculateAndDisplayBmi();
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "fetchWeightLogs: ", e));
        }
    }

    private void updateWeightChart() {
        if (weightChart == null) return;

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        // Sort by date ascending for chart
        ArrayList<WeightLog> sorted = new ArrayList<>(weightLogList);
        Collections.sort(sorted, new Comparator<WeightLog>() {
            @Override
            public int compare(WeightLog o1, WeightLog o2) {
                return o1.getDate().compareTo(o2.getDate());
            }
        });

        SimpleDateFormat df = new SimpleDateFormat("MMM d", Locale.getDefault());
        for (int i = 0; i < sorted.size(); i++) {
            WeightLog log = sorted.get(i);
            entries.add(new Entry(i, (float) log.getWeight())); // weight already in kg
            labels.add(df.format(log.getDate()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Weight (kg)");
        dataSet.setColor(ContextCompat.getColor(this, R.color.munch_bangladesh_green));
        dataSet.setDrawValues(false);
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.munch_bangladesh_green));
        dataSet.setLineWidth(4f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);

        LineData lineData = new LineData(dataSet);
        weightChart.setData(lineData);

        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.max(2, labels.size() / 2));

        YAxis leftAxis = weightChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        weightChart.getAxisRight().setEnabled(false);

        weightChart.getDescription().setEnabled(false);
        weightChart.getLegend().setEnabled(true);

        weightChart.invalidate();
    }

    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final Drawable deleteIcon
                    = ContextCompat.getDrawable(ProfileActivity.this, R.drawable.ic_delete);
            private final ColorDrawable background = new ColorDrawable(Color.RED);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos < 0 || pos >= weightLogList.size() || currentUser == null) return;

                WeightLog log = weightLogList.get(pos);
                // Delete by date match (and weight) to be safe
                db.collection("users").document(currentUser.getUid())
                        .collection("personal_weight_logs")
                        .whereEqualTo("date", log.getDate())
                        .get()
                        .addOnSuccessListener(snap -> {
                            if (!snap.isEmpty()) {
                                snap.getDocuments().get(0).getReference().delete()
                                        .addOnSuccessListener(v -> {
                                            weightLogList.remove(pos);
                                            weightLogAdapter.notifyItemRemoved(pos);
                                            calculateAndDisplayBmi();
                                            Snackbar.make(weightLogsRecyclerView, "Deleted weight log", Snackbar.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(e -> Log.w(TAG, "delete weight log failed", e));
                            } else {
                                weightLogAdapter.notifyItemChanged(pos);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "query for delete failed", e);
                            weightLogAdapter.notifyItemChanged(pos);
                        });
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View itemView = viewHolder.itemView;
                int backgroundCornerOffset = 20;

                int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();

                if (dX < 0) { // Swiping left
                    int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                    int iconRight = itemView.getRight() - iconMargin;
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                    background.setBounds(itemView.getRight() + ((int) dX) - backgroundCornerOffset,
                            itemView.getTop(), itemView.getRight(), itemView.getBottom());
                } else {
                    background.setBounds(0, 0, 0, 0);
                }

                background.draw(c);
                deleteIcon.draw(c);
            }
        }).attachToRecyclerView(weightLogsRecyclerView);
    }

    private void calculateAndDisplayBmi() {
        String heightStr = personalHeightEditText.getText().toString().trim();
        if (weightLogList.isEmpty() || heightStr.isEmpty()) {
            bmiValueTextView.setText("-");
            return;
        }

        try {
            double heightInCm = Double.parseDouble(heightStr);
            if (heightInCm <= 0) {
                bmiValueTextView.setText("-");
                return;
            }
            double heightInM = heightInCm / 100.0;
            double latestWeight = weightLogList.get(0).getWeight(); // Stored as kg

            double bmi = latestWeight / (heightInM * heightInM);

            String classification;
            if (bmi < 18.5) {
                classification = "Underweight";
            } else if (bmi < 25.0) {
                classification = "Normal";
            } else if (bmi < 30.0) {
                classification = "Overweight";
            } else if (bmi < 35.0) {
                classification = "Obese class I";
            } else if (bmi < 40.0) {
                classification = "Obese class II";
            } else {
                classification = "Obese class III";
            }
            DecimalFormat df = new DecimalFormat("#.#");
            bmiValueTextView.setText(df.format(bmi) + " (" + classification + ")");

        } catch (NumberFormatException e) {
            bmiValueTextView.setText("-");
            Log.e(TAG, "Could not parse height for BMI calculation", e);
        }
    }
}
// == END OF FILE: ProfileActivity.java ==
