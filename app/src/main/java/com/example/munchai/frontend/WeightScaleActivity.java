package com.example.munchai.frontend;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.munchai.R;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Weight page activity:
 * - "Connect Bluetooth" button opens system Bluetooth settings.
 * - When an SPP connection to the ESP32 exists: button shows "Connected" and is disabled.
 * - Otherwise: button shows "Connect Bluetooth" and is enabled.
 *
 * Auto-connect behavior:
 * - On resume, if not connected, tries to connect to a bonded device whose name starts with "ESP32".
 *   (Change TARGET_DEVICE_NAME_PREFIX below if your device advertises a different name.)
 */
public class WeightScaleActivity extends AppCompatActivity {

    public static final String EXTRA_WEIGHT = "";
    public static final String EXTRA_UNIT = "";
    // ---- Customize this if needed ----
    private static final String TARGET_DEVICE_NAME_PREFIX = "ESP32"; // e.g., "ESP32", "HC-05", etc.
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Classic SPP

    private static final String TAG = "WeightScale";

    private Button connectBtn;

    private BluetoothAdapter btAdapter;
    @Nullable private BluetoothSocket socket;
    @Nullable private BluetoothDevice connectedDevice;

    // Runtime permission requesters (Android 12+)
    private final ActivityResultLauncher<String> permBtConnect =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // We only request if we need it; UI will update afterward
                updateConnectButton();
            });

    private final ActivityResultLauncher<String> permBtScan =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Not strictly used here since we connect via bonded devices; still nice to have
                updateConnectButton();
            });

    // Listen for adapter state + ACL connects/disconnects to refresh button state
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                // A device connected — we might or might not be the one. Refresh UI.
                updateConnectButton();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                // A device disconnected — if it's ours, clean up socket.
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null && connectedDevice != null && d.getAddress().equals(connectedDevice.getAddress())) {
                    closeSocketQuietly();
                }
                updateConnectButton();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                updateConnectButton();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weightpage);

        connectBtn = findViewById(R.id.connectBtn);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            toast("Bluetooth not supported on this device.");
        }

        connectBtn.setOnClickListener(v -> {
            // Open system Bluetooth settings so user pairs/connects to ESP32 there
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(i);
        });

        updateConnectButton();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btReceiver, f);
        updateConnectButton();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(btReceiver); } catch (Exception ignore) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        // After user returns from Settings, attempt SPP connect if possible and not already connected
        if (!isConnected()) {
            tryAutoConnectToEsp32();
        }
        updateConnectButton();
    }

    // ---- Connection helpers ----

    private boolean isConnected() {
        try {
            return socket != null && socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasBtConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBtScanPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMissingBtPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBtConnectPermission()) {
                permBtConnect.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (!hasBtScanPermission()) {
                permBtScan.launch(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
    }

    private void tryAutoConnectToEsp32() {
        if (btAdapter == null) return;
        if (!btAdapter.isEnabled()) return;

        requestMissingBtPermissionsIfNeeded();

        // We only look at bonded devices — user pairs in Settings first
        Set<BluetoothDevice> bonded;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtConnectPermission()) {
            // Can't read bonded devices without permission on 12+
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission; cannot auto-connect.");
            return;
        }

        bonded = btAdapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            Log.i(TAG, "No bonded devices; user must pair in Settings.");
            return;
        }

        // Pick the first bonded device whose name starts with the prefix
        BluetoothDevice target = null;
        for (BluetoothDevice d : bonded) {
            String name = safeName(d);
            if (name != null && name.startsWith(TARGET_DEVICE_NAME_PREFIX)) {
                target = d;
                break;
            }
        }
        if (target == null) {
            Log.i(TAG, "No bonded device matches prefix: " + TARGET_DEVICE_NAME_PREFIX);
            return;
        }

        connectOnBackground(target);
    }

    @SuppressLint("MissingPermission")
    private void connectOnBackground(BluetoothDevice device) {
        if (isConnected()) return;

        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtConnectPermission()) {
                    Log.w(TAG, "Cannot connect: missing BLUETOOTH_CONNECT permission.");
                    runOnUiThread(() -> updateConnectButton());
                    return;
                }
                BluetoothSocket tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);

                // Cancel discovery (not scanning here, but just to be safe)
                if (btAdapter != null && btAdapter.isDiscovering()) {
                    btAdapter.cancelDiscovery();
                }

                tmp.connect(); // blocks
                socket = tmp;
                connectedDevice = device;
                Log.i(TAG, "SPP connected to " + safeName(device) + " (" + device.getAddress() + ")");
                runOnUiThread(() -> {
                    toast("Connected to " + safeName(device));
                    updateConnectButton();
                });
            } catch (IOException e) {
                Log.e(TAG, "SPP connect failed: " + e.getMessage(), e);
                closeSocketQuietly();
                runOnUiThread(() -> {
                    toast("Bluetooth connect failed. Pair in Settings then return.");
                    updateConnectButton();
                });
            }
        }, "bt-connect").start();
    }

    private void closeSocketQuietly() {
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
        socket = null;
        connectedDevice = null;
    }

    private String safeName(BluetoothDevice d) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtConnectPermission()) return "(unknown)";
            return d.getName();
        } catch (SecurityException se) {
            return "(unknown)";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---- UI state ----
    private void updateConnectButton() {
        boolean connected = isConnected();
        if (connectBtn != null) {
            connectBtn.setText(connected ? "Connected" : "Connect Bluetooth");
            connectBtn.setEnabled(!connected);
        }
    }

    // Optional: call this to explicitly disconnect from menu or similar
    public void disconnectIfNeeded(View v) {
        if (isConnected()) {
            closeSocketQuietly();
            updateConnectButton();
            toast("Disconnected.");
        }
    }
}
