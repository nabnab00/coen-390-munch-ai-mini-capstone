package com.example.munchai.frontend;

import android.Manifest;
import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothGattDescriptor;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.munchai.R;
import com.example.munchai.backend.GeminiRequest;
import com.example.munchai.model.NutritionFacts;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission") // Permissions are checked before use
public class WeightScaleActivity extends AppCompatActivity {

    private static final String TAG = "WeightScaleActivity";
    // Standard Bluetooth Service and Characteristic UUIDs for Weight Scale
    private static final UUID WEIGHT_SERVICE_UUID = UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb");
    private static final UUID WEIGHT_MEASUREMENT_UUID = UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private TextView weightText;
    private Button saveBtn;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private Uri photoUri;

    private boolean isScanning = false;

    // Launcher for Bluetooth permissions
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                if (permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false) &&
                        permissions.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false)) {
                    startScan();
                } else {
                    Toast.makeText(this, "Bluetooth permissions are required to use the scale.", Toast.LENGTH_LONG).show();
                    // Fallback to manual entry if permissions are denied
                }
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.weightpage);

        weightText = findViewById(R.id.weightText);
        saveBtn = findViewById(R.id.weightsave);

        photoUri = getIntent().getData();
        if (photoUri == null) {
            Toast.makeText(this, "No photo URI provided. Cannot proceed.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeBluetooth();

        saveBtn.setOnClickListener(v -> {
            String weight = weightText.getText().toString();
            if (weight.isEmpty() || weight.equals("---")) {
                Toast.makeText(this, "Weight not captured. Please place food on the scale or enter manually.", Toast.LENGTH_SHORT).show();
                return;
            }
            // The rest of the logic is the same
            processAndReturnData(weight);
        });
    }

    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth.", Toast.LENGTH_LONG).show();
            // Consider launching settings: startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            checkPermissionsAndScan();
        }
    }

    private void checkPermissionsAndScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION // Required for scanning on older APIs
            });
        } else {
            startScan();
        }
    }


    private void startScan() {
        if (isScanning || scanner != null) return; // Already scanning
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Toast.makeText(this, "Could not get Bluetooth scanner.", Toast.LENGTH_SHORT).show();
            return;
        }

        isScanning = true;
        Toast.makeText(this, "Scanning for scale...", Toast.LENGTH_SHORT).show();
        scanner.startScan(scanCallback);

        // Stop scanning after 15 seconds if no device is found
        new android.os.Handler().postDelayed(() -> {
            if (isScanning) {
                stopScan();
                Toast.makeText(this, "Scale not found. You can enter weight manually.", Toast.LENGTH_LONG).show();
            }
        }, 15000);
    }

    private void stopScan() {
        if (scanner != null && isScanning) {
            scanner.stopScan(scanCallback);
            isScanning = false;
            scanner = null;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // Check if the discovered device has the Weight Scale Service UUID
            if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                for (android.os.ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
                    if (WEIGHT_SERVICE_UUID.equals(uuid.getUuid())) {
                        Log.d(TAG, "Weight scale found: " + result.getDevice().getAddress());
                        stopScan(); // Stop scanning once we found our device
                        gatt = result.getDevice().connectGatt(WeightScaleActivity.this, false, gattCallback);
                        break;
                    }
                }
            }
        }
    };


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                runOnUiThread(() -> Toast.makeText(WeightScaleActivity.this, "Scale disconnected.", Toast.LENGTH_SHORT).show());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(WEIGHT_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(WEIGHT_MEASUREMENT_UUID);
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (WEIGHT_MEASUREMENT_UUID.equals(characteristic.getUuid())) {
                // Parse the weight data
                byte[] data = characteristic.getValue();
                // The first byte contains flags.
                int flags = data[0];
                // Check if weight is in kg (bit 0 = 0) or lbs (bit 0 = 1)
                boolean isImperial = (flags & 0x01) != 0;

                // Weight is a 16-bit unsigned integer (2 bytes) starting from the second byte
                int weightRaw = ByteBuffer.wrap(data, 1, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

                // The standard resolution is 0.005 kg. We need grams.
                double weightInGrams = weightRaw * 5;

                final String finalWeight = String.valueOf(Math.round(weightInGrams));
                Log.d(TAG, "Weight received: " + finalWeight + "g");

                runOnUiThread(() -> {
                    weightText.setText(finalWeight);
                    Toast.makeText(WeightScaleActivity.this, "Weight Captured!", Toast.LENGTH_SHORT).show();
                });
            }
        }
    };

    private void processAndReturnData(String weight) {
        // Disable button to prevent multiple clicks
        saveBtn.setEnabled(false);
        Toast.makeText(this, "Analyzing nutrition...", Toast.LENGTH_LONG).show();

        // Run Gemini request in the background
        exec.execute(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoUri);
                // We pass the weight to Gemini for more accurate results
                NutritionFacts facts = GeminiRequest.fetchNutritionFacts(bitmap, weight);

                ObjectMapper mapper = new ObjectMapper();
                String nutritionJson = mapper.writeValueAsString(facts);

                runOnUiThread(() -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("weight", weight);
                    resultIntent.putExtra("nutrition_facts", nutritionJson);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(WeightScaleActivity.this, "Failed to get nutrition data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveBtn.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
        stopScan();
        if (!exec.isShutdown()) {
            exec.shutdown();
        }
    }
}
