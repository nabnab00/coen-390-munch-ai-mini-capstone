package com.example.munchai.frontend;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import android.view.View;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import java.util.Date;
import java.util.concurrent.Executors;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executor;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public class MealActivity extends AppCompatActivity
{
    private EditText nameEt;
    private Spinner unitSp, mealSp;
    private ActivityResultLauncher<Intent> weightScaleLauncher;
    private Uri currentPhotoUri;
    private EditText weightEt, caloriesEt, fatEt, proteinEt, carbsEt, sodiumEt, vitaminAEt, vitaminBEt, vitaminCEt, ironEt;
    private TextView dateTv;
    private ImageView photoIv;
    private Button retakeBtn, weightAgainBtn, saveBtn, cancelBtn;

    private SessionManager session;
    private int selYear, selMonth, selDay;

    private PhotoCaptureManager photoMgr;

    private final SimpleDateFormat isoUtc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;

    // Button state + anti-spam guards
    private volatile boolean isSaving = false;
    private volatile boolean isCancelling = false;
    private String saveBtnOriginalText = null;
    private final AtomicBoolean saveClickGuard = new AtomicBoolean(false);
    private long lastSaveClickAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mealpage);
        session = new SessionManager(this);

        photoIv = findViewById(R.id.photo_preview);
        retakeBtn = findViewById(R.id.retake_button);
        weightAgainBtn = findViewById(R.id.weight_again_button);

        // Fields
        nameEt = findViewById(R.id.input_food_name);
        weightEt = findViewById(R.id.input_weight);
        caloriesEt = findViewById(R.id.input_calories);
        fatEt = findViewById(R.id.input_fat);
        proteinEt = findViewById(R.id.input_protein);
        carbsEt = findViewById(R.id.input_carbohydrates);
        sodiumEt = findViewById(R.id.input_sodium);
        vitaminAEt = findViewById(R.id.input_vitaminA);
        vitaminBEt = findViewById(R.id.input_vitaminB);
        vitaminCEt = findViewById(R.id.input_vitaminC);
        ironEt = findViewById(R.id.input_iron);
        mealSp = findViewById(R.id.spinner_meal);
        dateTv = findViewById(R.id.text_date_value);
        saveBtn = findViewById(R.id.save_button);
        cancelBtn = findViewById(R.id.cancel_button);

        if (saveBtn != null) saveBtnOriginalText = saveBtn.getText().toString();

        enableForm(false); // locked until we have picture/AI or manual entry

        if (unitSp != null) {
            ArrayAdapter<CharSequence> unitAd = ArrayAdapter.createFromResource(
                    this, R.array.units_array, android.R.layout.simple_spinner_item);
            unitAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            unitSp.setAdapter(unitAd);
        }

        ArrayAdapter<CharSequence> mealAd = ArrayAdapter.createFromResource(
                this, R.array.meals_array, android.R.layout.simple_spinner_item);
        mealAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (mealSp != null) mealSp.setAdapter(mealAd);

        Calendar now = Calendar.getInstance();
        selYear = now.get(Calendar.YEAR);
        selMonth = now.get(Calendar.MONTH);
        selDay = now.get(Calendar.DAY_OF_MONTH);

        if (dateTv != null) {
            dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", selDay, selMonth + 1, selYear));
            dateTv.setOnClickListener(v -> showDatePicker());
        }

        // Debounced + guarded click
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                long nowMs = SystemClock.elapsedRealtime();
                if (nowMs - lastSaveClickAt < 900) return; // debounce
                lastSaveClickAt = nowMs;

                if (!saveClickGuard.compareAndSet(false, true)) return; // already saving

                // Final eligibility check before saving
                if (!areRequiredFieldsValid()) {
                    // keep disabled with reason
                    setSaveButtonDisabledWithReason(requiredFieldsReason());
                    saveClickGuard.set(false);
                    return;
                }

                setSaveButtonLoading();
                saveBtn.setClickable(false);
                if (cancelBtn != null) cancelBtn.setEnabled(false);
                if (retakeBtn != null) retakeBtn.setEnabled(false);
                if (weightAgainBtn != null) weightAgainBtn.setEnabled(false);
                enableForm(false);
                saveLog();
            });

            saveBtn.setOnTouchListener((v, e) -> !v.isEnabled());
        }

        if (cancelBtn != null) cancelBtn.setOnClickListener(v -> finish());

        weightAgainBtn.setOnClickListener(v -> {
            if (currentPhotoUri != null) {
                Intent intent = new Intent(this, WeightScaleActivity.class);
                weightScaleLauncher.launch(intent);
            } else {
                startCameraWithPermissionCheck();
            }
        });


        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Log Meal");
        }

        requestCameraPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) {
                                openCamera();
                            } else {
                                Toast.makeText(
                                        this,
                                        "Camera permission is required to take a meal photo.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                );

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
        retakeBtn.setOnClickListener(v -> startCameraWithPermissionCheck());
        startCameraWithPermissionCheck();

        weightScaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String weightStr = result.getData().getStringExtra(WeightScaleActivity.EXTRA_WEIGHT);
                        String unitStr = result.getData().getStringExtra(WeightScaleActivity.EXTRA_UNIT);

                        if (weightStr != null && !weightStr.isEmpty() && currentPhotoUri != null) {
                            Toast.makeText(this, "Analyzing image...", Toast.LENGTH_LONG).show();
                            setSaveButtonDisabledWithReason("Analyzing…");
                            enableForm(false);
                            callGeminiApi(currentPhotoUri, weightStr, unitStr);
                            photoIv.setImageURI(currentPhotoUri);
                        }
                    } else {
                        Toast.makeText(this, "Weight measurement was cancelled.", Toast.LENGTH_SHORT).show();
                        retakeBtn.setVisibility(View.VISIBLE);
                        setSaveButtonDisabledWithReason("Retake photo to continue");
                    }
                }
        );

        // NEW: initial disabled state reason (since only name/weight might be present)
        setSaveButtonDisabledWithReason("Enter calories, fat, protein, carbs");
        attachEligibilityWatchers(); // live-enable when required fields are valid
        enableForm(true); // allow manual entry if user wants
    }

    // ---------- Camera helpers ----------
    private void startCameraWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        if (photoMgr != null) {
            photoMgr.startCapture();
        }
    }

    // ---------- AI call ----------
    private void callGeminiApi(Uri photoUri, String weight, String unit) {
        Executor executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                NutritionFacts facts = GeminiRequest.fetchNutritionFactsFromUri(this, photoUri, weight, unit);
                handler.post(() -> {
                    if (isFactsMeaningful(facts)) {
                        populateFormWithNutritionData(facts, weight);
                        enableForm(true);
                        updateSaveButtonEligibility(); // reevaluate after AI fills fields
                        Toast.makeText(this, "Nutrition data loaded!", Toast.LENGTH_SHORT).show();
                    } else {
                        showAiNullPictureError();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    Toast.makeText(MealActivity.this, "Failed to get nutrition data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    enableForm(true);
                    updateSaveButtonEligibility();
                });
            }
        });
    }

    private boolean isFactsMeaningful(NutritionFacts facts) {
        if (facts == null) return false;
        if (facts.name == null) return false;
        String n = facts.name.trim().toLowerCase(Locale.ROOT);
        return !(n.isEmpty() || n.equals("unknown") || n.equals("null") || n.equals("undefined"));
    }

    private void showAiNullPictureError() {clearNutritionFields(); // Clear out old data
        setSaveButtonDisabledWithReason("Retake photo or enter manually");
        enableForm(true); // allow manual entry
        Toast.makeText(this,
                "I couldn't identify the meal from the photo. Retake the picture or enter details manually.",
                Toast.LENGTH_LONG).show();
        if (retakeBtn != null) retakeBtn.setVisibility(View.VISIBLE);
        updateSaveButtonEligibility();
    }


    // ---------- Save button helpers ----------
    private void setSaveButtonEnabledNormal() {
        if (saveBtn == null) return;
        isSaving = false;
        saveBtn.setEnabled(true);
        saveBtn.setClickable(true);
        saveBtn.setAlpha(1f);
        if (saveBtnOriginalText != null) saveBtn.setText(saveBtnOriginalText);
        saveClickGuard.set(false);
    }

    private void setSaveButtonDisabledWithReason(String label) {
        if (saveBtn == null) return;
        isSaving = false;
        saveBtn.setEnabled(false);
        saveBtn.setClickable(false);
        saveBtn.setAlpha(0.5f);
        saveBtn.setText(label);
        // don’t reset saveClickGuard here if we’re mid-save; this is used mainly outside save flow
    }

    private void setCancelButtonDisabledWithReason(String label) {
        if (saveBtn == null) return;
        isCancelling = false;
        cancelBtn.setEnabled(false);
        cancelBtn.setClickable(false);
        cancelBtn.setAlpha(0.5f);
        cancelBtn.setText(label);
        // don’t reset saveClickGuard here if we’re mid-save; this is used mainly outside save flow
    }

    private void setSaveButtonLoading() {
        if (saveBtn == null) return;
        isSaving = true;
        saveBtn.setEnabled(false);
        saveBtn.setClickable(false);
        saveBtn.setAlpha(0.5f);
        saveBtn.setText("Saving…");
    }

    // ---------- Eligibility (NEW) ----------
    private void attachEligibilityWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateSaveButtonEligibility(); }
        };
        nameEt.addTextChangedListener(watcher);
        weightEt.addTextChangedListener(watcher);
        caloriesEt.addTextChangedListener(watcher);
        fatEt.addTextChangedListener(watcher);
        proteinEt.addTextChangedListener(watcher);
        carbsEt.addTextChangedListener(watcher);
    }

    private void updateSaveButtonEligibility() {
        if (isSaving) return; // don’t change state while saving

        if (!areRequiredFieldsValid()) {
            setSaveButtonDisabledWithReason(requiredFieldsReason());
        } else {
            setSaveButtonEnabledNormal();
        }
    }

    private boolean areRequiredFieldsValid() {
        // Required: name (non-empty), weight (>0), calories/fat/protein/carbs (>=0)
        String name = safeStr(nameEt);
        String w = safeStr(weightEt);
        String c = safeStr(caloriesEt);
        String f = safeStr(fatEt);
        String p = safeStr(proteinEt);
        String cb = safeStr(carbsEt);

        if (TextUtils.isEmpty(name)) return false;
        double weight;
        try { weight = Double.parseDouble(w); if (weight <= 0) return false; }
        catch (Exception e) { return false; }

        Double calories = parseNonNegative(c);
        Double fat = parseNonNegative(f);
        Double protein = parseNonNegative(p);
        Double carbs = parseNonNegative(cb);

        return calories != null && fat != null && protein != null && carbs != null;
    }

    private String requiredFieldsReason() {
        // Show a concise reason; you can make this more granular if you want per-field hints
        return "Enter calories, fat, protein, carbs";
    }

    private static String safeStr(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static Double parseNonNegative(String s) {
        if (TextUtils.isEmpty(s)) return null;
        try {
            double v = Double.parseDouble(s);
            return v >= 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Populate ----------
    private void populateFormWithNutritionData(NutritionFacts facts, String weight) {
        if (facts == null) return;

        nameEt.setText(facts.name);
        weightEt.setText(weight);
        caloriesEt.setText(facts.calories != null ? String.valueOf(facts.calories) : "");
        fatEt.setText(facts.totalFatG != null ? String.valueOf(facts.totalFatG) : "");
        proteinEt.setText(facts.proteinG != null ? String.valueOf(facts.proteinG) : "");
        carbsEt.setText(facts.totalCarbG != null ? String.valueOf(facts.totalCarbG) : "");
        sodiumEt.setText(facts.sodiumMg != null ? String.valueOf(facts.sodiumMg) : "");
        vitaminAEt.setText(facts.vitaminAPercent != null ? String.valueOf(facts.vitaminAPercent) : "");
        vitaminBEt.setText(facts.vitaminBPercent != null ? String.valueOf(facts.vitaminBPercent) : "");
        vitaminCEt.setText(facts.vitaminCPercent != null ? String.valueOf(facts.vitaminCPercent) : "");
        ironEt.setText(facts.ironPercent != null ? String.valueOf(facts.ironPercent) : "");
    }

    // ---------- Date ----------
    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.set(selYear, selMonth, selDay);

        new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    selYear = y;
                    selMonth = m;
                    selDay = d;
                    dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void enableForm(boolean enabled) {
        nameEt.setEnabled(enabled);
        weightEt.setEnabled(enabled);
        caloriesEt.setEnabled(enabled);
        fatEt.setEnabled(enabled);
        proteinEt.setEnabled(enabled);
        carbsEt.setEnabled(enabled);
        sodiumEt.setEnabled(enabled);
        vitaminAEt.setEnabled(enabled);
        vitaminBEt.setEnabled(enabled);
        vitaminCEt.setEnabled(enabled);
        ironEt.setEnabled(enabled);

        if (unitSp != null) unitSp.setEnabled(enabled);
        mealSp.setEnabled(enabled);
        dateTv.setEnabled(enabled);

        if (saveBtn != null) {
            saveBtn.setEnabled(enabled && !isSaving);
            saveBtn.setClickable(enabled && !isSaving);
            saveBtn.setAlpha((enabled && !isSaving) ? 1f : 0.5f);
            if (enabled && !isSaving && saveBtnOriginalText != null && areRequiredFieldsValid()) {
                saveBtn.setText(saveBtnOriginalText);
            } else if (enabled && !isSaving && !areRequiredFieldsValid()) {
                saveBtn.setText(requiredFieldsReason());
            }
        }
    }

    // ---------- Save ----------
    private void saveLog() {
        // UI already locked in onClick; validate anyway for safety
        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            setSaveButtonEnabledNormal();
            enableForm(true);
            finish();
            return;
        }
        if (!photoMgr.hasPhoto()) {
            Toast.makeText(this, "Please take a photo first", Toast.LENGTH_SHORT).show();
            setSaveButtonDisabledWithReason("Retake photo to continue");
            enableForm(true);
            return;
        }

        // Required fields are validated by areRequiredFieldsValid()
        if (!areRequiredFieldsValid()) {
            Toast.makeText(this, requiredFieldsReason(), Toast.LENGTH_SHORT).show();
            setSaveButtonDisabledWithReason(requiredFieldsReason());
            enableForm(true);
            return;
        }

        String name = nameEt.getText().toString().trim();
        String weightStr = weightEt.getText().toString().trim();
        String unit = (unitSp != null && unitSp.getSelectedItem() != null)
                ? (String) unitSp.getSelectedItem() : "";
        String meal = (String) mealSp.getSelectedItem();
        String calStr = caloriesEt.getText().toString().trim();
        String fatStr = fatEt.getText().toString().trim();
        String proStr = proteinEt.getText().toString().trim();
        String carbStr = carbsEt.getText().toString().trim();

        double weight = Double.parseDouble(weightStr);
        double calories = Double.parseDouble(calStr);
        double fat = Double.parseDouble(fatStr);
        double protein = Double.parseDouble(proStr);
        double carbs = Double.parseDouble(carbStr);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "User not logged in, please sign in first.", Toast.LENGTH_SHORT).show();
            setSaveButtonEnabledNormal();
            enableForm(true);
            return;
        }

        Date now = new Date();
        isoUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
        String loggedAtIso = isoUtc.format(now);

        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        // pre-create doc to get an ID
        DocumentReference docRef = fs.collection("users").document(uid).collection("food_logs").document();
        String logId = docRef.getId();

        Map<String, Object> base = new HashMap<>();
        base.put("name", name);
        base.put("unit", unit);
        base.put("weight", weight);
        base.put("meal", meal);
        base.put("logged_at", loggedAtIso);
        base.put("imageUrl", null);
        base.put("calories", calories);
        base.put("fat_g", fat);
        base.put("protein_g", protein);
        base.put("carb_g", carbs);

        docRef.set(base, SetOptions.merge())
                .addOnSuccessListener(v -> uploadPhoto(uid, logId, docRef))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setSaveButtonEnabledNormal();
                    enableForm(true);
                });
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
                    setSaveButtonEnabledNormal();
                    enableForm(true);
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

    private void clearNutritionFields() {
        nameEt.setText("");
        caloriesEt.setText("");fatEt.setText("");
        proteinEt.setText("");
        carbsEt.setText("");
        sodiumEt.setText("");
        vitaminAEt.setText("");
        vitaminBEt.setText("");
        vitaminCEt.setText("");
        ironEt.setText("");
        // Do NOT clear weightEt here, as it may have been just measured
    }
}
