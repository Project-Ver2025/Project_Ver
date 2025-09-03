package com.example.esp32;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements BluetoothTTSService.ServiceCallback {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private BluetoothTTSService bluetoothTTSService;
    private boolean serviceBound = false;
    private String lastStatusText = "Disconnected";
    private int lastStatusColor = Color.RED;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothTTSService.LocalBinder binder = (BluetoothTTSService.LocalBinder) service;
            bluetoothTTSService = binder.getService();
            bluetoothTTSService.setServiceCallback(MainActivity.this);
            serviceBound = true;
            Log.d("MainActivity", "Service connected");

            // Update UI with current service state
            updateConnectionStatusUI(
                    bluetoothTTSService.getConnectionStatusText(),
                    bluetoothTTSService.getConnectionStatusColor()
            );
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothTTSService = null;
            serviceBound = false;
            Log.d("MainActivity", "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check permissions first
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startAndBindService();
        }

        setupBottomNavigation();
    }

    public boolean isServiceBound() {
        return serviceBound && bluetoothTTSService != null;
    }

    public BluetoothTTSService getBluetoothService() {
        return bluetoothTTSService;
    }


    private void setupBottomNavigation() {
        // Bottom navigation setup
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment selected;

            if (itemId == R.id.nav_settings) {
                selected = new SettingsFragment(
                        serviceBound && bluetoothTTSService != null ?
                                bluetoothTTSService.textToSpeech : null,
                        getSharedPreferences("app_prefs", MODE_PRIVATE).getFloat("speech_rate", 1.0f),
                        serviceBound && bluetoothTTSService != null ?
                                bluetoothTTSService::reconnectGatt : null
                );
            } else if (itemId == R.id.nav_presets) {
                selected = new PresetsFragment(this::sendBLECommand);
            } else if (itemId == R.id.nav_send) {
                selected = new SendFragment(this::sendBLECommand);
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

    private void startAndBindService() {
        // Start the foreground service
        Intent serviceIntent = new Intent(this, BluetoothTTSService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Bind to the service to communicate with it
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("BLE", "Permissions granted!");
                startAndBindService();
            } else {
                Log.e("BLE", "Permissions denied!");
                Toast.makeText(this, "Bluetooth permissions are required for the app to function.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void sendBLECommand(String command) {
        if (serviceBound && bluetoothTTSService != null) {
            bluetoothTTSService.sendBLECommand(command);

            // Handle UI updates for cancel command
            if ("cancel".equals(command)) {
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (fragment instanceof ControlFragment) {
                    ControlFragment controlFragment = (ControlFragment) fragment;
                    controlFragment.startStopButton.setText("Start");
                    controlFragment.isRecording = false;
                }
            }

            if ("stop".equals(command)) {
                // Add a small delay to let any completion sound play first
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    if (serviceBound && bluetoothTTSService != null) {
                        bluetoothTTSService.startLoadingSoundFromCommand();
                    }
                }, 500);
            }

            if ("start".equals(command) || "cancel".equals(command)) {
                if (serviceBound && bluetoothTTSService != null) {
                    bluetoothTTSService.stopLoadingSoundFromCommand();
                }
            }

        } else {
            Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
        }
    }

    // Service callback implementation
    @Override
    public void onConnectionStateChanged(String status, int color) {
        runOnUiThread(() -> updateConnectionStatusUI(status, color));
    }

    private void updateConnectionStatusUI(String status, int color) {
        lastStatusText = status;
        lastStatusColor = color;

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof SettingsFragment) {
            ((SettingsFragment) fragment).setConnectionStatus(status, color);
        }
    }

    public String getLastStatusText() {
        return lastStatusText;
    }

    public int getLastStatusColor() {
        return lastStatusColor;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If we have permissions but service isn't bound, bind it
        if (hasPermissions() && !serviceBound) {
            Intent intent = new Intent(this, BluetoothTTSService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Don't unbind the service when activity stops - let it run in background
        // The service continues running as a foreground service
    }

    @Override
    protected void onDestroy() {
        // Only unbind when activity is truly destroyed
        if (serviceBound) {
            if (bluetoothTTSService != null) {
                bluetoothTTSService.setServiceCallback(null);
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }

        // Note: We don't stop the service here as it should continue running
        // The service will be stopped when the user explicitly wants to disconnect
        // or when the system needs to reclaim resources

        super.onDestroy();
    }

    // Method to stop the service (you can call this from a menu or button if needed)
    public void stopBluetoothTTSService() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        stopService(new Intent(this, BluetoothTTSService.class));
    }
}