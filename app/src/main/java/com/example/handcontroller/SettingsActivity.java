package com.example.handcontroller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.handcontroller.utils.InstructionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextView selectLanguage;
    private MaterialButton autoCalibrateButton;
    private TextView calibrationInstructions;
    private InstructionManager instructionManager;
    private MaterialButton selectDelay;
    private MaterialButton selectActuationType;
    private TextView currentDelay;
    private TextView currentActuation;
    private static final int MIN_DELAY = 500;
    private static final int MAX_DELAY = 3000;
    private static final int DELAY_STEP = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply saved language preference
        applySavedLanguage();

        setContentView(R.layout.activity_settings);

        // Initialize InstructionManager
        instructionManager = InstructionManager.getInstance(this);

        // Initialize views
        selectLanguage = findViewById(R.id.selectLanguage);
        autoCalibrateButton = findViewById(R.id.autocalibrate);
        calibrationInstructions = findViewById(R.id.calibrationInstructions);
        selectDelay = findViewById(R.id.selectDelay);
        selectActuationType = findViewById(R.id.selectActuationType);
        currentDelay = findViewById(R.id.currentDelay);
        currentActuation = findViewById(R.id.currentActuation);

        // Set click listeners
        selectLanguage.setOnClickListener(v -> openLanguageMenu(v));
        autoCalibrateButton.setOnClickListener(v -> startCalibration());
        selectDelay.setOnClickListener(v -> openDelayMenu(v));
        selectActuationType.setOnClickListener(v -> openActuationMenu(v));

        // Load saved preferences
        loadSavedPreferences();

        // Bottom navigation setup
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.settings);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.settings) {
                return true;
            } else if (itemId == R.id.control) {
                startActivity(new Intent(this, ControlActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void startCalibration() {
        autoCalibrateButton.setVisibility(View.GONE);
        try {
            JSONObject initialResponse = new JSONObject();
            initialResponse.put("calibration_state", "INITIAL");
            instructionManager.updateInstructionsFromApiResponse(initialResponse);
            updateCalibrationInstructions();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateCalibrationInstructions() {
        calibrationInstructions.setVisibility(View.VISIBLE);
        String currentInstruction = instructionManager.getCurrentInstruction();
        calibrationInstructions.setText(currentInstruction);

        calibrationInstructions.setOnClickListener(v -> {
            if (instructionManager.hasMoreInstructions()) {
                instructionManager.advanceInstruction();
                String nextInstruction = instructionManager.getCurrentInstruction();
                calibrationInstructions.setText(nextInstruction);
            } else {
                finishCalibration();
            }
        });
    }

    private void finishCalibration() {
        try {
            JSONObject completedResponse = new JSONObject();
            completedResponse.put("calibration_state", "COMPLETED");
            instructionManager.updateInstructionsFromApiResponse(completedResponse);
            calibrationInstructions.setText(instructionManager.getCurrentInstruction());

            calibrationInstructions.postDelayed(() -> {
                calibrationInstructions.setVisibility(View.GONE);
                autoCalibrateButton.setVisibility(View.VISIBLE);
            }, 2000);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void openLanguageMenu(View v) {
        registerForContextMenu(selectLanguage);
        openContextMenu(selectLanguage);
        unregisterForContextMenu(selectLanguage);
    }

    private void openDelayMenu(View v) {
        registerForContextMenu(selectDelay);
        openContextMenu(selectDelay);
        unregisterForContextMenu(selectDelay);
    }

    private void openActuationMenu(View v) {
        registerForContextMenu(selectActuationType);
        openContextMenu(selectActuationType);
        unregisterForContextMenu(selectActuationType);
    }

    private void loadSavedPreferences() {
        SharedPreferences preferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int delay = preferences.getInt("sensor_delay", 500);
        String actuationType = preferences.getString("actuation_type", "Momentary");

        currentDelay.setText(String.format(getString(R.string.current_delay), delay));
        currentActuation.setText(actuationType);
    }

    @Override
    public void onCreateContextMenu(@NonNull android.view.ContextMenu menu, @NonNull View v, android.view.ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.selectLanguage) {
            menu.setHeaderTitle(getString(R.string.select_language));
            menu.add(0, 1, 0, getString(R.string.english));
            menu.add(0, 2, 1, getString(R.string.hindi));
            menu.add(0, 3, 2, getString(R.string.tamil));
            menu.add(0, 4, 3, getString(R.string.telugu));
            menu.add(0, 5, 4, getString(R.string.malayalam));
        } else if (v.getId() == R.id.selectDelay) {
            menu.setHeaderTitle("Select Delay");
            for (int delay = MIN_DELAY; delay <= MAX_DELAY; delay += DELAY_STEP) {
                menu.add(1, delay, 0, delay + " ms");
            }
        } else if (v.getId() == R.id.selectActuationType) {
            menu.setHeaderTitle("Select Actuation Type");
            menu.add(2, 1, 0, "Momentary");
            menu.add(2, 2, 1, "Latching");
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (item.getGroupId() == 0) {
            // Language selection
            String languageCode = "en"; // Default to English
            switch (item.getItemId()) {
                case 1:
                    languageCode = "en"; // English
                    break;
                case 2:
                    languageCode = "hi"; // Hindi
                    break;
                case 3:
                    languageCode = "ta"; // Tamil
                    break;
                case 4:
                    languageCode = "te"; // Telugu
                    break;
                case 5:
                    languageCode = "ml"; // Malayalam
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
            changeLanguage(languageCode);
            return true;
        } else if (item.getGroupId() == 1) {
            // Delay selection
            SharedPreferences preferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            int delay = item.getItemId();
            editor.putInt("sensor_delay", delay);
            editor.apply();
            currentDelay.setText(String.format(getString(R.string.current_delay), delay));
            return true;
        } else if (item.getGroupId() == 2) {
            // Actuation type selection
            SharedPreferences preferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            String actuationType = item.getItemId() == 1 ? "Momentary" : "Latching";
            editor.putString("actuation_type", actuationType);
            editor.apply();
            currentActuation.setText(actuationType);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void changeLanguage(String languageCode) {
        SharedPreferences preferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("language", languageCode);
        editor.apply();

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void applySavedLanguage() {
        SharedPreferences preferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String languageCode = preferences.getString("language", "en");
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}
