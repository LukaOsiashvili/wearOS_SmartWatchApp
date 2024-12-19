package com.example.wearosapp;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String TAG = "WearOSSensorApp";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PERMISSION_REQUEST_CODE = 123;

    private TextView statusText;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor accelerometerSensor;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private Handler mainHandler;

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        statusText = findViewById(R.id.statusText);
        showStatus("Starting application...");

        if (checkAndRequestPermissions()) {
            showStatus("Permissions granted, initializing...");
            initializeApp();
        } else {
            showStatus("Requesting permissions...");
        }
    }

    private boolean checkAndRequestPermissions() {
        boolean allPermissionsGranted = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                Log.d(TAG, "Permission not granted: " + permission);
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d(TAG, "Permission denied: " + permissions[i]);
                    break;
                }
            }

            if (allGranted) {
                showStatus("All permissions granted, initializing...");
                initializeApp();
            } else {
                showStatus("Required permissions not granted!");
                finish();
            }
        }
    }

    private void initializeApp() {
        showStatus("Initializing sensors...");
        initializeSensors();

        showStatus("Initializing Bluetooth...");
        initializeBluetooth();
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) {
            showStatus("Heart rate sensor not available!");
            return;
        }

        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometerSensor == null) {
            showStatus("Accelerometer not available!");
            return;
        }

        sensorManager.registerListener(this, heartRateSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, accelerometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL);

        showStatus("Sensors initialized successfully");
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showStatus("Bluetooth not supported on this device!");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            showStatus("Please enable Bluetooth!");
            return;
        }

        showStatus("Starting Bluetooth connection...");
        new Thread(this::connectToDevice).start();
    }

    private void connectToDevice() {
        try {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) !=
                    PackageManager.PERMISSION_GRANTED) {
                showStatus("Bluetooth connect permission not granted!");
                return;
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() == 0) {
                showStatus("No paired devices found!");
                return;
            }

            showStatus("Found " + pairedDevices.size() + " paired devices");

            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, "Paired device: " + device.getName() + " - " + device.getAddress());
            }

            BluetoothDevice device = pairedDevices.iterator().next();
            showStatus("Connecting to: " + device.getName());

            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();

            showStatus("Connected to: " + device.getName());

        } catch (IOException e) {
            String errorMsg = "Connection failed: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            showStatus(errorMsg);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (outputStream == null) return;

        try {
            String data;
            if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
                data = String.format("HR:%.1f", event.values[0]);
                Log.d(TAG, "Heart Rate: " + event.values[0]);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                data = String.format("ACC:%.2f,%.2f,%.2f",
                        event.values[0], event.values[1], event.values[2]);
                Log.d(TAG, "Accelerometer: x=" + event.values[0] +
                        " y=" + event.values[1] + " z=" + event.values[2]);
            } else {
                return;
            }

            data += ";" + System.currentTimeMillis() + "\n";
            outputStream.write(data.getBytes());
            outputStream.flush();

        } catch (IOException e) {
            Log.e(TAG, "Error sending data", e);
            showStatus("Error sending data: " + e.getMessage());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + sensor.getName() + " - " + accuracy);
    }

    private void showStatus(final String message) {
        Log.d(TAG, message);
        mainHandler.post(() -> {
            statusText.setText(message);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        showStatus("Cleaning up...");

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
    }
}