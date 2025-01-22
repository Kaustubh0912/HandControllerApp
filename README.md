# HandController - Prosthetic Hand Control Application

## Overview
HandController is an Android application designed to control a prosthetic hand via Bluetooth Low Energy (BLE) connection. The app provides intuitive controls for individual motor movements, preset gestures, and device calibration in multiple languages.

## Features

### 1. Bluetooth Connectivity
- Easy device discovery and pairing
- Stable BLE connection management
- Real-time connection status monitoring
- Signal strength indication

### 2. Motor Control
- Individual control of 3 servo motors
- Angle adjustment from 0° to 180°
- Real-time position feedback
- Save and load motor positions
- Emergency stop functionality

### 3. Preset Gestures
- Open hand (✋)
- Closed fist (✊)
- Peace sign (✌️)
- Custom gesture saving capability

### 4. Calibration
- Guided calibration process
- Step-by-step instructions
- Sensor sensitivity adjustment
- Auto-calibration feature

### 5. Multi-language Support
- English
- Hindi (हिन्दी)
- Tamil (தமிழ்)
- Telugu (తెలుగు)
- Malayalam (മലയാളം)

## Technical Requirements

### Minimum Requirements
- Android SDK 23 (Android 6.0 Marshmallow)
- Bluetooth Low Energy (BLE) capable device
- Storage: 50MB
- RAM: 1GB

### Recommended Requirements
- Android SDK 34 (Android 14)
- Bluetooth 5.0
- Storage: 100MB
- RAM: 2GB

## Installation

1. Clone the repository:
```bash
git clone https://github.com/Kaustubh0912/HandController.git
```

2. Open the project in Android Studio

3. Build and run the application:
```bash
./gradlew assembleDebug
```

## Usage

### Initial Setup
1. Enable Bluetooth on your device
2. Launch the HandController app
3. Grant necessary permissions when prompted
4. Click "Connect to Bluetooth" to discover nearby devices

### Basic Control
1. Navigate to the Control screen
2. Use sliders to adjust individual motor positions
3. Monitor connection status and signal strength
4. Use preset buttons for common gestures

### Calibration
1. Go to Settings
2. Select "Calibrate"
3. Follow on-screen instructions
4. Wait for confirmation of successful calibration

## Permissions Required
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `VIBRATE`
- `WRITE_EXTERNAL_STORAGE`
- `READ_EXTERNAL_STORAGE`

## Architecture
- Built using Android's native BLE APIs
- MVVM architecture pattern
- Service-based Bluetooth communication
- SharedPreferences for data persistence

## Contributing
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License
This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments
- Bluetooth Low Energy Android documentation
- Material Design components
- Android Jetpack libraries
- GraphView library for data visualization

## Support
For support, please open an issue in the GitHub repository or contact [kaustubharun2003@gmail.com]

## Version History
- v1.0.0 (Initial Release)
  - Basic motor control
  - Bluetooth connectivity
  - Multi-language support

## Roadmap
- [ ] Add haptic feedback
- [ ] Implement gesture recording
- [ ] Advanced calibration options
- [ ] Data logging and analytics
- [ ] Cloud backup support

## Authors
- Kaustubh Agrawal aka NOX- Initial work - [YourGitHub](https://github.com/Kaustubh0912)

## Project Status
Active development - Bug reports and feature requests are welcome!
