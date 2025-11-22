package com.example.munchai.frontend;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DisplayWeightLogActivity extends AppCompatActivity {

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
    private Button logWeightButton;
    private ImageButton backButton;
    private RecyclerView weightLogsRecyclerView; // Changed from ListView
    private LineChart weightChart; // Add LineChart variable

    private WeightLogAdapter weightLogAdapter;
    private ArrayList<WeightLog> weightLogList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.historyweightpage);

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
        logWeightButton = findViewById(R.id.profile_save);
        backButton = findViewById(R.id.profile_back_button);
        weightLogsRecyclerView = findViewById(R.id.weight_logs_list);
        weightChart = findViewById(R.id.weight_chart);
        weightLogList = new ArrayList<>();

        weightLogsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        weightLogAdapter = new WeightLogAdapter(this, weightLogList);
        weightLogsRecyclerView.setAdapter(weightLogAdapter);

        setupWeightChart();
        setupSwipeToDelete();

        if (currentUser != null) {
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
            }
            return false; // Return false to allow default handling (e.g., closing keyboard)
        };

        personalNameEditText.setOnEditorActionListener(editorActionListener);
        personalAgeEditText.setOnEditorActionListener(editorActionListener);
        personalHeightEditText.setOnEditorActionListener(editorActionListener);

        Button logoutButton = findViewById(R.id.logout_button);
        SessionManager sessionManager = new SessionManager(this);

        backButton.setOnClickListener(v -> {

                        finish();
        });

        logoutButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        sessionManager.logout();
                        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(DisplayWeightLogActivity.this, StartActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finishAffinity();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void loadUserProfile() {
        personalEmailTextView.setText(currentUser.getEmail());
        String userName = currentUser.getDisplayName();
        if (userName != null && !userName.isEmpty()) {
            profileTitle.setText("Hello, " + userName);
            personalNameEditText.setText(userName);
        } else {
            profileTitle.setText("Hello, User");
        }

        // load age & height
        DocumentReference userDocRef = db.collection("users").document(currentUser.getUid());
        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                if (documentSnapshot.contains("age")) {
                    personalAgeEditText.setText(String.valueOf(documentSnapshot.getLong("age")));
                }
                if (documentSnapshot.contains("height")) {
                    personalHeightEditText.setText(String.valueOf(documentSnapshot.get("height")));
                }
                calculateAndDisplayBmi();
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error loading user profile from Firestore", e));
    }

    private void saveUserProfile() {
        String name = personalNameEditText.getText().toString().trim();
        String ageStr = personalAgeEditText.getText().toString().trim();
        String heightStr = personalHeightEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            personalNameEditText.setError("Name cannot be empty");
            personalNameEditText.requestFocus();
            return;
        }

        // save names into fire auth for & purposes
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        currentUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        profileTitle.setText("Hello, " + name);
                        Toast.makeText(DisplayWeightLogActivity.this, "Profile updated!", Toast.LENGTH_SHORT).show();
                    }
                });

        // age & height should be in firestore
        Map<String, Object> userData = new HashMap<>();
        if (!ageStr.isEmpty()) userData.put("age", Integer.parseInt(ageStr));
        if (!heightStr.isEmpty()) userData.put("height", Double.parseDouble(heightStr));

        if (!userData.isEmpty()) {
            db.collection("users").document(currentUser.getUid())
                    .set(userData, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> calculateAndDisplayBmi())
                    .addOnFailureListener(e -> {
                        Toast.makeText(DisplayWeightLogActivity.this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error saving user profile to Firestore", e);
                    });
        }
    }

    private void setupWeightChart() {
        weightChart.getDescription().setEnabled(false);
        weightChart.setDrawGridBackground(false);
        weightChart.setNoDataText("No weight data logged yet.");
        weightChart.getLegend().setEnabled(false);

        weightChart.setTouchEnabled(false);
        weightChart.setDragEnabled(false);
        weightChart.setScaleEnabled(false);

        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setYOffset(5f);

        YAxis leftAxis = weightChart.getAxisLeft();
        leftAxis.setGranularity(10f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setXOffset(20f);

        weightChart.getAxisRight().setEnabled(false);
    }

    private void updateWeightChart() {
        if (weightLogList == null || weightLogList.isEmpty()) {
            weightChart.clear();
            weightChart.invalidate();
            return;
        }

        ArrayList<WeightLog> chartList = new ArrayList<>(weightLogList);
        Collections.reverse(chartList);

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());

        for (int i = 0; i < chartList.size(); i++) {
            WeightLog log = chartList.get(i);
            entries.add(new Entry(i, (float) log.getWeight()));
            xLabels.add(sdf.format(log.getDate()));
        }

        weightChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));

        LineDataSet dataSet = new LineDataSet(entries, "Weight (kg)");
        dataSet.setColor(ContextCompat.getColor(this, R.color.munch_bangladesh_green));
        dataSet.setDrawValues(false);
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.munch_bangladesh_green));
        dataSet.setLineWidth(4f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);

        LineData lineData = new LineData(dataSet);
        weightChart.setData(lineData);
        weightChart.invalidate();
    }


    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final Drawable deleteIcon = ContextCompat.getDrawable(DisplayWeightLogActivity.this, android.R.drawable.ic_menu_delete);
            private final ColorDrawable background = new ColorDrawable(Color.RED);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                WeightLog logToDelete = weightLogList.get(position);
                weightLogList.remove(position);
                weightLogAdapter.notifyItemRemoved(position);
                updateWeightChart();
                deleteWeightLogFromFirestore(logToDelete, position);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.7f;
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View itemView = viewHolder.itemView;

                if (dX < 0) {
                    int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                    int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                    int iconRight = itemView.getRight() - iconMargin;
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    deleteIcon.draw(c);
                }
            }
        }).attachToRecyclerView(weightLogsRecyclerView);
    }

    private void deleteWeightLogFromFirestore(WeightLog logToDelete, int position) {
        if (currentUser == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .collection("personal_weight_logs")
                .whereEqualTo("date", logToDelete.getDate())
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentReference docRef = queryDocumentSnapshots.getDocuments().get(0).getReference();
                        docRef.delete()
                                .addOnSuccessListener(aVoid -> {
                                    Snackbar.make(weightLogsRecyclerView, "Log deleted", Snackbar.LENGTH_LONG)
                                            .setAction("UNDO", view -> {
                                                weightLogList.add(position, logToDelete);
                                                weightLogAdapter.notifyItemInserted(position);
                                                updateWeightChart();
                                                db.collection("users").document(currentUser.getUid())
                                                        .collection("personal_weight_logs").add(logToDelete);
                                            }).show();
                                });
                    }
                });
    }

    private void logWeight() {
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

        if (currentUser == null) {
            Toast.makeText(this, "Error: You must be logged in to log weight.", Toast.LENGTH_SHORT).show();
            return;
        }

        Date currentDate = new Date();
        WeightLog newLog = new WeightLog(currentDate, weightValue);

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

                        // if log the same day aready exists
                        DocumentReference docRef = queryDocumentSnapshots.getDocuments().get(0).getReference();
                        docRef.update("weight", weightValue)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(DisplayWeightLogActivity.this, "Weight updated successfully!", Toast.LENGTH_SHORT).show();
                                    personalWeightEditText.setText(""); // Clear the input field
                                    fetchWeightLogs(); // Refresh the list and chart
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(DisplayWeightLogActivity.this, "Failed to update weight.", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "Error updating document", e);
                                });
                    } else {
                        // no logs today
                        weightLogsCollection.add(newLog)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(DisplayWeightLogActivity.this, "Weight logged successfully!", Toast.LENGTH_SHORT).show();
                                    personalWeightEditText.setText(""); // Clear the input field
                                    fetchWeightLogs(); // Refresh the list and chart to show the new log
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(DisplayWeightLogActivity.this, "Failed to log weight.", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "Error adding document", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DisplayWeightLogActivity.this, "Failed to check for existing log.", Toast.LENGTH_SHORT).show();
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
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching weight logs", e));
        }
    }

    private void calculateAndDisplayBmi() {
        String heightStr = personalHeightEditText.getText().toString().trim();
        if (heightStr.isEmpty() || weightLogList == null || weightLogList.isEmpty()) {
            TextView bmiValueTextView = findViewById(R.id.bmi_value);
            bmiValueTextView.setText("-");
            return;
        }

        try {
            double heightInCm = Double.parseDouble(heightStr);
            if (heightInCm <= 0) {
                return; // Height must be positive
            }
            double heightInM = heightInCm / 100.0;
            double latestWeight = weightLogList.get(0).getWeight(); // List is sorted descending

            double bmi = latestWeight / (heightInM * heightInM);
            DecimalFormat df = new DecimalFormat("#.#");

            TextView bmiValueTextView = findViewById(R.id.bmi_value);
            bmiValueTextView.setText(df.format(bmi));

        } catch (NumberFormatException e) {
            Log.e(TAG, "Cannot parse height or weight for BMI calculation.", e);
        }
    }

}

