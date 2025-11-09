package com.example.munchai.frontend;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import android.view.View;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.munchai.R;
import com.example.munchai.backend.SessionManager;
import com.example.munchai.backend.media.PhotoCaptureManager;
import com.example.munchai.backend.media.PhotoStore;
import com.example.munchai.backend.GeminiRequest;
import com.example.munchai.model.NutritionFacts;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.concurrent.Executors;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executor;

public class MealActivity extends AppCompatActivity
{
    private EditText nameEt, qtyEt;
    private Spinner unitSp, mealSp;
    private ActivityResultLauncher<Intent> weightScaleLauncher;
    private Uri currentPhotoUri;
    private EditText weightEt, caloriesEt, fatEt, proteinEt, carbsEt;
    private TextView dateTv;
    private ImageView photoIv;
    private Button retakeBtn, toWeightBtn, saveBtn, cancelBtn;

    private SessionManager session;
    private int selYear, selMonth, selDay;

    private PhotoCaptureManager photoMgr;

    private final SimpleDateFormat isoUtc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_screen);

        isoUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
        session = new SessionManager(this);

        photoIv = findViewById(R.id.photo_preview);
        retakeBtn = findViewById(R.id.retake_button);
        toWeightBtn = findViewById(R.id.to_weight);

        nameEt = findViewById(R.id.input_food_name);
        qtyEt  = findViewById(R.id.input_calories);
        unitSp = findViewById(getResources().getIdentifier("spinner_unit", "id", getPackageName())); //change this (can't find unit spinner id)
        mealSp = findViewById(R.id.spinner_meal);
        dateTv = findViewById(R.id.text_date_value);

        saveBtn = findViewById(R.id.save_button);
        cancelBtn = findViewById(R.id.cancel_button);

        enableForm(false);

        if (unitSp != null) {
            ArrayAdapter<CharSequence> unitAd = ArrayAdapter.createFromResource(
                    this, R.array.units_array, android.R.layout.simple_spinner_item);
            unitAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            unitSp.setAdapter(unitAd);
        }

        ArrayAdapter<CharSequence> mealAd = ArrayAdapter.createFromResource(
                this, R.array.meals_array, android.R.layout.simple_spinner_item);
        mealAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mealSp.setAdapter(mealAd);

        Calendar now = Calendar.getInstance();
        selYear = now.get(Calendar.YEAR);
        selMonth = now.get(Calendar.MONTH);
        selDay = now.get(Calendar.DAY_OF_MONTH);
        dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", selDay, selMonth + 1, selYear));
        dateTv.setOnClickListener(v -> showDatePicker());

        saveBtn.setOnClickListener(v -> saveLog());
        cancelBtn.setOnClickListener(v -> finish());
        //log meal
        toWeightBtn.setOnClickListener(v -> startActivity(new Intent(this, WeightScaleActivity.class)));


        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Log Meal");
        }

        photoMgr = new PhotoCaptureManager(
                this,
                photoIv,
                new PhotoStore(this),
                new PhotoCaptureManager.Callbacks() {
                    @Override
                    public void onPhotoReady(Uri uri) {
                        currentPhotoUri = uri; // Store the URI
                        Intent intent = new Intent(MealActivity.this, WeightScaleActivity.class);
                        weightScaleLauncher.launch(intent);
                    }
                    @Override
                    public void onCaptureCanceled() {
                        Toast.makeText(MealActivity.this,
                                "Photo required to log a meal", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );
        photoMgr.register();
        retakeBtn.setOnClickListener(v -> photoMgr.retake());
        photoMgr.startCapture();

        weightScaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String weightStr = result.getData().getStringExtra(WeightScaleActivity.EXTRA_WEIGHT);
                        if (weightStr != null && !weightStr.isEmpty() && currentPhotoUri != null) {
                            // We have the weight and the photo URI, now call Gemini
                            Toast.makeText(this, "Analyzing image...", Toast.LENGTH_LONG).show();
                            callGeminiApi(currentPhotoUri, weightStr);

                            // Show the taken photo in the preview
                            photoIv.setImageURI(currentPhotoUri);
                        }
                    }
                    else {
                        // Handle the case where the user comes back without a weight
                        Toast.makeText(this, "Weight measurement was cancelled.", Toast.LENGTH_SHORT).show();
                        // You might want to allow retaking the photo or exiting
                        retakeBtn.setVisibility(View.VISIBLE);
                    }
                }
        );

    }

    private void callGeminiApi(Uri photoUri, String weight) {
        Executor executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                NutritionFacts facts = GeminiRequest.fetchNutritionFactsFromUri(this, photoUri, weight);
                handler.post(() -> populateFormWithNutritionData(facts, weight));
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    Toast.makeText(MealActivity.this, "Failed to get nutrition data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    enableForm(true); // Allow manual entry
                });
            }
        });
    }

    private void populateFormWithNutritionData(NutritionFacts facts, String weight) {
        if (facts == null) return;

        //PARSE OBJECTS INTO THE FIELDS
        nameEt.setText(facts.name);
        weightEt.setText(weight);
        caloriesEt.setText(facts.calories != null ? String.valueOf(facts.calories) : "");
        fatEt.setText(facts.totalFatG != null ? String.valueOf(facts.totalFatG) : "");
        proteinEt.setText(facts.proteinG != null ? String.valueOf(facts.proteinG) : "");
        carbsEt.setText(facts.totalCarbG != null ? String.valueOf(facts.totalCarbG) : "");

        enableForm(true); // Enable the form for editing
        Toast.makeText(this, "Nutrition data loaded!", Toast.LENGTH_SHORT).show();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.set(selYear, selMonth, selDay);

        new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    selYear = y; selMonth = m; selDay = d;
                    dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void enableForm(boolean enabled) {
        nameEt.setEnabled(enabled);
        qtyEt.setEnabled(enabled);
        if (unitSp != null) unitSp.setEnabled(enabled);
        mealSp.setEnabled(enabled);
        dateTv.setEnabled(enabled);
        saveBtn.setEnabled(enabled);
    }

    private void saveLog() {
        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!photoMgr.hasPhoto()) {
            Toast.makeText(this, "Please take a photo first", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEt.getText().toString().trim();
        String qtyStr = qtyEt.getText().toString().trim();
        String unit = (unitSp != null && unitSp.getSelectedItem() != null)
                ? (String) unitSp.getSelectedItem() : "";
        String meal = (String) mealSp.getSelectedItem();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Enter food name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(qtyStr)) {
            Toast.makeText(this, "Enter food quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        double qty;
        try {
            qty = Double.parseDouble(qtyStr);
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException nfe) {
            Toast.makeText(this, "Quantity must be a positive number", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "User not logged in, please sign in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar localMidnight = Calendar.getInstance();
        localMidnight.set(selYear, selMonth, selDay, 0, 0, 0);
        localMidnight.set(Calendar.MILLISECOND, 0);
        String loggedAtIso = isoUtc.format(localMidnight.getTime());

        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        //pre-create doc to get an ID
        DocumentReference docRef = fs.collection("users").document(uid).collection("food_logs").document();
        String logId = docRef.getId();

        Map<String, Object> base = new HashMap<>();
        base.put("name", name);
        base.put("unit", unit);
        base.put("qty", qty);
        base.put("meal", meal);
        base.put("logged_at", loggedAtIso);
        base.put("imageUrl", null);

        // save base doc (works offline)
        docRef.set(base, SetOptions.merge())
                .addOnSuccessListener(v -> uploadPhoto(uid, logId, docRef))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void uploadPhoto(String uid, String logId, DocumentReference docRef) {
        Uri photoUri = photoMgr.getCurrentUri();
        if (photoUri == null) {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            navigateHome();
            return;
        }

        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("users").child(uid).child("food_images").child(logId + ".jpg");
        ref.putFile(photoUri)
                .continueWithTask(task -> {
                   if (!task.isSuccessful()) throw task.getException();
                   return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("imageUrl", uri.toString());
                    docRef.set(upd, SetOptions.merge());
                    Toast.makeText(this, "Saved with photo", Toast.LENGTH_SHORT).show();
                    navigateHome();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    navigateHome();
                });
    }

    private void navigateHome() {
        Intent intent = new Intent(MealActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
