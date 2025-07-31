package com.example.esp32;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final StringBuilder messageBuffer = new StringBuilder();
    private final Queue<String> ttsQueue = new LinkedList<>();
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writableCharacteristic;
    private BluetoothDevice targetDevice;
    private TextToSpeech textToSpeech;
    private boolean isSpeaking = false;
    private boolean cancelled = false;
    private float currentSpeechRate = 1.0f;

    private final String SERVICE_UUID = "12345678-1234-1234-1234-1234567890ab";
    private final String CHARACTERISTIC_UUID = "abcd1234-abcd-1234-abcd-1234567890ab";
    private static final int PERMISSION_REQUEST_CODE = 1001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Bluetooth FIRST
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = manager.getAdapter();

        // Add null checks for safety
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
            // Optionally request to enable Bluetooth
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Now check permissions and start scan
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startScan();
        }

        // Remove the duplicate permission check code that was here before

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(currentSpeechRate);
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) { }

                    @Override
                    public void onDone(String utteranceId) {
                        isSpeaking = false;
                        if (!cancelled) speakNextFromQueue();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        isSpeaking = false;
                        if (!cancelled) speakNextFromQueue();
                    }
                });
            }
        });

        // Bottom navigation setup
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment selected;

            if (itemId == R.id.nav_settings) {
                selected = new SettingsFragment(textToSpeech, currentSpeechRate, this::reconnectGatt);
            } else if (itemId == R.id.nav_presets) {
                selected = new PresetsFragment(this::sendBLECommand);
            } else {
                selected = new ControlFragment(this::sendBLECommand);
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selected)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
            return true;
        });

        // Load default fragment
        navView.setSelectedItemId(R.id.nav_control);
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("BLE", "Permissions granted!");
                startScan();
            } else {
                Log.e("BLE", "Permissions denied!");
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void sendBLECommand(String command) {
        if (bluetoothGatt != null && writableCharacteristic != null) {
            writableCharacteristic.setValue(command);
            bluetoothGatt.writeCharacteristic(writableCharacteristic);
        }

        if (Objects.equals(command, "cancel")) {

            // Immediately stop any ongoing speech
            if (textToSpeech != null && textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }

            // Clear remaining messages
            ttsQueue.clear();

            isSpeaking = false; // Reset speaking flag to allow future TTS

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment instanceof ControlFragment) {
                ControlFragment controlFragment = (ControlFragment) fragment;
                controlFragment.startStopButton.setText("Start");
                controlFragment.isRecording = false;
            }
        }

    }

    private void speakNextFromQueue() {
//        if (cancelled) {
//            ttsQueue.clear();
//            isSpeaking = false;
//            cancelled = false;
//            return;
//        }

        if (!isSpeaking && !ttsQueue.isEmpty()) {
            String msg = ttsQueue.poll();
            isSpeaking = true;
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "TTS_ID");
            textToSpeech.speak(msg, TextToSpeech.QUEUE_FLUSH, params, "TTS_ID");
        }
    }

    private void startScan() {
        // Create the callback as a class variable so we can stop it later
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device != null && result.getScanRecord() != null &&
                        result.getScanRecord().getServiceUuids() != null &&
                        result.getScanRecord().getServiceUuids().contains(ParcelUuid.fromString(SERVICE_UUID))) {

                    bluetoothLeScanner.stopScan(this);
                    bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
                    targetDevice = device;
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e("BLE", "Scan failed with error code: " + errorCode);
            }
        };

        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.startScan(scanCallback);
        }
    }

    private void reconnectGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
        if (targetDevice != null) {
            bluetoothGatt = targetDevice.connectGatt(this, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            runOnUiThread(() -> {
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (fragment instanceof SettingsFragment) {
                    SettingsFragment settingsFragment = (SettingsFragment) fragment;

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        settingsFragment.setConnectionStatus("Connected", Color.GREEN);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        settingsFragment.setConnectionStatus("Disconnected", Color.RED);
                    }
                }
            });


            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
            if (service != null) {
                writableCharacteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));

                gatt.setCharacteristicNotification(writableCharacteristic, true);
                BluetoothGattDescriptor descriptor = writableCharacteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String chunk = characteristic.getStringValue(0);
            runOnUiThread(() -> {
                messageBuffer.append(chunk);
                String bufferStr = messageBuffer.toString();
                int endIndex = bufferStr.indexOf("<EOM>");
                if (endIndex != -1) {
                    String fullMessage = bufferStr.substring(0, endIndex).trim();
                    messageBuffer.delete(0, endIndex + 5);
                    ttsQueue.offer(fullMessage);
                    speakNextFromQueue();
                }
            });
        }
    };


    @Override
    protected void onDestroy() {
        // Stop any ongoing scans
        if (bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback); // You'll need to make scanCallback a class variable
            } catch (Exception e) {
                Log.e("BLE", "Error stopping scan: " + e.getMessage());
            }
        }

        // Properly disconnect and clean up GATT
        if (bluetoothGatt != null) {
            bluetoothGatt.setCharacteristicNotification(writableCharacteristic, false);
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        // Shutdown TTS
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}
