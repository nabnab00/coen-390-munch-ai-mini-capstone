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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    private Button connectBtn, tareBtn, resetBtn;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket socket;
    private OutputStream out;
    private InputStream in;

    private final ExecutorService exec = Executors.newCachedThreadPool();
    private volatile boolean reading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weightpage);

        statusText = findViewById(R.id.statusText);
        weightText = findViewById(R.id.weightText);
        macInput   = findViewById(R.id.macInput);
        connectBtn = findViewById(R.id.connectBtn);
        tareBtn    = findViewById(R.id.tareBtn);
        resetBtn   = findViewById(R.id.resetBtn); // <— new button

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            toast("Bluetooth not supported");
            finish();
            return;
        }

        connectBtn.setOnClickListener(v -> {
            if (isSPlus() && !hasBtConnect()) {
                requestBtPerms();
                status("Grant Bluetooth permission");
            } else {
                connectBluetooth();
            }
        });

        tareBtn.setOnClickListener(v -> sendCommand("t"));
        resetBtn.setOnClickListener(v -> {
            sendCommand("r");     // tell firmware to soft-reset scale
            toast("Reset sent");
        });
    }

    private void connectBluetooth() {
        String mac = macInput.getText().toString().trim();
        BluetoothDevice device = null;

        try {
            if (!mac.isEmpty()) {
                try {
                    device = safeGetRemote(mac);
                } catch (SecurityException se) {
                    status("Permission error (CONNECT). Grant permissions.");
                    requestBtPerms();
                    return;
                } catch (IllegalArgumentException iae) {
                    status("Invalid MAC address");
                    return;
                }
            } else {
                try {
                    Set<BluetoothDevice> bonded = safeGetBonded();
                    for (BluetoothDevice d : bonded) {
                        if (TARGET_NAME.equals(d.getName())) {
                            device = d; break;
                        }
                    }
                } catch (SecurityException se) {
                    status("Permission error (CONNECT). Grant permissions.");
                    requestBtPerms();
                    return;
                }
                if (device == null) {
                    status("Enter MAC or pair " + TARGET_NAME + " first");
                    return;
                }
            }

            final BluetoothDevice target = device;
            status("Connecting...");
            exec.execute(() -> {
                closeSocketQuiet();
                try {
                    BluetoothSocket s = target.createRfcommSocketToServiceRecord(SPP_UUID);
                    s.connect();
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
        } catch (Exception ignore) {
            // ignore non-JSON lines
        }
    }

    private void sendCommand(String cmd) {
        if (socket == null || out == null) {
            toast("Not connected");
            return;
        }
        exec.execute(() -> {
            try {
                out.write(cmd.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                statusOnUi("Send failed");
            }
        });
    }

    private boolean isSPlus() { return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S; }
    private boolean hasBtConnect() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void requestBtPerms() {
        if (isSPlus()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.BLUETOOTH_CONNECT }, REQ_BT_PERMS);
        }
    }
    private Set<BluetoothDevice> safeGetBonded() throws SecurityException {
        if (isSPlus() && !hasBtConnect()) throw new SecurityException("no CONNECT");
        return btAdapter.getBondedDevices();
    }
    private BluetoothDevice safeGetRemote(String mac)
            throws SecurityException, IllegalArgumentException {
        if (isSPlus() && !hasBtConnect()) throw new SecurityException("no CONNECT");
        return btAdapter.getRemoteDevice(mac);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_BT_PERMS && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            connectBluetooth();
        }
    }

    private void status(String s) { runOnUiThread(() -> statusText.setText(s)); }
    private void statusOnUi(String s) { runOnUiThread(() -> statusText.setText(s)); }
    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reading = false;
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
