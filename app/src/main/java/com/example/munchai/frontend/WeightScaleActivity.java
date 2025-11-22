package com.example.munchai.frontend;

import com.example.munchai.R;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightScaleActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMS = 2001;
    private static final String TARGET_NAME = "ESP32-Scale";
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TextView statusText;
    private EditText macInput, weightText;
    private Button connectBtn, tareBtn, resetBtn, saveBtn;
    private Spinner unitSpinner;
    public static final String EXTRA_WEIGHT = "com.example.munchai.WEIGHT";
    public static final String EXTRA_UNIT = "com.example.munchai.UNIT";

    private BluetoothAdapter btAdapter;
    private BluetoothSocket socket;
    private OutputStream out;
    private InputStream in;

    private final ExecutorService exec = Executors.newCachedThreadPool();
    private volatile boolean reading = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.weightpage);

        statusText  = findViewById(R.id.statusText);
        weightText  = findViewById(R.id.weightText);
        macInput    = findViewById(R.id.macInput);
        connectBtn  = findViewById(R.id.connectBtn);
        tareBtn     = findViewById(R.id.tareBtn);
        resetBtn    = findViewById(R.id.resetBtn);
        saveBtn     = findViewById(R.id.weightsave);
        unitSpinner = findViewById(R.id.unitSpinner);

        setupSpinner();

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            toast("Bluetooth not supported");
            finish();
            return;
        }

        connectBtn.setOnClickListener(v -> {
            if (isSPlus() && !hasBtPerms()) {
                requestBtPerms();
            } else {
                connectBluetooth();
            }
        });

        saveBtn.setOnClickListener(v -> {
            String currentWeight = weightText.getText().toString();
            // Extract just the number, removing units like " g"
            String numericWeight = currentWeight.replaceAll("[^0-9.]", "");

            if (numericWeight.isEmpty() || Double.parseDouble(numericWeight) == 0) {
                Toast.makeText(this, "No weight measured", Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedUnit = unitSpinner.getSelectedItem().toString();

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_WEIGHT, numericWeight);
            resultIntent.putExtra(EXTRA_UNIT, selectedUnit);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });

        // Tare / reset send commands to ESP32 (same as before)
        tareBtn.setOnClickListener(v -> sendCmd("t\n"));
        resetBtn.setOnClickListener(v -> sendCmd("r\n"));
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.units_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(adapter);

        int saved = getSharedPreferences("scale_prefs", MODE_PRIVATE)
                .getInt("unit_idx", 0);
        unitSpinner.setSelection(saved);

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view,
                                       int pos,
                                       long id) {
                char cmd = idxToCmd(pos);
                sendCmd(cmd + "\n");
                saveUnitIndex(pos);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private char idxToCmd(int i) {
        switch (i) {
            case 0: return 'G';
            case 1: return 'O';
            case 2: return 'L';
            case 3: return 'K';
        }
        return 'G';
    }

    private void saveUnitIndex(int idx) {
        getSharedPreferences("scale_prefs", MODE_PRIVATE)
                .edit().putInt("unit_idx", idx).apply();
    }

    // ===== Bluetooth connection path =====

    private void connectBluetooth() {
        String mac = macInput.getText().toString().trim();

        // If user typed a MAC address, keep that path
        if (!mac.isEmpty()) {
            try {
                BluetoothDevice device = safeGetRemote(mac);
                connectToDevice(device);
            } catch (Exception e) {
                status("Error: " + e.getMessage());
            }
            return;
        }

        // No MAC entered: open paired-device picker
        showPairedDevicePicker();
    }

    private void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            status("No device selected");
            return;
        }

        status("Connecting...");
        final BluetoothDevice target = device;

        exec.execute(() -> {
            closeSocket();
            try {
                BluetoothSocket s =
                        target.createRfcommSocketToServiceRecord(SPP_UUID);
                btAdapter.cancelDiscovery();
                s.connect();
                socket = s;
                out = socket.getOutputStream();
                in = socket.getInputStream();
                status("Connected");
                runOnUiThread(() -> {
                    connectBtn.setText("Connected");
                    connectBtn.setEnabled(false);
                });
                startReader();
            } catch (SecurityException se) {
                status("Permission denied");
            } catch (IOException e) {
                status("Failed");
            }
        });
    }

    private void showPairedDevicePicker() {
        try {
            Set<BluetoothDevice> bonded = safeGetBonded();

            if (bonded == null || bonded.isEmpty()) {
                status("No paired devices found. Please pair your ESP32 once in Bluetooth settings.");
                // Open system Bluetooth settings so user can pair easily
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
                return;
            }

            List<BluetoothDevice> devices = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (BluetoothDevice d : bonded) {
                String name = d.getName();
                if (name == null || name.isEmpty()) {
                    name = "Unknown device";
                }

                // If you want to filter to ESP devices only, you could do:
                // if (name == null || !name.contains("ESP")) continue;

                devices.add(d);
                labels.add(name + " (" + d.getAddress() + ")");
            }

            if (devices.isEmpty()) {
                status("No suitable Bluetooth devices found.");
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select Bluetooth device")
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                        BluetoothDevice chosen = devices.get(which);
                        connectToDevice(chosen);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        } catch (SecurityException se) {
            status("Permission denied");
        } catch (Exception e) {
            status("Error: " + e.getMessage());
        }
    }

    private void startReader() {
        if (reading) return;
        reading = true;

        exec.execute(() -> {
            try {
                BufferedReader br =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

                String line;
                while (reading && (line = br.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException e) {
                status("Disconnected");
            } finally {
                reading = false;
                closeSocket();
                // Re-enable the connect button when connection is lost
                runOnUiThread(() -> {
                    connectBtn.setText("Connect Bluetooth");
                    connectBtn.setEnabled(true);
                });
            }
        });
    }

    private void handleLine(String line) {
        try {
            JSONObject obj = new JSONObject(line);

            double w = obj.optDouble("weight", Double.NaN);
            String u = obj.optString("unit", "");

            if (!Double.isNaN(w) && !u.isEmpty()) {
                runOnUiThread(() ->
                        weightText.setText(String.format("%.2f %s", w, u))
                );
                return;
            }

        } catch (Exception ignore) { }
    }

    private void sendCmd(String cmd) {
        if (socket == null || out == null) {
            toast("Not connected");
            return;
        }
        exec.execute(() -> {
            try {
                out.write(cmd.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                status("Send failed");
            }
        });
    }

    // ===== Permission helpers =====

    private boolean isSPlus() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private boolean hasBtPerms() {
        if (!isSPlus()) return true;

        boolean connectOk = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED;

        boolean scanOk = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED;

        return connectOk && scanOk;
    }

    private void requestBtPerms() {
        if (!isSPlus()) return;

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                },
                REQ_BT_PERMS
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BT_PERMS) {
            boolean allGranted = true;
            if (grantResults.length == 0) {
                allGranted = false;
            } else {
                for (int res : grantResults) {
                    if (res != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            }

            if (allGranted) {
                connectBluetooth();
            } else {
                toast("Bluetooth permission denied");
            }
        }
    }

    private BluetoothDevice safeGetRemote(String mac) throws SecurityException {
        if (isSPlus() && !hasBtPerms()) throw new SecurityException("Missing Bluetooth permissions");
        return btAdapter.getRemoteDevice(mac);
    }

    private Set<BluetoothDevice> safeGetBonded() throws SecurityException {
        if (isSPlus() && !hasBtPerms()) throw new SecurityException("Missing Bluetooth permissions");
        return btAdapter.getBondedDevices();
    }

    private void status(String s) {
        runOnUiThread(() -> statusText.setText(s));
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reading = false;
        closeSocket();
        exec.shutdownNow();
    }

    private void closeSocket() {
        try { if (in != null)  in.close(); } catch (Exception ignore) {}
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
        in = null; out = null; socket = null;
    }
}
