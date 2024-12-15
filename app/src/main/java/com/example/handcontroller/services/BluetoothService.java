package com.example.handcontroller.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.util.UUID;

public class BluetoothService extends Service {

    private static final String TAG = "BluetoothService";

    // Connection states
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    // Command types
    private static final String CMD_MOTOR = "M";
    private static final String CMD_SENSOR = "S";
    private static final String CMD_CALIBRATE = "C";
    private static final String CMD_STOP = "STOP";

    // UUIDs for BLE service and characteristic - Update these with your device's UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString(
        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
    );
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString(
        "beb5483e-36e1-4688-b7f5-ea07361b26a8"
    );

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler;
    private int connectionState;
    private static BluetoothService instance;

    // Callbacks
    private OnConnectionStateChangeListener stateChangeListener;
    private OnDataReceivedListener dataReceivedListener;
    private OnDeviceFoundListener deviceFoundListener;

    // Binder
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {

        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        connectionState = STATE_NONE;
        handler = new Handler();
        initializeBluetooth();
    }

    private void initializeBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(
            BLUETOOTH_SERVICE
        );
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }
    }

    public static BluetoothService getInstance() {
        return instance;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Scanning methods
    public void startScan() {
        if (
            bluetoothLeScanner != null &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothLeScanner.startScan(scanCallback);
        }
    }

    public void stopScan() {
        if (
            bluetoothLeScanner != null &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    // Connection methods
    public void connect(BluetoothDevice device) {
        if (
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            connectionState = STATE_CONNECTING;
            notifyStateChange();
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    public void closeConnection() {
        if (bluetoothGatt != null) {
            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
        connectionState = STATE_NONE;
        notifyStateChange();
    }

    // Command sending methods
    public void sendMotorCommand(int motorId, int position) {
        if (!isConnected()) return;

        try {
            String command = String.format(
                "%s%d:%d",
                CMD_MOTOR,
                motorId,
                position
            );
            sendData(command.getBytes());
        } catch (Exception e) {
            Log.e(TAG, "Error sending motor command: " + e.getMessage());
            notifyError("Failed to send motor command: " + e.getMessage());
        }
    }

    public void sendSensorConfig(int sensorId, int sensitivity) {
        if (!isConnected()) return;

        try {
            String command = String.format(
                "%s%d:%d",
                CMD_SENSOR,
                sensorId,
                sensitivity
            );
            sendData(command.getBytes());
        } catch (Exception e) {
            Log.e(TAG, "Error sending sensor config: " + e.getMessage());
            notifyError("Failed to send sensor config: " + e.getMessage());
        }
    }

    public void sendCalibrationCommand(String type) {
        if (!isConnected()) return;

        try {
            String command = String.format("%s:%s", CMD_CALIBRATE, type);
            sendData(command.getBytes());
        } catch (Exception e) {
            Log.e(TAG, "Error sending calibration command: " + e.getMessage());
            notifyError(
                "Failed to send calibration command: " + e.getMessage()
            );
        }
    }

    public void sendEmergencyStop() {
        if (!isConnected()) return;

        try {
            sendData(CMD_STOP.getBytes());
        } catch (Exception e) {
            Log.e(TAG, "Error sending emergency stop: " + e.getMessage());
            notifyError("Failed to send emergency stop: " + e.getMessage());
        }
    }

    private void sendData(byte[] data) {
        if (
            bluetoothGatt == null ||
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        BluetoothGattCharacteristic characteristic = bluetoothGatt
            .getService(SERVICE_UUID)
            .getCharacteristic(CHARACTERISTIC_UUID);

        if (characteristic != null) {
            characteristic.setValue(data);
            bluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    // Callbacks
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (deviceFoundListener != null) {
                deviceFoundListener.onDeviceFound(result.getDevice());
            }
        }
    };

    private final BluetoothGattCallback gattCallback =
        new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(
                BluetoothGatt gatt,
                int status,
                int newState
            ) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    connectionState = STATE_CONNECTED;
                    notifyStateChange();
                    if (
                        ActivityCompat.checkSelfPermission(
                            BluetoothService.this,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        gatt.discoverServices();
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    connectionState = STATE_NONE;
                    notifyStateChange();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    enableCharacteristicNotification();
                }
            }

            @Override
            public void onCharacteristicChanged(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic
            ) {
                if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    byte[] data = characteristic.getValue();
                    notifyDataReceived(data);
                }
            }
        };

    private void enableCharacteristicNotification() {
        if (
            bluetoothGatt != null &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            BluetoothGattCharacteristic characteristic = bluetoothGatt
                .getService(SERVICE_UUID)
                .getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic != null) {
                bluetoothGatt.setCharacteristicNotification(
                    characteristic,
                    true
                );
            }
        }
    }

    // Notification methods
    private void notifyStateChange() {
        if (stateChangeListener != null) {
            handler.post(() ->
                stateChangeListener.onStateChanged(connectionState)
            );
        }
    }

    private void notifyDataReceived(byte[] data) {
        if (dataReceivedListener != null) {
            handler.post(() ->
                dataReceivedListener.onDataReceived(data, data.length)
            );
        }
    }

    private void notifyError(String message) {
        if (stateChangeListener != null) {
            handler.post(() -> stateChangeListener.onConnectionError(message));
        }
    }

    // Status methods
    public boolean isConnected() {
        return connectionState == STATE_CONNECTED;
    }

    public BluetoothDevice getCurrentDevice() {
        return bluetoothGatt != null ? bluetoothGatt.getDevice() : null;
    }

    // Data parsing
    public double[] parseEMGData(byte[] data) {
        double[] values = new double[2];
        if (data != null && data.length >= 8) {
            values[0] = (double) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
            values[1] = (double) (((data[2] & 0xFF) << 8) | (data[3] & 0xFF));
        }
        return values;
    }

    // Listener interfaces
    public interface OnConnectionStateChangeListener {
        void onStateChanged(int state);
        void onConnectionError(String message);
    }

    public interface OnDataReceivedListener {
        void onDataReceived(byte[] data, int length);
    }

    public interface OnDeviceFoundListener {
        void onDeviceFound(BluetoothDevice device);
    }

    // Setter methods for listeners
    public void setOnConnectionStateChangeListener(
        OnConnectionStateChangeListener listener
    ) {
        this.stateChangeListener = listener;
    }

    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        this.dataReceivedListener = listener;
    }

    public void setOnDeviceFoundListener(OnDeviceFoundListener listener) {
        this.deviceFoundListener = listener;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeConnection();
        handler.removeCallbacksAndMessages(null);
        instance = null;
    }
}
