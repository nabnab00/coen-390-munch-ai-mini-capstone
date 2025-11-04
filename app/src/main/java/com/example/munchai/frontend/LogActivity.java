package com.example.munchai.frontend;

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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.munchai.R;
import com.example.munchai.backend.AppDatabaseHelper;
import com.example.munchai.backend.SessionManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LogActivity extends AppCompatActivity
{
    private EditText nameEt, qtyEt;
    private Spinner unitSp, mealSp;
    private TextView dateTv;
    private ImageView photoIv;
    private Button retakeBtn;
    private AppDatabaseHelper db;
    private SessionManager session;
    private int selYear, selMonth, selDay;
    private Uri photoUri;
    private File photoFile;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
                if (result != null && result) {
                    photoIv.setImageURI(photoUri);
                    enableForm(true);
                } else {
                    Toast.makeText(this, "Photo required to log a meal", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_screen);

        db = new AppDatabaseHelper(this);
        session = new SessionManager(this);

        photoIv = findViewById(R.id.photo_preview);
        retakeBtn = findViewById(R.id.retake_button);
        Button toWeight = findViewById(R.id.to_weight);

        nameEt = findViewById(R.id.input_food_name);
        qtyEt  = findViewById(R.id.input_qty);
        unitSp = findViewById(R.id.spinner_unit);
        mealSp = findViewById(R.id.spinner_meal);
        dateTv = findViewById(R.id.text_date_value);

        enableForm(false);

        ArrayAdapter<CharSequence> unitAd = ArrayAdapter.createFromResource(
                this, R.array.units_array, android.R.layout.simple_spinner_item);
        unitAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSp.setAdapter(unitAd);

        ArrayAdapter<CharSequence> mealAd = ArrayAdapter.createFromResource(
                this, R.array.meals_array, android.R.layout.simple_spinner_item);
        mealAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mealSp.setAdapter(mealAd);

        Calendar now = Calendar.getInstance();
        selYear = now.get(Calendar.YEAR);
        selMonth = now.get(Calendar.MONTH);
        selDay = now.get(Calendar.DAY_OF_MONTH);
        dateTv.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", selDay, selMonth + 1, selYear));

        Button saveBtn = findViewById(R.id.save_button);
        Button cancelBtn = findViewById(R.id.cancel_button);
        saveBtn.setOnClickListener(v -> saveLog());
        cancelBtn.setOnClickListener(v -> finish());
        retakeBtn.setOnClickListener(v -> startCameraCapture());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Log Meal");
        }

        //log meal
        toWeight.setOnClickListener(v -> {
            Intent intent = new Intent(LogActivity.this, WeightActivity.class);
            startActivity(intent);
        });

        startCameraCapture();
    }

    private void enableForm(boolean enabled) {
        nameEt.setEnabled(enabled);
        qtyEt.setEnabled(enabled);
        unitSp.setEnabled(enabled);
        mealSp.setEnabled(enabled);
        dateTv.setEnabled(enabled);
        findViewById(R.id.save_button).setEnabled(enabled);
    }

    private void startCameraCapture() {
        try {
            photoUri = createImageUri();
            takePicture.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, "Unable to start camera", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private Uri createImageUri() throws IOException {
        File dir = new File(getCacheDir(), "images");
        if (!dir.exists()) dir.mkdirs();
        photoFile = File.createTempFile("meal_", ".jpg", dir);
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
    }

    private void saveLog() {
        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (photoUri == null) {
            Toast.makeText(this, "Please take a photo first", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEt.getText().toString().trim();
        String qtyStr = qtyEt.getText().toString().trim();
        String unit = (String) unitSp.getSelectedItem();
        String meal = (String) mealSp.getSelectedItem();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(qtyStr)) {
            Toast.makeText(this, "Enter food name and quantity", Toast.LENGTH_SHORT).show();
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

        String tsIso = selectedDateMidnightIsoUtc();

        long id = db.insertLog(
                session.getLoggedInUserId(), name, unit, qty, meal, tsIso
        );

        if (id > 0) {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();

            // TODO (next step): save photo path/uri to DB if you add a column for it.
            // We currently just capture for UI flow. The image file remains in cache.
            Intent intent = new Intent(LogActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private String selectedDateMidnightIsoUtc() {
        Calendar local = Calendar.getInstance();
        local.set(Calendar.YEAR, selYear);
        local.set(Calendar.MONTH, selMonth);
        local.set(Calendar.DAY_OF_MONTH, selDay);
        local.set(Calendar.HOUR_OF_DAY, 0);
        local.set(Calendar.MINUTE, 0);
        local.set(Calendar.SECOND, 0);
        local.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return iso.format(new Date(local.getTimeInMillis()));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
