package com.example.munchai.frontend;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.munchai.R;
import com.example.munchai.frontend.adapter.WeightLogAdapter;
import com.example.munchai.model.WeightLog;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private TextView profileTitle;
    private EditText personalWeightEditText;
    private Button logWeightButton;
    private RecyclerView weightLogsRecyclerView; // Changed from ListView
    private LineChart weightChart; // Add LineChart variable

    private WeightLogAdapter weightLogAdapter;
    private ArrayList<WeightLog> weightLogList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profilepage);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        profileTitle = findViewById(R.id.profile_title);
        personalWeightEditText = findViewById(R.id.personal_weight);
        logWeightButton = findViewById(R.id.profile_save);
        weightLogsRecyclerView = findViewById(R.id.weight_logs_list); // Updated ID
        weightChart = findViewById(R.id.weight_chart); // Initialize LineChart
        weightLogList = new ArrayList<>();

        // Setup RecyclerView
        weightLogsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        weightLogAdapter = new WeightLogAdapter(this, weightLogList);
        weightLogsRecyclerView.setAdapter(weightLogAdapter);

        // Setup Chart
        setupWeightChart();

        // Attach ItemTouchHelper for swipe-to-delete
        setupSwipeToDelete();

        if (currentUser != null) {
            String userName = currentUser.getDisplayName();
            if (userName != null && !userName.isEmpty()) {
                profileTitle.setText("Hello, " + userName);
            } else {
                profileTitle.setText("Hello, User");
            }
            fetchWeightLogs();
        } else {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            finish();
        }

        logWeightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logWeight();
            }
        });
    }

    private void setupWeightChart() {
        weightChart.getDescription().setEnabled(false);
        weightChart.setDrawGridBackground(false);
        weightChart.setNoDataText("No weight data logged yet.");
        weightChart.getLegend().setEnabled(false); // Remove the legend

        // Configure X-Axis
        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false); // Hide the x-axis line itself

        // Configure Y-Axis
        YAxis leftAxis = weightChart.getAxisLeft();
        leftAxis.setGranularity(10f); // Set Y-axis increments to 10 kg
        leftAxis.setDrawGridLines(false); // Remove horizontal grid lines
        leftAxis.setDrawAxisLine(false); // Hide the y-axis line itself

        weightChart.getAxisRight().setEnabled(false); // Disable the right Y-axis
    }

    private void updateWeightChart() {
        if (weightLogList == null || weightLogList.isEmpty()) {
            weightChart.clear();
            weightChart.invalidate(); // Refresh the chart
            return;
        }

        // Sort the list by date to ensure the chart is drawn correctly
        Collections.sort(weightLogList, (log1, log2) -> log1.getDate().compareTo(log2.getDate()));

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());

        for (int i = 0; i < weightLogList.size(); i++) {
            WeightLog log = weightLogList.get(i);
            // Use timestamp for the x-value for accurate date representation
            entries.add(new Entry(i, (float) log.getWeight()));
            xLabels.add(sdf.format(log.getDate()));
        }

        weightChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));

        LineDataSet dataSet = new LineDataSet(entries, "Weight (kg)");
        dataSet.setColor(ContextCompat.getColor(this, R.color.munch_bangladesh_green));
        dataSet.setValueTextColor(ContextCompat.getColor(this, R.color.munch_mountain_meadow));
        // Style the dataSet (e.g., circles, line thickness)
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.munch_bangladesh_green));
        dataSet.setLineWidth(3f); // Make the line thicker
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);


        LineData lineData = new LineData(dataSet);
        weightChart.setData(lineData);
        weightChart.invalidate(); // Refresh the chart
    }


    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final Drawable deleteIcon = ContextCompat.getDrawable(ProfileActivity.this, android.R.drawable.ic_menu_delete);
            private final ColorDrawable background = new ColorDrawable(Color.RED);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // We are not moving items up/down
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                // Ensure position is valid before proceeding
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                WeightLog logToDelete = weightLogList.get(position);

                // Remove from the list and notify adapter
                weightLogList.remove(position);
                weightLogAdapter.notifyItemRemoved(position);
                updateWeightChart(); // Update chart after deletion

                // Delete from Firestore
                deleteWeightLogFromFirestore(logToDelete, position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                // This is the corrected line
                View itemView = viewHolder.itemView;

                if (dX < 0) { // Swiping to the left
                    // Calculate position of delete icon
                    int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                    int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                    int iconRight = itemView.getRight() - iconMargin;

                    // Draw the delete icon
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
                                    // Show Snackbar with Undo option
                                    Snackbar.make(weightLogsRecyclerView, "Log deleted", Snackbar.LENGTH_LONG)
                                            .setAction("UNDO", view -> {
                                                // Undo was clicked, re-add the log
                                                weightLogList.add(position, logToDelete);
                                                weightLogAdapter.notifyItemInserted(position);
                                                updateWeightChart(); // Update chart after undo
                                                // Re-add to Firestore as well
                                                db.collection("users").document(currentUser.getUid())
                                                        .collection("personal_weight_logs").add(logToDelete);
                                            }).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileActivity.this, "Failed to delete log.", Toast.LENGTH_SHORT).show();
                                    // Restore UI if delete fails
                                    weightLogList.add(position, logToDelete);
                                    weightLogAdapter.notifyItemInserted(position);
                                    updateWeightChart(); // Restore chart as well
                                });
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error finding document to delete", e));
    }


    private void logWeight() {
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

        // Get the start and end of the current day
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startDate = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date endDate = cal.getTime();

        // Query for logs from the current day
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
                                    Toast.makeText(ProfileActivity.this, "Weight updated successfully!", Toast.LENGTH_SHORT).show();
                                    personalWeightEditText.setText(""); // Clear the input field
                                    fetchWeightLogs(); // Refresh the list and chart
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileActivity.this, "Failed to update weight.", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "Error updating document", e);
                                });
                    } else {
                        // no logs today
                        weightLogsCollection.add(newLog)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(ProfileActivity.this, "Weight logged successfully!", Toast.LENGTH_SHORT).show();
                                    personalWeightEditText.setText(""); // Clear the input field
                                    fetchWeightLogs(); // Refresh the list and chart to show the new log
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
        if (currentUser == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .collection("personal_weight_logs")
                .orderBy("date", Query.Direction.DESCENDING) // Order by date
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    weightLogList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        WeightLog log = document.toObject(WeightLog.class);
                        weightLogList.add(log);
                    }
                    weightLogAdapter.notifyDataSetChanged();
                    updateWeightChart(); // Update the chart with the fetched data
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error getting documents: ", e);
                    Toast.makeText(ProfileActivity.this, "Failed to load weight logs.", Toast.LENGTH_SHORT).show();
                });
    }
}
