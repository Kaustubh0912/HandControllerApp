package com.example.handcontroller.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Constants for message types
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int MESSAGE_READ = 3;
    public static final int MESSAGE_WRITE = 4;
    public static final int MESSAGE_STATE_CHANGE = 5;
    public static final int MESSAGE_DEVICE_NAME = 6;
    public static final int MESSAGE_TOAST = 7;

    // Member fields
    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private ConnectedThread connectedThread;
    private int state;
    private final Handler handler;
    private final ExecutorService executorService;
    private BluetoothDevice currentDevice;
    private OnDataReceivedListener dataListener;
    private OnConnectionStateChangeListener stateListener;

    // Buffer for storing incoming data
    private static final int BUFFER_SIZE = 1024;
    private final byte[] buffer = new byte[BUFFER_SIZE];

    public BluetoothService() {
        state = STATE_NONE;
        handler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // Interface for data reception
    public interface OnDataReceivedListener {
        void onDataReceived(byte[] data, int length);
    }

    // Interface for connection state changes
    public interface OnConnectionStateChangeListener {
        void onStateChanged(int state);
        void onConnectionError(String message);
    }

    // Binder class for client binding
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

    // Set listeners
    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        this.dataListener = listener;
    }

    public void setOnConnectionStateChangeListener(OnConnectionStateChangeListener listener) {
        this.stateListener = listener;
    }

    // Connect to a device
    public void connect(BluetoothDevice device) {
        if (state == STATE_CONNECTING) {
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }
        }

        currentDevice = device;
        setState(STATE_CONNECTING);

        executorService.execute(() -> {
            BluetoothSocket socket = null;
            try {
                socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                socket.connect();
                connected(socket);
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                notifyError("Failed to connect: " + e.getMessage());
                closeConnection();
            }
        });
    }

    // Handle successful connection
    private synchronized void connected(BluetoothSocket socket) {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(STATE_CONNECTED);
        notifyDeviceConnected(currentDevice.getName());
    }

    // Send data
    public void sendData(byte[] data) {
        ConnectedThread thread;
        synchronized (this) {
            if (state != STATE_CONNECTED) {
                return;
            }
            thread = connectedThread;
        }
        thread.write(data);
    }

    // Close the connection
    public synchronized void closeConnection() {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        setState(STATE_NONE);
    }

    // Set connection state
    private synchronized void setState(int newState) {
        state = newState;
        if (stateListener != null) {
            handler.post(() -> stateListener.onStateChanged(state));
        }
    }

    // Get current state
    public synchronized int getState() {
        return state;
    }

    // Notify of connection error
    private void notifyError(String message) {
        if (stateListener != null) {
            handler.post(() -> stateListener.onConnectionError(message));
        }
    }

    // Notify of device connection
    private void notifyDeviceConnected(String deviceName) {
        handler.post(() -> {
            // Implement any necessary UI updates here
        });
    }

    // Thread for handling the connection
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error creating streams", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            while (true) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0 && dataListener != null) {
                        byte[] data = Arrays.copyOf(buffer, bytes);
                        handler.post(() -> dataListener.onDataReceived(data, bytes));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Connection lost", e);
                    notifyError("Connection lost: " + e.getMessage());
                    closeConnection();
                    break;
                }
            }
        }

        public void write(byte[] data) {
            try {
                outputStream.write(data);
                handler.post(() -> {
                    // Implement write success callback if needed
                });
            } catch (IOException e) {
                Log.e(TAG, "Error writing data", e);
                notifyError("Error sending data: " + e.getMessage());
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
    }

    // Data parsing methods
    public double[] parseEMGData(byte[] data) {
        // Implement your EMG data parsing logic here
        // This is just an example implementation
        double[] parsedData = new double[2];
        if (data.length >= 4) {
            parsedData[0] = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
            parsedData[1] = (data[2] & 0xFF) | ((data[3] & 0xFF) << 8);
        }
        return parsedData;
    }

    // Helper method to convert int to bytes
    private byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeConnection();
        executorService.shutdown();
    }

    // Additional utility methods

    // Send a motor command
    public void sendMotorCommand(int motorId, int position) {
        byte[] command = new byte[3];
        command[0] = (byte) motorId;
        command[1] = (byte) (position & 0xFF);
        command[2] = (byte) ((position >> 8) & 0xFF);
        sendData(command);
    }

    // Send a calibration command
    public void sendCalibrationCommand(boolean start) {
        byte[] command = new byte[1];
        command[0] = (byte) (start ? 1 : 0);
        sendData(command);
    }

    // Check if device is connected
    public boolean isConnected() {
        return state == STATE_CONNECTED;
    }

    // Get current device
    public BluetoothDevice getCurrentDevice() {
        return currentDevice;
    }

    // Reconnect to last device
    public void reconnect() {
        if (currentDevice != null) {
            connect(currentDevice);
        }
    }

    // Helper method to format data for sending
    private byte[] formatDataPacket(byte command, byte[] data) {
        byte[] packet = new byte[data.length + 2];
        packet[0] = command;
        packet[1] = (byte) data.length;
        System.arraycopy(data, 0, packet, 2, data.length);
        return packet;
    }
}