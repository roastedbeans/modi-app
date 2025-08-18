# QMDL Data Gatherer

An Android application for gathering QMDL (Qualcomm Mobile Data Logging) data from mobile devices with root access.

## Features

- **Root Access Management**: Automatically checks for and utilizes root access
- **QMDL Data Collection**: Uses `diag_mdlog` command to gather diagnostic data
- **Single Button Control**: Start/Stop functionality with a single toggle button
- **Real-time Logging**: Live display of command output and status updates
- **Automatic Backup**: Automatically backs up .qmdl files to app's external storage
- **File Size Monitoring**: Displays the size of collected data

## Requirements

- Android device with root access (SuperSU, Magisk, etc.)
- Android 7.0 (API level 24) or higher
- External storage permissions

## Installation

1. Build the project using Android Studio
2. Install the APK on your rooted device
3. Grant root permissions when prompted
4. Grant storage permissions when prompted

## Usage

### Starting QMDL Data Gathering

1. Launch the app
2. The app will automatically check for root access
3. Press the "Start QMDL Gathering" button
4. Grant root permissions if prompted by your root manager
5. The app will execute the following command:
   ```bash
   su
   diag_mdlog -o /sdcard/diag_logs -f /sdcard/diag_logs/Diag.cfg -m /sdcard/diag_logs/Diag.cfg
   ```

### Stopping QMDL Data Gathering

1. Press the "Stop QMDL Gathering" button
2. The app will terminate the diag_mdlog process
3. QMDL files will be automatically backed up to the app's external storage
4. Files are saved in a timestamped directory: `QMDL_Logs/QMDL_YYYYMMDD_HHMMSS/`

### Monitoring

- **Status Display**: Shows current operation status
- **Live Log**: Displays real-time output from the diag_mdlog command
- **File Size**: Shows the total size of collected QMDL data

## File Locations

### Source Directory

- QMDL files are initially saved to: `/sdcard/diag_logs/`

### Backup Directory

- Backed up files are stored in: `Android/data/com.example.modiv3/files/QMDL_Logs/`

## Technical Details

### Commands Executed

1. **Root Check**: `su` with exit command
2. **Directory Setup**: Creates `/sdcard/diag_logs/` and `Diag.cfg` file
3. **Data Gathering**: `diag_mdlog -o /sdcard/diag_logs -f /sdcard/diag_logs/Diag.cfg -m /sdcard/diag_logs/Diag.cfg`

### Configuration File

The app creates a basic `Diag.cfg` file with the following content:

```
# Basic QMDL configuration
log_mask_qxdm=0x1
log_mask_qxdm2=0x1
```

### Permissions Required

- `WRITE_EXTERNAL_STORAGE`: For saving QMDL files
- `READ_EXTERNAL_STORAGE`: For reading QMDL files
- `MANAGE_EXTERNAL_STORAGE`: For managing external storage
- `INTERNET`: For network operations
- `ACCESS_NETWORK_STATE`: For network state monitoring
- `FOREGROUND_SERVICE`: For service operations
- `WAKE_LOCK`: For keeping the device awake during data gathering

## Troubleshooting

### Root Access Issues

- Ensure your device is properly rooted
- Check that your root manager (SuperSU, Magisk) is working
- Grant root permissions to the app when prompted

### Storage Issues

- Ensure you have sufficient storage space
- Grant storage permissions to the app
- Check that external storage is mounted

### Command Execution Issues

- Verify that `diag_mdlog` is available on your device
- Check that the device supports QMDL logging
- Ensure the device is not in airplane mode

## Security Notes

- This app requires root access and can execute system commands
- QMDL data may contain sensitive information
- Store collected data securely
- Only use on devices you own or have permission to test

## Development

### Building from Source

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build the project
5. Install on a rooted device

### Key Classes

- `MainActivity`: Main UI and user interaction
- `QmdlService`: Service for managing QMDL data gathering
- `QmdlUtils`: Utility functions for file operations and root access

## License

This project is for educational and testing purposes. Use responsibly and in accordance with applicable laws and regulations.
