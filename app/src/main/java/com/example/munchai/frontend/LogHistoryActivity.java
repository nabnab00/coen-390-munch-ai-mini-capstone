package com.example.munchai.frontend;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.munchai.R;
import com.example.munchai.backend.SessionManager;
import com.example.munchai.frontend.adapter.FoodLogAdapter;
import com.example.munchai.model.FoodLogRow;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class LogHistoryActivity extends AppCompatActivity {
    private SessionManager session;
    private FoodLogAdapter adapter;
    private TextView empty;

    private ListenerRegistration reg;

    private final List<FoodLogRow> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.foodlogpage);

        session = new SessionManager(this);


        RecyclerView rv = findViewById(R.id.logs_list);
        empty = findViewById(R.id.empty_state);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FoodLogAdapter();
        rv.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        reg = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("food_logs")
                .orderBy("logged_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        Toast.makeText(this, "Error: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    rows.clear();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            String name = safe(d.getString("name"));
                            String unit = safe(d.getString("unit"));
                            Double weight = d.getDouble("weight");
                            String meal = safe(d.getString("meal"));
                            String atIso = safe(d.getString("logged_at"));
                            Double calories = d.getDouble("calories");
                            Double fatG     = d.getDouble("fat_g");
                            Double proteinG = d.getDouble("protein_g");
                            Double carbG    = d.getDouble("carb_g");


                            rows.add(new FoodLogRow(
                                    name, unit, weight != null ? weight : 0d, meal, atIso, calories, fatG, proteinG, carbG
                            ));
                        }
                    }
                    adapter.setItems(rows);
                    empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
