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
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // UI Components
    private GraphView graphSensor1, graphSensor2;
    private LineGraphSeries<DataPoint> seriesSensor1, seriesSensor2;
    private TextView signalStrengthText, connectionStatusText;
    private CardView statusCard;
    private MaterialButton connectButton;

    // Graph Configuration
    private static final int MAX_DATA_POINTS = 400;
    private static final int VIEWPORT_WIDTH = 20;
    private double lastX = 0;
    private static final int UPDATE_INTERVAL = 50; // 50ms = 20Hz update rate
    private static final int INITIAL_MAX_Y = 500;
    private static final int MIN_Y = -10;

    // Bluetooth Service
    private BluetoothService bluetoothService;
    private boolean serviceBound = false;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private ArrayList<BluetoothDevice> scannedDevices;
    private static final int PERMISSION_REQUEST_CODE = 100;

    // Random for demo data
    private final Random random = new Random();

    // Handlers and Runnables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateDataRunnable;

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
        setupGraphs();
        setupBottomNavigation();
        bindBluetoothService();
        startDataUpdates();
    }

    private void initializeViews() {
        graphSensor1 = findViewById(R.id.graphSensor1);
        graphSensor2 = findViewById(R.id.graphSensor2);
        signalStrengthText = findViewById(R.id.signalStrength);
        connectionStatusText = findViewById(R.id.connectionStatus);
        statusCard = findViewById(R.id.statusCard);
        connectButton = findViewById(R.id.btnConnect);

        connectButton.setOnClickListener(v -> attemptConnection());
    }

    private void setupGraphs() {
        // Initialize series
        seriesSensor1 = new LineGraphSeries<>();
        seriesSensor2 = new LineGraphSeries<>();

        // Configure both graphs
        configureGraph(graphSensor1, seriesSensor1, Color.CYAN, "EMG Signal 1");
        configureGraph(
            graphSensor2,
            seriesSensor2,
            Color.GREEN,
            "EMG Signal 2"
        );
    }

    private void configureGraph(
        GraphView graph,
        LineGraphSeries<DataPoint> series,
        int color,
        String title
    ) {
        // Series configuration
        series.setColor(color);
        series.setThickness(3);
        series.setTitle(title);
        series.setAnimated(true);

        // Add series to graph
        graph.addSeries(series);

        // Viewport configuration
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(VIEWPORT_WIDTH);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(MIN_Y);
        graph.getViewport().setMaxY(INITIAL_MAX_Y);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScalableY(true);
        graph.getViewport().setBackgroundColor(Color.rgb(30, 58, 87));

        // Grid label configuration
        GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
        gridLabel.setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        gridLabel.setHorizontalLabelsColor(Color.WHITE);
        gridLabel.setVerticalLabelsColor(Color.WHITE);
        gridLabel.setVerticalAxisTitle("Amplitude (mV)");
        gridLabel.setHorizontalAxisTitle("Time (s)");
        gridLabel.setTextSize(30f);
        gridLabel.setVerticalAxisTitleColor(Color.WHITE);
        gridLabel.setHorizontalAxisTitleColor(Color.WHITE);
        gridLabel.setLabelsSpace(5);

        // Custom label formatter
        gridLabel.setLabelFormatter(
            new DefaultLabelFormatter() {
                @Override
                public String formatLabel(double value, boolean isValueX) {
                    if (isValueX) {
                        return super.formatLabel(value, true);
                    } else {
                        if (Math.abs(Math.round(value) % 100) == 0) {
                            return String.valueOf((int) value);
                        }
                        return "";
                    }
                }
            }
        );

        // Calculate number of labels
        int totalRange = 510; // -10 to 500
        int stepSize = 100;
        int numLabels = (totalRange / stepSize) + 1;
        gridLabel.setNumVerticalLabels(numLabels);

        gridLabel.reloadStyles();

        // Legend configuration
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setTextColor(Color.WHITE);
        graph.getLegendRenderer().setTextSize(30f);
        graph.getLegendRenderer().setMargin(20);
        graph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);

        gridLabel.setPadding(50);
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
            bluetoothService.setOnDataReceivedListener((data, length) -> {
                double[] values = bluetoothService.parseEMGData(data);
                if (values.length >= 2) {
                    addDataPoint(values[0], values[1]);
                }
            });

            bluetoothService.setOnConnectionStateChangeListener(
                new BluetoothService.OnConnectionStateChangeListener() {
                    @Override
                    public void onStateChanged(int state) {
                        updateConnectionState(state);
                        if (state == BluetoothService.STATE_CONNECTED) {
                            stopDataUpdates();
                        } else if (state == BluetoothService.STATE_NONE) {
                            startDataUpdates();
                        }
                    }

                    @Override
                    public void onConnectionError(String message) {
                        showError(message);
                        startDataUpdates();
                    }
                }
            );
        }
    }

    private void startDataUpdates() {
        updateDataRunnable = new Runnable() {
            @Override
            public void run() {
                if (
                    !serviceBound ||
                    bluetoothService == null ||
                    !bluetoothService.isConnected()
                ) {
                    generateDemoData();
                }
                updateStatus();
                mainHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        mainHandler.post(updateDataRunnable);
    }

    private void stopDataUpdates() {
        if (updateDataRunnable != null) {
            mainHandler.removeCallbacks(updateDataRunnable);
        }
    }

    private void generateDemoData() {
        double time = (lastX * UPDATE_INTERVAL) / 1000.0;
        double baseSignal1 = 100 * Math.sin(2 * Math.PI * 0.5 * time);
        double baseSignal2 = 80 * Math.cos(2 * Math.PI * 0.3 * time);

        if (random.nextDouble() < 0.05) {
            baseSignal1 += random.nextDouble() * 400;
        }
        if (random.nextDouble() < 0.05) {
            baseSignal2 += random.nextDouble() * 300;
        }

        baseSignal1 += random.nextGaussian() * 10;
        baseSignal2 += random.nextGaussian() * 8;

        addDataPoint(baseSignal1, baseSignal2);
    }

    private void addDataPoint(double value1, double value2) {
        seriesSensor1.appendData(
            new DataPoint(lastX, value1),
            true,
            MAX_DATA_POINTS
        );
        seriesSensor2.appendData(
            new DataPoint(lastX, value2),
            true,
            MAX_DATA_POINTS
        );

        checkAndAdjustYBounds(graphSensor1, value1);
        checkAndAdjustYBounds(graphSensor2, value2);

        if (lastX > VIEWPORT_WIDTH) {
            graphSensor1.getViewport().setMinX(lastX - VIEWPORT_WIDTH);
            graphSensor1.getViewport().setMaxX(lastX);
            graphSensor2.getViewport().setMinX(lastX - VIEWPORT_WIDTH);
            graphSensor2.getViewport().setMaxX(lastX);
        }

        lastX += UPDATE_INTERVAL / 1000.0; // Increment X in seconds
    }

    private void checkAndAdjustYBounds(GraphView graph, double value) {
        double currentMax = graph.getViewport().getMaxY(true);
        if (value > currentMax) {
            double newMax = Math.max(500, value * 1.2); // Never go below 500
            graph.getViewport().setMaxY(newMax);
        }
    }

    private void updateStatus() {
        try {
            if (bluetoothService != null && bluetoothService.isConnected()) {
                signalStrengthText.setText(
                    getString(R.string.signal_strength, "Strong")
                );
                connectionStatusText.setText(R.string.connected);
                connectionStatusText.setTextColor(Color.GREEN);
                connectButton.setText(R.string.disconnect);
            } else {
                int signalLevel = (int) (Math.abs(Math.sin(lastX)) * 100);
                String signalStrength = signalLevel > 70
                    ? "Strong"
                    : signalLevel > 40 ? "Medium" : "Weak";
                signalStrengthText.setText(
                    getString(R.string.signal_strength, signalStrength)
                );
                connectionStatusText.setText(R.string.disconnected);
                connectionStatusText.setTextColor(Color.RED);
                connectButton.setText(R.string.connect_to_bluetooth);
            }
        } catch (SecurityException e) {
            Log.e(
                TAG,
                "Security Exception while updating status: " + e.getMessage()
            );
            showError("Permission denied: Unable to update connection status");
        }
    }

    private void attemptConnection() {
        try {
            if (bluetoothService != null) {
                if (bluetoothService.isConnected()) {
                    bluetoothService.closeConnection();
                    updateConnectionState(BluetoothService.STATE_NONE);
                    startDataUpdates();
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

        stopDataUpdates();
        scannedDevices = new ArrayList<>();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Device");

        // Create adapter for device list
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

                        final String finalDeviceInfo = deviceInfo;
                        runOnUiThread(() ->
                            deviceListAdapter.add(finalDeviceInfo)
                        );
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
                    break;
                case BluetoothService.STATE_CONNECTING:
                    connectionStatusText.setText(R.string.connecting);
                    connectionStatusText.setTextColor(Color.YELLOW);
                    break;
                case BluetoothService.STATE_NONE:
                    connectionStatusText.setText(R.string.disconnected);
                    connectionStatusText.setTextColor(Color.RED);
                    connectButton.setText(R.string.connect_to_bluetooth);
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
        stopDataUpdates();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
