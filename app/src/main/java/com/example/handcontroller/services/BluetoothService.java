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

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";

    // Connection states
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    // UUIDs for BLE service and characteristic
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler;
    private int connectionState;

    // Callbacks
    private OnConnectionStateChangeListener stateChangeListener;
    private OnDataReceivedListener dataReceivedListener;
    private OnDeviceFoundListener deviceFoundListener;

    // Binder
    private final IBinder binder = new LocalBinder();

    // Required default constructor
    public BluetoothService() {
        // Initialize any non-context dependent variables here
        connectionState = STATE_NONE;
        handler = new Handler();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize context-dependent variables here
        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }
    }

    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Scanning methods
    public void startScan() {
        if (bluetoothLeScanner != null && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner.startScan(scanCallback);
        }
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (deviceFoundListener != null) {
                deviceFoundListener.onDeviceFound(result.getDevice());
            }
        }
    };

    // Connection methods
    public void connect(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            connectionState = STATE_CONNECTING;
            notifyStateChange();
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    public void closeConnection() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
        connectionState = STATE_NONE;
        notifyStateChange();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED;
                notifyStateChange();
                if (ActivityCompat.checkSelfPermission(BluetoothService.this,
                        android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
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
                // Enable notifications for the characteristic
                enableCharacteristicNotification();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                notifyDataReceived(data);
            }
        }
    };

    private void enableCharacteristicNotification() {
        if (bluetoothGatt != null && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            BluetoothGattCharacteristic characteristic =
                    bluetoothGatt.getService(SERVICE_UUID).getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic != null) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
            }
        }
    }

    // Helper methods
    private void notifyStateChange() {
        if (stateChangeListener != null) {
            handler.post(() -> stateChangeListener.onStateChanged(connectionState));
        }
    }

    private void notifyDataReceived(byte[] data) {
        if (dataReceivedListener != null) {
            handler.post(() -> dataReceivedListener.onDataReceived(data, data.length));
        }
    }

    // Status methods
    public boolean isConnected() {
        return connectionState == STATE_CONNECTED;
    }

    public BluetoothDevice getCurrentDevice() {
        return bluetoothGatt != null ? bluetoothGatt.getDevice() : null;
    }

    // Data parsing method
    public double[] parseEMGData(byte[] data) {
        double[] values = new double[2];
        if (data != null && data.length >= 8) {
            values[0] = (double) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
            values[1] = (double) (((data[2] & 0xFF) << 8) | (data[3] & 0xFF));
        }
        return values;
    }

    // Interfaces
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
    public void setOnConnectionStateChangeListener(OnConnectionStateChangeListener listener) {
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
    }
}