package com.example.munchai.frontend;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.*;
import android.content.SharedPreferences;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.example.munchai.R;

public class WeightActivity extends AppCompatActivity {

    EditText ipInput;
    Button saveBtn, refreshBtn, tareBtn;
    TextView weightText;
    SharedPreferences prefs;
    String baseUrl;

    ScheduledExecutorService scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weightpage);

        ipInput = findViewById(R.id.ipInput);
        saveBtn = findViewById(R.id.saveBtn);
        refreshBtn = findViewById(R.id.refreshBtn);
        tareBtn = findViewById(R.id.tareBtn);
        weightText = findViewById(R.id.weightText);
        prefs = getSharedPreferences("ESP32", MODE_PRIVATE);

        String savedIp = prefs.getString("ip", "");
        ipInput.setText(savedIp);
        if (!savedIp.isEmpty()) baseUrl = "http://" + savedIp;

        saveBtn.setOnClickListener(v -> {
            String ip = ipInput.getText().toString().trim();
            if (!ip.isEmpty()) {
                prefs.edit().putString("ip", ip).apply();
                baseUrl = "http://" + ip;
                Toast.makeText(this, "IP saved", Toast.LENGTH_SHORT).show();
            }
        });

        refreshBtn.setOnClickListener(v -> new Thread(this::getWeight).start());
        tareBtn.setOnClickListener(v -> new Thread(this::tareScale).start());

        // Automatically refresh weight every 1 second
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> getWeight(), 0, 1, TimeUnit.SECONDS);
    }

    private void getWeight() {
        if (baseUrl == null) return;
        try {
            URL url = new URL(baseUrl + "/weight");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject obj = new JSONObject(sb.toString());
            double weight = obj.getDouble("weight_g");

            runOnUiThread(() -> weightText.setText(String.format("%.2f g", weight)));
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error reading weight", Toast.LENGTH_SHORT).show());
        }
    }

    private void tareScale() {
        if (baseUrl == null) return;
        try {
            URL url = new URL(baseUrl + "/tare");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.getInputStream().close();
            runOnUiThread(() -> Toast.makeText(this, "Tared", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error taring", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduler != null) scheduler.shutdownNow();
    }
}
