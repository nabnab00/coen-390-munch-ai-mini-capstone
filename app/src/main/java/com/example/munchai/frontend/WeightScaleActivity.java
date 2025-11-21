package com.example.munchai.frontend;

import com.example.munchai.R;

import android.content.Intent;
import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

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

    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean reading = false;

    private double currentWeight = 0.0;
    private double tareOffset = 0.0;

    private String selectedUnit = "g";

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
            if (isSPlus() && !hasBtConnect()) {
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

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_WEIGHT, numericWeight);
            resultIntent.putExtra(EXTRA_UNIT, selectedUnit);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });

        tareBtn.setOnClickListener(v -> {
            tareOffset = currentWeight;
            updateWeightDisplay();
        });

        resetBtn.setOnClickListener(v -> {
            tareOffset = 0.0;
            currentWeight = 0.0;
            updateWeightDisplay();
        });
    }

    private void setupSpinner() {
        String[] units = {"g", "oz", "lb"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, units);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(adapter);

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                selectedUnit = units[position];
                updateWeightDisplay();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private boolean isSPlus() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private boolean hasBtConnect() {
        if (!isSPlus()) return true;
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtPerms() {
        if (!isSPlus()) return;
        ActivityCompat.requestPermissions(
                this,
                new String[]{ Manifest.permission.BLUETOOTH_CONNECT },
                REQ_BT_PERMS
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BT_PERMS) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectBluetooth();
            } else {
                toast("Bluetooth permission denied");
            }
        }
    }

    private void connectBluetooth() {
        String mac = macInput.getText().toString().trim();

        // If user typed a MAC address, keep supporting that path
        if (!mac.isEmpty()) {
            try {
                BluetoothDevice device = safeGetRemote(mac);
                connectToDevice(device);
            } catch (Exception e) {
                status("Error: " + e.getMessage());
            }
            return;
        }

        // No MAC entered: show a picker with paired devices instead of forcing manual pairing
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
                // Open system Bluetooth settings so the user can pair easily
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

                // If you want to show only ESP32 devices, you can filter here, e.g.:
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

    private BluetoothDevice safeGetRemote(String mac) {
        try {
            if (isSPlus() && !hasBtConnect()) {
                throw new SecurityException("No BLUETOOTH_CONNECT permission");
            }
            return btAdapter.getRemoteDevice(mac);
        } catch (IllegalArgumentException e) {
            toast("Invalid MAC");
            return null;
        }
    }

    private Set<BluetoothDevice> safeGetBonded() {
        if (isSPlus() && !hasBtConnect()) {
            throw new SecurityException("No BLUETOOTH_CONNECT permission");
        }
        return btAdapter.getBondedDevices();
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
            String msg = obj.optString("msg", "");

            if (!Double.isNaN(w)) {
                currentWeight = w;
                updateWeightDisplay();
            }

            if (!msg.isEmpty()) {
                status(msg);
            }

        } catch (Exception ignore) {
        }
    }

    private void updateWeightDisplay() {
        double netWeight = currentWeight - tareOffset;
        if (netWeight < 0) netWeight = 0;

        String displayText;
        switch (selectedUnit) {
            case "oz":
                double oz = netWeight * 0.035274;
                displayText = String.format("%.2f oz", oz);
                break;
            case "lb":
                double lb = netWeight * 0.00220462;
                displayText = String.format("%.3f lb", lb);
                break;
            case "g":
            default:
                displayText = String.format("%.1f g", netWeight);
                break;
        }

        String finalDisplayText = displayText;
        runOnUiThread(() -> weightText.setText(finalDisplayText));
    }

    private void status(String s) {
        runOnUiThread(() -> statusText.setText(s));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
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
