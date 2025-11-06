package com.example.munchai.frontend;

import com.example.munchai.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightScaleActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMS = 2001;
    private static final String TARGET_NAME = "ESP32-Scale";
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TextView statusText, weightText;
    private EditText macInput;
    private Button connectBtn, streamBtn, tareBtn;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket socket;
    private OutputStream out;
    private InputStream in;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean reading = false;
    private volatile boolean streaming = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weightpage);

        statusText = findViewById(R.id.statusText);
        weightText = findViewById(R.id.weightText);
        macInput   = findViewById(R.id.macInput);
        connectBtn = findViewById(R.id.connectBtn);
        streamBtn  = findViewById(R.id.streamBtn);
        tareBtn    = findViewById(R.id.tareBtn);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            toast("Bluetooth not supported");
            finish();
            return;
        }

        connectBtn.setOnClickListener(v -> ensurePermsThenConnect());
        streamBtn.setOnClickListener(v -> toggleStream());
        tareBtn.setOnClickListener(v -> sendTare());
    }

    // ---- Permission helpers ----
    private boolean isSPlus() {
        return Build.VERSION.SDK_INT >= 31;
    }

    private boolean hasBtConnect() {
        if (!isSPlus()) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBtScan() {
        if (!isSPlus()) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtPerms() {
        if (!isSPlus()) return;
        ActivityCompat.requestPermissions(
                this,
                new String[]{ Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN },
                REQ_BT_PERMS
        );
    }

    private void ensurePermsThenConnect() {
        if (isSPlus() && (!hasBtConnect() || !hasBtScan())) {
            requestBtPerms();
            return;
        }
        connectBluetooth();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMS) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
            }
            if (!granted) {
                status("Bluetooth permission denied");
                return;
            }
            connectBluetooth();
        }
    }

    // ---- Connect / IO ----
    private void connectBluetooth() {
        // Guard every adapter call
        if (isSPlus() && !hasBtConnect()) {
            status("Grant Bluetooth permission");
            requestBtPerms();
            return;
        }

        String mac = macInput.getText().toString().trim();
        BluetoothDevice device = null;

        try {
            if (!mac.isEmpty()) {
                // getRemoteDevice also requires BLUETOOTH_CONNECT
                if (!hasBtConnect()) {
                    status("Grant Bluetooth permission");
                    requestBtPerms();
                    return;
                }
                try {
                    device = btAdapter.getRemoteDevice(mac);
                } catch (SecurityException se) {
                    status("Permission error (CONNECT). Grant permissions.");
                    requestBtPerms();
                    return;
                }
            } else {
                Set<BluetoothDevice> bonded;
                try {
                    bonded = hasBtConnect() ? btAdapter.getBondedDevices() : Collections.emptySet();
                } catch (SecurityException se) {
                    status("Permission error (CONNECT). Grant permissions.");
                    requestBtPerms();
                    return;
                }

                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        String name;
                        try {
                            name = d.getName();  // requires CONNECT on S+
                        } catch (SecurityException se) {
                            status("Permission error (CONNECT). Grant permissions.");
                            requestBtPerms();
                            return;
                        }
                        if (name != null && name.equals(TARGET_NAME)) {
                            device = d;
                            break;
                        }
                    }
                }
            }

            if (device == null) {
                status("Paired 'ESP32-Scale' not found. Enter MAC.");
                return;
            }

            status("Connecting...");
            final BluetoothDevice target = device;

            exec.execute(() -> {
                closeSocketQuiet();
                try {
                    BluetoothSocket s = target.createRfcommSocketToServiceRecord(SPP_UUID);
                    s.connect(); // may throw
                    socket = s;
                    out = socket.getOutputStream();
                    in  = socket.getInputStream();
                    statusOnUi("Connected");
                    startReader();
                } catch (SecurityException se) {
                    statusOnUi("Permission error during connect");
                } catch (IOException e) {
                    statusOnUi("Connection failed");
                    closeSocketQuiet();
                }
            });

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
                statusOnUi("Disconnected");
            } finally {
                reading = false;
                closeSocketQuiet();
            }
        });
    }

    private void handleLine(String line) {
        try {
            JSONObject obj = new JSONObject(line);
            double g = obj.optDouble("weight_g", Double.NaN);
            if (!Double.isNaN(g)) {
                runOnUiThread(() -> weightText.setText(String.format("%.2f g", g)));
            }
        } catch (Exception ignore) {}
    }

    private void toggleStream() {
        if (socket == null || out == null) {
            toast("Not connected");
            return;
        }
        streaming = !streaming;
        sendCommand("s"); // firmware toggles on 's'
        streamBtn.setText(streaming ? "Stop Stream" : "Start Stream");
        status(streaming ? "Streaming..." : "Stream stopped");
    }

    private void sendTare() {
        if (socket == null || out == null) {
            toast("Not connected");
            return;
        }
        sendCommand("t");
        toast("Tare sent");
    }

    private void sendCommand(String cmd) {
        exec.execute(() -> {
            try {
                out.write(cmd.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                statusOnUi("Send failed");
            }
        });
    }

    private void status(String s) {
        runOnUiThread(() -> statusText.setText(s));
    }
    private void statusOnUi(String s) { status(s); }
    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reading = false;
        streaming = false;
        closeSocketQuiet();
        exec.shutdownNow();
    }

    private void closeSocketQuiet() {
        try { if (in != null) in.close(); } catch (Exception ignore) {}
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
        in = null; out = null; socket = null;
    }
}