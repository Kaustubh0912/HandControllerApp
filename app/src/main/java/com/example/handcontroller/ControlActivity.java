package com.example.handcontroller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
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
    private MaterialButton exerciseInfoButton;
    private MaterialButton emergencyStopButton;
    private MaterialButton presetOpenButton;
    private MaterialButton presetCloseButton;
    private MaterialButton presetMidButton;

    // Constants
    private static final int NUM_MOTORS = 3;
    private static final int MAX_ANGLE = 180;
    private static final String PREFS_NAME = "MotorPrefs";

    // Bluetooth Service
    private BluetoothService bluetoothService;
    private boolean serviceBound = false;

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
        exerciseInfoButton = findViewById(R.id.exerciseInfoButton);
        emergencyStopButton = findViewById(R.id.emergencyStop);
        presetOpenButton = findViewById(R.id.presetOpen);
        presetCloseButton = findViewById(R.id.presetClose);
        presetMidButton = findViewById(R.id.presetMid);

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
                        public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                        ) {
                            updateMotorValue(motorIndex, progress);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            sendMotorValueToHardware(
                                motorIndex,
                                seekBar.getProgress()
                            );
                        }
                    }
                );
        }
    }

    private void setupButtons() {
        saveButton.setOnClickListener(v -> saveMotorPositions());
        resetButton.setOnClickListener(v -> resetMotorPositions());
        exerciseInfoButton.setOnClickListener(v -> showExerciseInfo());
        emergencyStopButton.setOnClickListener(v -> handleEmergencyStop());

        presetOpenButton.setOnClickListener(v -> setPresetPosition("OPEN"));
        presetCloseButton.setOnClickListener(v -> setPresetPosition("CLOSE"));
        presetMidButton.setOnClickListener(v -> setPresetPosition("MID"));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(
            R.id.bottom_navigation
        );
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
        SharedPreferences.Editor editor = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        ).edit();
        for (int i = 0; i < motorSeekBars.length; i++) {
            editor.putInt("motor" + i, motorSeekBars[i].getProgress());
        }
        editor.apply();
        Toast.makeText(this, "Positions saved", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedPositions() {
        SharedPreferences prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        );
        for (int i = 0; i < motorSeekBars.length; i++) {
            int savedPosition = prefs.getInt("motor" + i, 0);
            motorSeekBars[i].setProgress(savedPosition);
            updateMotorValue(i, savedPosition);
        }
    }

    private void resetMotorPositions() {
        for (int i = 0; i < motorSeekBars.length; i++) {
            motorSeekBars[i].setProgress(0);
            updateMotorValue(i, 0);
            sendMotorValueToHardware(i, 0);
        }
    }

    private void setPresetPosition(String preset) {
        if (!checkConnection()) return;

        int targetPosition;
        switch (preset) {
            case "OPEN":
                targetPosition = MAX_ANGLE;
                break;
            case "CLOSE":
                targetPosition = 0;
                break;
            case "MID":
                targetPosition = MAX_ANGLE / 2;
                break;
            default:
                return;
        }

        for (int i = 0; i < motorSeekBars.length; i++) {
            motorSeekBars[i].setProgress(targetPosition);
            updateMotorValue(i, targetPosition);
            sendMotorValueToHardware(i, targetPosition);
        }
    }

    private void handleEmergencyStop() {
        if (bluetoothService != null) {
            bluetoothService.sendEmergencyStop();
            resetMotorPositions();
            Toast.makeText(
                this,
                "Emergency Stop Activated",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showExerciseInfo() {
        MarkdownDialog dialog = new MarkdownDialog(
            this,
            getString(R.string.exercise_info_title),
            getString(R.string.exercise_info_content)
        );
        dialog.show();
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
            return false;
        }
        return true;
    }

    private void showError(String message) {
        runOnUiThread(() ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        );
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
