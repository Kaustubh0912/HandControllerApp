package com.example.handcontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.handcontroller.services.BluetoothService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // UI Components
    private TextView signalStrengthText, connectionStatusText;
    private CardView statusCard;
    private MaterialButton connectButton;

    // Bluetooth Service
    private BluetoothService bluetoothService;
    private boolean serviceBound = false;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private ArrayList<BluetoothDevice> scannedDevices;
    private static final int PERMISSION_REQUEST_CODE = 100;

    // Bluetooth Enable Launcher
    private final ActivityResultLauncher<Intent> bluetoothEnableLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    connectToDevice();
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth must be enabled to use this app",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );

    // Service Connection
    private final ServiceConnection serviceConnection =
        new ServiceConnection() {
            @Override
            public void onServiceConnected(
                ComponentName name,
                IBinder service
            ) {
                BluetoothService.LocalBinder binder =
                    (BluetoothService.LocalBinder) service;
                bluetoothService = binder.getService();
                serviceBound = true;
                setupBluetoothCallbacks();
                updateConnectionState(
                    bluetoothService.isConnected()
                        ? BluetoothService.STATE_CONNECTED
                        : BluetoothService.STATE_NONE
                );
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                serviceBound = false;
                bluetoothService = null;
                updateConnectionState(BluetoothService.STATE_NONE);
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupBottomNavigation();
        bindBluetoothService();
    }

    private void initializeViews() {
        signalStrengthText = findViewById(R.id.signalStrength);
        connectionStatusText = findViewById(R.id.connectionStatus);
        statusCard = findViewById(R.id.statusCard);
        connectButton = findViewById(R.id.btnConnect);

        connectButton.setOnClickListener(v -> attemptConnection());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(
            R.id.bottom_navigation
        );
        bottomNavigationView.setSelectedItemId(R.id.home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.home) {
                return true;
            } else if (itemId == R.id.settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.control) {
                startActivity(new Intent(this, ControlActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void bindBluetoothService() {
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupBluetoothCallbacks() {
        if (bluetoothService != null) {
            bluetoothService.setOnConnectionStateChangeListener(
                new BluetoothService.OnConnectionStateChangeListener() {
                    @Override
                    public void onStateChanged(int state) {
                        updateConnectionState(state);
                    }

                    @Override
                    public void onConnectionError(String message) {
                        showError(message);
                    }
                }
            );
        }
    }

    private void attemptConnection() {
        try {
            if (bluetoothService != null) {
                if (bluetoothService.isConnected()) {
                    bluetoothService.closeConnection();
                    updateConnectionState(BluetoothService.STATE_NONE);
                } else {
                    if (checkAndRequestPermissions()) {
                        connectToDevice();
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(
                TAG,
                "Security Exception during connection attempt: " +
                e.getMessage()
            );
            showError("Permission denied: Unable to manage connection");
        }
    }

    private boolean checkAndRequestPermissions() {
        String[] requiredPermissions;
        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        ) {
            requiredPermissions = new String[] {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            };
        } else {
            requiredPermissions = new String[] {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            };
        }

        ArrayList<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (
                ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
            return false;
        }

        return true;
    }

    private void connectToDevice() {
        if (bluetoothService != null) {
            showDeviceSelectionDialog();
        }
    }

    private void showDeviceSelectionDialog() {
        if (
            ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) !=
                PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                },
                PERMISSION_REQUEST_CODE
            );
            return;
        }

        scannedDevices = new ArrayList<>();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Device");

        ArrayAdapter<String> deviceListAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_1
        );

        builder.setAdapter(deviceListAdapter, (dialog, which) -> {
            BluetoothDevice device = scannedDevices.get(which);
            try {
                bluetoothService.stopScan();
                bluetoothService.connect(device);
            } catch (SecurityException e) {
                Log.e(TAG, "Security Exception: " + e.getMessage());
                showError("Permission denied: Unable to connect to device");
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        if (bluetoothService != null) {
            bluetoothService.setOnDeviceFoundListener(device -> {
                try {
                    if (!scannedDevices.contains(device)) {
                        scannedDevices.add(device);
                        String deviceName = device.getName();
                        if (deviceName == null) deviceName = "Unknown Device";
                        String deviceInfo =
                            deviceName + " (" + device.getAddress() + ")";

                        runOnUiThread(() -> deviceListAdapter.add(deviceInfo));
                    }
                } catch (SecurityException e) {
                    Log.e(
                        TAG,
                        "Security Exception while accessing device info: " +
                        e.getMessage()
                    );
                    showError(
                        "Permission denied: Unable to access device information"
                    );
                }
            });

            try {
                bluetoothService.startScan();
            } catch (SecurityException e) {
                Log.e(
                    TAG,
                    "Security Exception while starting scan: " + e.getMessage()
                );
                showError("Permission denied: Unable to start scanning");
                dialog.dismiss();
                return;
            }

            new Handler()
                .postDelayed(
                    () -> {
                        try {
                            bluetoothService.stopScan();
                            if (deviceListAdapter.getCount() == 0) {
                                runOnUiThread(() -> {
                                    dialog.dismiss();
                                    showError("No devices found");
                                });
                            }
                        } catch (SecurityException e) {
                            Log.e(
                                TAG,
                                "Security Exception while stopping scan: " +
                                e.getMessage()
                            );
                            showError(
                                "Permission denied: Unable to stop scanning"
                            );
                        }
                    },
                    SCAN_PERIOD
                );
        }
    }

    private void updateConnectionState(int state) {
        runOnUiThread(() -> {
            switch (state) {
                case BluetoothService.STATE_CONNECTED:
                    connectionStatusText.setText(R.string.connected);
                    connectionStatusText.setTextColor(Color.GREEN);
                    connectButton.setText(R.string.disconnect);
                    signalStrengthText.setText(
                        getString(R.string.signal_strength, "Strong")
                    );
                    break;
                case BluetoothService.STATE_CONNECTING:
                    connectionStatusText.setText(R.string.connecting);
                    connectionStatusText.setTextColor(Color.YELLOW);
                    signalStrengthText.setText(
                        getString(R.string.signal_strength, "Connecting...")
                    );
                    break;
                case BluetoothService.STATE_NONE:
                    connectionStatusText.setText(R.string.disconnected);
                    connectionStatusText.setTextColor(Color.RED);
                    connectButton.setText(R.string.connect_to_bluetooth);
                    signalStrengthText.setText(
                        getString(R.string.signal_strength, "None")
                    );
                    break;
            }
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] grantResults
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        );
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                connectToDevice();
            } else {
                showError("Required permissions not granted");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
