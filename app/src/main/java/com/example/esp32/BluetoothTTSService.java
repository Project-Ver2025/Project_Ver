package com.example.esp32;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.media.MediaPlayer;
import android.media.AudioManager;
import java.io.IOException;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;

public class BluetoothTTSService extends Service {
    private static final String TAG = "BluetoothTTSService";
    private static final String CHANNEL_ID = "BluetoothTTSChannel";
    private static final int NOTIFICATION_ID = 1;

    private final StringBuilder messageBuffer = new StringBuilder();
    private final Queue<String> ttsQueue = new LinkedList<>();
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writableCharacteristic;
    private BluetoothDevice targetDevice;
    public TextToSpeech textToSpeech;
    private boolean isSpeaking = false;
    private float currentSpeechRate = 1.0f;

    private final String SERVICE_UUID = "12345678-1234-1234-1234-1234567890ab";
    private final String CHARACTERISTIC_UUID = "abcd1234-abcd-1234-abcd-1234567890ab";

    private MediaPlayer mediaPlayer;
    private MediaPlayer loadingPlayer; // Separate player for loading sound
    private boolean isLoading = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY_MS = 3000;

    public enum ConnectionState {
        SCANNING, CONNECTING, CONNECTED, DISCONNECTED
    }
    private Handler loadingHandler = new Handler();

    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private ServiceCallback serviceCallback;

    // Binder for activity communication
    private final IBinder binder = new LocalBinder();

    public interface ServiceCallback {
        void onConnectionStateChanged(String status, int color);
        void onControlCommandReceived(String command);

    }

    public class LocalBinder extends Binder {
        BluetoothTTSService getService() {
            return BluetoothTTSService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initializeBluetooth();
        initializeTTS();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());

        if (!hasPermissions()) {
            Log.e(TAG, "Missing permissions for BLE operations");
            stopSelf();
            return START_NOT_STICKY;
        }

        startScan();
        return START_STICKY; // Service will be restarted if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth TTS Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps Bluetooth and TTS active in background");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void playSound(int soundResourceId) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = MediaPlayer.create(this, soundResourceId);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayer = null;
                });
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound: " + e.getMessage());
        }
    }


    private void startLoadingSound() {
        if (!isLoading) {
            isLoading = true;
        }
        if (loadingPlayer != null && loadingPlayer.isPlaying()) {
            return;
        }

        try {
            if (loadingPlayer != null) {
                loadingPlayer.release();
            }

            loadingPlayer = MediaPlayer.create(this, R.raw.processing);
            if (loadingPlayer != null) {
//                loadingPlayer.setLooping(true); // Loop the loading sound
                loadingPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    loadingPlayer = null;
                    // This shouldn't be called since we're looping, but just in case
                    if (isLoading) {
                        loadingHandler.postDelayed(this::startLoadingSound, 2000); // Restart if we're still in loading state
                    }
                });
                loadingPlayer.start();
                Log.d(TAG, "Loading sound started");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting loading sound: " + e.getMessage());
            isLoading = false;
        }
    }

    private void stopLoadingSound() {
        if (!isLoading) return; // Not currently loading

        isLoading = false;
        loadingHandler.removeCallbacksAndMessages(null);
        try {
            if (loadingPlayer != null) {
                loadingPlayer.stop();
                loadingPlayer.release();
                loadingPlayer = null;
                Log.d(TAG, "Loading sound stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping loading sound: " + e.getMessage());
        }
    }

    public void startLoadingSoundFromCommand() {
        startLoadingSound();
    }

    public void stopLoadingSoundFromCommand() {
        stopLoadingSound();
    }

    private void speakImmediate(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, "IMMEDIATE_" + System.currentTimeMillis());
        }
    }
    public void playBeep() {
        playSound(R.raw.beep); // Add beep.wav or beep.mp3 into res/raw/
    }


    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String statusText = getConnectionStatusText();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ESP32 Controller Active")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_bluetooth) // You'll need to add this icon
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    String getConnectionStatusText() {
        switch (connectionState) {
            case SCANNING: return "Scanning for device...";
            case CONNECTING: return "Connecting...";
            case CONNECTED: return "Connected to ESP32";
            case DISCONNECTED: return "Disconnected";
            default: return "Status unknown";
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    private void initializeBluetooth() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = manager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not available or not enabled");
            stopSelf();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BLE scanner not available");
            stopSelf();
        }
    }

    private void initializeTTS() {
        // Load saved speech rate
        currentSpeechRate = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getFloat("speech_rate", 1.0f);

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
                        speakNextFromQueue();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        isSpeaking = false;
                        speakNextFromQueue();
                    }
                });
            }
        });
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void startScan() {
        connectionState = ConnectionState.SCANNING;
        updateConnectionStatusUI("Scanning...", Color.YELLOW);
        updateNotification();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device != null && result.getScanRecord() != null &&
                        result.getScanRecord().getServiceUuids() != null &&
                        result.getScanRecord().getServiceUuids().contains(ParcelUuid.fromString(SERVICE_UUID))) {

                    bluetoothLeScanner.stopScan(this);
                    connectionState = ConnectionState.CONNECTING;
                    updateConnectionStatusUI("Connecting...", Color.CYAN);
                    updateNotification();

                    bluetoothGatt = device.connectGatt(BluetoothTTSService.this, false, gattCallback);
                    targetDevice = device;
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                stopLoadingSoundFromCommand();
                Log.e(TAG, "Scan failed with error code: " + errorCode);
                connectionState = ConnectionState.DISCONNECTED;
                updateConnectionStatusUI("Scan failed", Color.RED);
                updateNotification();
            }
        };

        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.startScan(scanCallback);
        }
    }

    public void sendBLECommand(String command) {
        if (bluetoothGatt != null && writableCharacteristic != null) {
            writableCharacteristic.setValue(command);
            bluetoothGatt.writeCharacteristic(writableCharacteristic);
        }

        if (Objects.equals(command, "cancel")) {
            if (textToSpeech != null && textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            ttsQueue.clear();
            isSpeaking = false;
        }
    }

    public void updateSpeechRate(float speechRate) {
        currentSpeechRate = speechRate;
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(speechRate);
        }
        // Save the speech rate
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putFloat("speech_rate", speechRate)
                .apply();
    }

    public void reconnectGatt() {
        // Properly clean up existing connection
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        // Reset connection state and characteristics
        connectionState = ConnectionState.DISCONNECTED;
        writableCharacteristic = null;
        reconnectAttempts = 0;

        updateConnectionStatusUI("Scanning...", Color.YELLOW);
        updateNotification();

//        if (targetDevice != null) {
//            connectionState = ConnectionState.CONNECTING;
//            bluetoothGatt = targetDevice.connectGatt(BluetoothTTSService.this, false, gattCallback);
//        } else {
//            connectionState = ConnectionState.SCANNING;
//            startScan();
//        }
        connectionState = ConnectionState.SCANNING;
        startScan();

//        connectionState = ConnectionState.SCANNING;
//        startScan();
    }

    private void updateConnectionStatusUI(String status, int color) {
        if (serviceCallback != null) {
            serviceCallback.onConnectionStateChanged(status, color);
        }
    }

    public void setServiceCallback(ServiceCallback callback) {
        this.serviceCallback = callback;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }
    public int getConnectionStatusColor() {
        switch (connectionState) {
            case SCANNING: return Color.YELLOW;
            case CONNECTING: return Color.CYAN;
            case CONNECTED: return Color.GREEN;
            case DISCONNECTED: return Color.RED;
            default: return Color.GRAY;
        }
    }

    private void speakNextFromQueue() {
        if (!isSpeaking && !ttsQueue.isEmpty()) {
            String msg = ttsQueue.poll();
            isSpeaking = true;
            if (textToSpeech != null) {
                textToSpeech.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");
            }
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED && connectionState != ConnectionState.CONNECTED) {
                connectionState = ConnectionState.CONNECTED;
                updateConnectionStatusUI("Connected", Color.GREEN);
                updateNotification();
                bluetoothGatt.discoverServices();
                reconnectAttempts = 0;
                speakImmediate("Connected");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && connectionState != ConnectionState.DISCONNECTED) {
                stopLoadingSoundFromCommand();
                connectionState = ConnectionState.DISCONNECTED;
                updateConnectionStatusUI("Disconnected", Color.RED);
                updateNotification();
                if (reconnectAttempts == 0) {
                    speakImmediate("Disconnected");
                }


                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    Log.d(TAG, "Reconnecting attempt " + reconnectAttempts);
                    new Handler(getMainLooper()).postDelayed(this::attemptReconnect, RECONNECT_DELAY_MS);
                } else {
                    Log.e(TAG, "Max reconnect attempts reached.");
                }
            }
        }

        private void attemptReconnect() {
            if (targetDevice != null) {
                bluetoothGatt = targetDevice.connectGatt(BluetoothTTSService.this, false, gattCallback);
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
            messageBuffer.append(chunk);
            String bufferStr = messageBuffer.toString();
            int endIndex = bufferStr.indexOf("<EOM>");
            if (endIndex != -1) {
                String fullMessage = bufferStr.substring(0, endIndex).trim();
                messageBuffer.delete(0, endIndex + 5);

                if (fullMessage.equals("XStart")) {
                    // Play sound instead of TTS
                    stopLoadingSound();
                    playSound(R.raw.loading); // You'll need to add a sound file to res/raw/
                    if (serviceCallback != null) {
                        serviceCallback.onControlCommandReceived("XStart");
                    }
                } else if (fullMessage.equals("XStop")) {
                    playSound(R.raw.complete);
                    new Handler(getMainLooper()).postDelayed(() -> startLoadingSound(), 500);
                    if (serviceCallback != null) {
                        serviceCallback.onControlCommandReceived("XStop");
                    }
                } else if (fullMessage.equals("XCancel")) {
                    playSound(R.raw.cancel);

                    stopLoadingSound();
                    if (textToSpeech != null && textToSpeech.isSpeaking()) {
                        textToSpeech.stop();
                    }
                    ttsQueue.clear();
                    isSpeaking = false;

                    if (serviceCallback != null) {
                        serviceCallback.onControlCommandReceived("XCancel");
                    }
                }
                else if (fullMessage.startsWith("TTS speed: ")) {
                    try {
                        String numberStr = fullMessage.substring("TTS speed: ".length()).trim();
                        float requestedRate = Float.parseFloat(numberStr);
                        stopLoadingSound();

                        // Clamp between 0.5x and 2.0x
                        float clampedRate = Math.max(0.5f, Math.min(2.0f, requestedRate));
                        updateSpeechRate(clampedRate);
                        speakImmediate(String.format("Speech rate set to %.1fx", clampedRate));
                        Log.d(TAG, "TTS speed updated to: " + clampedRate);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid TTS speed value: " + fullMessage, e);
                    }
                }
                else if (fullMessage.startsWith("I understood ")) {
                    stopLoadingSound();
                    ttsQueue.offer(fullMessage);
                    startLoadingSound();
                    speakNextFromQueue();

                }


                else {
                    // Normal TTS processing
                    stopLoadingSound();
                    ttsQueue.offer(fullMessage);
                    speakNextFromQueue();
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        // Stop any ongoing scans
        if (bluetoothLeScanner != null && scanCallback != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan: " + e.getMessage());
            }
        }

        // Properly disconnect and clean up GATT
        if (bluetoothGatt != null) {
            if (writableCharacteristic != null) {
                bluetoothGatt.setCharacteristicNotification(writableCharacteristic, false);
            }
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        // Shutdown TTS
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        stopLoadingSound();
        if (loadingPlayer != null) {
            loadingPlayer.release();
            loadingPlayer = null;
        }


        super.onDestroy();
    }
}