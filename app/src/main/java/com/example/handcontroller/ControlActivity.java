package com.example.handcontroller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.handcontroller.services.BluetoothService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class ControlActivity extends AppCompatActivity {

    private static final String TAG = "ControlActivity";

    // UI Components
    private SeekBar[] motorSeekBars;
    private TextView[] motorValues;
    private TextView connectionStatusText;
    private MaterialButton saveButton;
    private MaterialButton resetButton;
    private MaterialButton emergencyStopButton;
    private MaterialButton openHandButton;
    private MaterialButton closeHandButton;
    private MaterialButton peaceButton;
    // Constants
    private static final int NUM_MOTORS = 3;
    private static final int MAX_ANGLE = 180;
    private static final String PREFS_NAME = "MotorPrefs";
    private static final int OPEN_POSITION = 0;
    private static final int CLOSED_POSITION = 180;
    // Bluetooth Service
    private BluetoothService bluetoothService;
    private boolean serviceBound = false;

    // Service Connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            serviceBound = true;
            updateConnectionStatus();
            loadSavedPositions();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            bluetoothService = null;
            updateConnectionStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        initializeViews();
        setupBottomNavigation();
        setupMotorControls();
        setupButtons();
        bindBluetoothService();
    }

    private void initializeViews() {
        // Initialize arrays for motors
        motorSeekBars = new SeekBar[NUM_MOTORS];
        motorValues = new TextView[NUM_MOTORS];

        // Find views for each motor
        motorSeekBars[0] = findViewById(R.id.motor1SeekBar);
        motorSeekBars[1] = findViewById(R.id.motor2SeekBar);
        motorSeekBars[2] = findViewById(R.id.motor3SeekBar);

        motorValues[0] = findViewById(R.id.motor1Value);
        motorValues[1] = findViewById(R.id.motor2Value);
        motorValues[2] = findViewById(R.id.motor3Value);

        // Initialize other UI components
        connectionStatusText = findViewById(R.id.connectionStatus);
        saveButton = findViewById(R.id.saveButton);
        resetButton = findViewById(R.id.resetButton);
        emergencyStopButton = findViewById(R.id.emergencyStop);
        openHandButton = findViewById(R.id.openHandButton);
        closeHandButton = findViewById(R.id.closeHandButton);
        peaceButton = findViewById(R.id.peaceButton);

        // Set max values for seek bars
        for (SeekBar seekBar : motorSeekBars) {
            seekBar.setMax(MAX_ANGLE);
        }
    }

    private void setupMotorControls() {
        for (int i = 0; i < motorSeekBars.length; i++) {
            final int motorIndex = i;
            motorSeekBars[i].setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        updateMotorValue(motorIndex, progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        sendMotorValueToHardware(motorIndex, seekBar.getProgress());
                    }
                }
            );
        }
    }

    private void setupButtons() {
        saveButton.setOnClickListener(v -> saveMotorPositions());
        resetButton.setOnClickListener(v -> resetMotorPositions());
        emergencyStopButton.setOnClickListener(v -> handleEmergencyStop());
        openHandButton.setOnClickListener(v -> setOpenHand());
        closeHandButton.setOnClickListener(v -> setClosedHand());
        peaceButton.setOnClickListener(v -> setPeaceSign());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.control);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.control) {
                return true;
            }
            return false;
        });
    }

    private void bindBluetoothService() {
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void updateMotorValue(int motorIndex, int value) {
        motorValues[motorIndex].setText(String.format("%d°", value));
    }

    private void sendMotorValueToHardware(int motorIndex, int value) {
        if (bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendMotorCommand(motorIndex + 1, value);
        } else {
            showError("Not connected to device");
        }
    }

    private void saveMotorPositions() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        for (int i = 0; i < motorSeekBars.length; i++) {
            editor.putInt("motor" + i, motorSeekBars[i].getProgress());
        }
        editor.apply();
        Toast.makeText(this, "Positions saved", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedPositions() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        for (int i = 0; i < motorSeekBars.length; i++) {
            int savedPosition = prefs.getInt("motor" + i, 0);
            motorSeekBars[i].setProgress(savedPosition);
            updateMotorValue(i, savedPosition);
        }
    }

    private void resetMotorPositions() {
        for (int i = 0; i < motorSeekBars.length; i++) {
            motorSeekBars[i].setProgress(OPEN_POSITION);
            updateMotorValue(i, OPEN_POSITION);
            sendMotorValueToHardware(i, OPEN_POSITION);
        }
    }

    private void handleEmergencyStop() {
        if (bluetoothService != null) {
            bluetoothService.sendEmergencyStop();
            resetMotorPositions();
            Toast.makeText(this, "Emergency Stop Activated", Toast.LENGTH_SHORT).show();
        }
    }



    private void updateConnectionStatus() {
        if (bluetoothService != null && bluetoothService.isConnected()) {
            connectionStatusText.setText(R.string.connected);
            connectionStatusText.setTextColor(Color.GREEN);
        } else {
            connectionStatusText.setText(R.string.disconnected);
            connectionStatusText.setTextColor(Color.RED);
        }
    }

    private boolean checkConnection() {
        if (bluetoothService == null || !bluetoothService.isConnected()) {
            showError("Please connect to device first");
            return true;
        }
        return false;
    }
    private void setOpenHand() {
        if (checkConnection()) return;

        // Set all motors to open position
        for (int i = 0; i < NUM_MOTORS; i++) {
            motorSeekBars[i].setProgress(OPEN_POSITION);
            updateMotorValue(i, OPEN_POSITION);
            sendMotorValueToHardware(i, OPEN_POSITION);
        }
    }

    private void setClosedHand() {
        if (checkConnection()) return;

        // Set all motors to closed position
        for (int i = 0; i < NUM_MOTORS; i++) {
            motorSeekBars[i].setProgress(CLOSED_POSITION);
            updateMotorValue(i, CLOSED_POSITION);
            sendMotorValueToHardware(i, CLOSED_POSITION);
        }
    }

    private void setPeaceSign() {
        if (checkConnection()) return;

        // Motor 1 (thumb) - closed
        motorSeekBars[0].setProgress(CLOSED_POSITION);
        updateMotorValue(0, CLOSED_POSITION);
        sendMotorValueToHardware(0, CLOSED_POSITION);

        // Motor 2 (index and middle) - open
        motorSeekBars[1].setProgress(OPEN_POSITION);
        updateMotorValue(1, OPEN_POSITION);
        sendMotorValueToHardware(1, OPEN_POSITION);

        // Motor 3 (ring and pinky) - closed
        motorSeekBars[2].setProgress(CLOSED_POSITION);
        updateMotorValue(2, CLOSED_POSITION);
        sendMotorValueToHardware(2, CLOSED_POSITION);
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            saveMotorPositions();
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
