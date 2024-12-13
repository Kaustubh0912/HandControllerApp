package com.example.handcontroller;

import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class ControlActivity extends AppCompatActivity {

    private SeekBar[] motorSeekBars;
    private TextView[] motorValues;
    private MaterialButton saveButton;
    private MaterialButton resetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        initializeViews();
        setupBottomNavigation();
        setupMotorControls();
        setupButtons();
    }

    private void initializeViews() {
        // Initialize arrays for 3 motors
        motorSeekBars = new SeekBar[3];
        motorValues = new TextView[3];

        // Find views for each motor
        motorSeekBars[0] = findViewById(R.id.motor1SeekBar);
        motorSeekBars[1] = findViewById(R.id.motor2SeekBar);
        motorSeekBars[2] = findViewById(R.id.motor3SeekBar);

        motorValues[0] = findViewById(R.id.motor1Value);
        motorValues[1] = findViewById(R.id.motor2Value);
        motorValues[2] = findViewById(R.id.motor3Value);

        saveButton = findViewById(R.id.saveButton);
        resetButton = findViewById(R.id.resetButton);
    }

    private void setupMotorControls() {
        // Set up listeners for each motor's seek bar
        for (int i = 0; i < motorSeekBars.length; i++) {
            final int motorIndex = i;
            motorSeekBars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateMotorValue(motorIndex, progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Here you would send the updated value to your hardware
                    sendMotorValueToHardware(motorIndex, seekBar.getProgress());
                }
            });
        }
    }

    private void setupButtons() {
        saveButton.setOnClickListener(v -> saveMotorPositions());
        resetButton.setOnClickListener(v -> resetMotorPositions());
    }

    private void updateMotorValue(int motorIndex, int value) {
        motorValues[motorIndex].setText(String.format("%d°", value));
    }

    private void sendMotorValueToHardware(int motorIndex, int value) {
        // TODO: Implement hardware communication
        // Example: send command via Bluetooth or other protocol
        String command = String.format("M%d:%d", motorIndex + 1, value);
        // sendBluetoothCommand(command);
    }

    private void saveMotorPositions() {
        // Save current positions to SharedPreferences or database
        for (int i = 0; i < motorSeekBars.length; i++) {
            int position = motorSeekBars[i].getProgress();
            // TODO: Save position for motor i
        }
    }

    private void resetMotorPositions() {
        // Reset all seek bars to 0
        for (int i = 0; i < motorSeekBars.length; i++) {
            motorSeekBars[i].setProgress(0);
            updateMotorValue(i, 0);
        }
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

    @Override
    protected void onPause() {
        super.onPause();
        // Save positions when activity is paused
        saveMotorPositions();
    }
}
