# MODIV3 - Mobile Network Diagnostic and Analysis Platform

## Overview

MODIV3 is a comprehensive Android application designed for advanced mobile network diagnostics, data collection, and security analysis. The application leverages Qualcomm Mobile Data Logging (QMDL) technology to gather detailed network diagnostic information from mobile devices with root access, providing insights into 4G and 5G network behavior, performance metrics, and security vulnerabilities.

## Current Implementation

### Core Features

- **Root Access Management**: Automated root access detection and management using `su` commands
- **QMDL Data Collection**: Utilizes `diag_mdlog` command for comprehensive diagnostic data gathering
- **Service-Based Architecture**: Background service (`QmdlService`) for persistent data collection
- **Real-time Monitoring**: Live display of command output and status updates
- **Automatic Setup**: Self-configuring directories and configuration files
- **Error Handling**: Comprehensive retry mechanisms and fallback options
- **Permission Management**: Automatic handling of storage and root permissions

### Technical Architecture

#### Main Components

1. **MainActivity**: Primary UI controller with real-time status updates and log display
2. **QmdlService**: Background service managing QMDL data collection and process lifecycle
3. **QmdlUtils**: Utility functions for root access verification and storage management
4. **RootManager**: Dedicated root access management and command execution

#### Data Collection Process

1. **Initialization**: Service startup with automatic permission establishment
2. **Directory Setup**: Creates `/sdcard/diag_logs/` with proper permissions
3. **Configuration**: Deploys `Diag.cfg` from assets with QMDL logging parameters
4. **Data Gathering**: Executes `diag_mdlog -o /sdcard/diag_logs -f /sdcard/diag_logs/Diag.cfg -m /sdcard/diag_logs/Diag.cfg`
5. **Monitoring**: Real-time process monitoring and output capture

### User Interface

- **Single Toggle Button**: Start/Stop QMDL data collection
- **Status Display**: Real-time operation status and setup progress
- **Live Log Viewer**: Terminal-style output display with auto-scroll
- **Material Design**: Modern Android UI with Material Components

## Requirements

### System Requirements

- **Android Version**: Android 7.0 (API level 24) or higher
- **Root Access**: SuperSU, Magisk, or equivalent root manager
- **Hardware**: Qualcomm Snapdragon chipset with QMDL support
- **Storage**: Minimum 100MB free storage space
- **Network**: 4G LTE and/or 5G NR capable device

### Current Device Implementation

- **Tested Device**: OnePlus 9 Pro 5G (LE2120)
- **Chipset**: Qualcomm Snapdragon 888
- **Android Version**: Android 11/12/13
- **Root Method**: Magisk (recommended)
- **QMDL Support**: Verified working with `diag_mdlog` command

### Device Compatibility

- **Qualcomm Devices**: All Qualcomm-based devices with QMDL support
- **5G Devices**: 5G NR capable devices for advanced network analysis
- **4G Devices**: LTE devices for basic network diagnostics
- **Root Required**: Device must be rooted for QMDL data access

### Permissions Required

- `WRITE_EXTERNAL_STORAGE`: QMDL file storage
- `READ_EXTERNAL_STORAGE`: QMDL file access
- `MANAGE_EXTERNAL_STORAGE`: External storage management
- `INTERNET`: Network operations
- `ACCESS_NETWORK_STATE`: Network state monitoring
- `FOREGROUND_SERVICE`: Background service operation
- `WAKE_LOCK`: Device wake maintenance during collection

## Installation and Setup

### Building from Source

1. **Clone Repository**

   ```bash
   git clone <repository-url>
   cd MODIV3
   ```

2. **Open in Android Studio**

   - Import project into Android Studio
   - Sync Gradle files
   - Build project (Ctrl+F9 or Cmd+F9)

3. **Install on Device**
   - Enable Developer Options and USB Debugging
   - Connect rooted device via USB
   - Install APK: `adb install app/build/outputs/apk/debug/app-debug.apk`

### Device Preparation

1. **Root Access Verification**

   - Ensure device is properly rooted (Magisk recommended)
   - Test root access: `adb shell su -c "id"`
   - Verify `diag_mdlog` availability: `adb shell su -c "which diag_mdlog"`
   - For OnePlus 9 Pro: Ensure Magisk is properly installed and configured

2. **Qualcomm Chipset Verification**

   - Confirm device has Qualcomm Snapdragon chipset
   - Check chipset: `adb shell cat /proc/cpuinfo | grep -i qualcomm`
   - Verify QMDL support: `adb shell su -c "ls /vendor/bin/diag_mdlog"`

3. **Storage Permissions**
   - Grant storage permissions when prompted
   - Ensure external storage is mounted and writable
   - For OnePlus 9 Pro: Grant "Files and media" permission

## Usage Guide

### Starting Data Collection

1. **Launch Application**

   - Open MODIV3 app
   - Wait for service initialization (status: "Setting up service...")

2. **Monitor Setup Progress**

   - Service automatically establishes root access
   - Creates necessary directories and configuration files
   - Verifies all prerequisites

3. **Begin Collection**
   - Tap "Start QMDL Gathering" button
   - Grant root permissions when prompted by root manager
   - Monitor real-time output in log viewer

### Monitoring and Control

- **Status Updates**: Real-time status display shows current operation
- **Live Logs**: Terminal output shows `diag_mdlog` activity
- **Process Control**: Single button toggles between start/stop states
- **Error Handling**: Automatic retry mechanisms for failed operations

### Stopping Data Collection

1. **Graceful Shutdown**

   - Tap "Stop QMDL Gathering" button
   - Service terminates `diag_mdlog` process
   - Files remain in `/sdcard/diag_logs/`

2. **Data Verification**
   - Check log output for successful termination
   - Verify QMDL files in target directory

## File Structure and Data

### Generated Files

```
/sdcard/diag_logs/
├── Diag.cfg                    # QMDL configuration file
├── *.qmdl                      # QMDL diagnostic data files
├── *.qmdl2                     # Additional QMDL data files
└── diag_logs_*.txt            # Process output logs
```

### Configuration File

<<<<<<< HEAD
The app creates a basic `Diag.cfg` file with the following content:
=======
The application creates a `Diag.cfg` file with basic QMDL logging parameters:

> > > > > > > b8147b2 (added readme file to align to the app)

```
# Basic QMDL configuration
log_mask_qxdm=0x1
log_mask_qxdm2=0x1
```

## Future Implementation Roadmap

### Phase 1: Network Data Parser (Q4 2024)

#### NAS (Non-Access Stratum) Data Extraction

- **Protocol Parsing**: Extract NAS messages from QMDL data
- **Message Types**: Authentication, Security, Session Management
- **Data Fields**: IMSI, TMSI, Authentication vectors, Security algorithms
- **Output Format**: JSON/CSV with timestamp and message details

#### RRC (Radio Resource Control) Data Analysis

- **4G LTE RRC**: Connection establishment, handover, measurement reports
- **5G NR RRC**: Initial access, mobility, beam management
- **Key Parameters**: Cell ID, RSRP, RSRQ, SINR, CQI
- **Event Tracking**: Handover events, connection failures, quality metrics

### Phase 2: Network Analysis Engine (Q1 2025)

#### Preprocessing Pipeline

- **Data Cleaning**: Remove duplicates, handle missing values
- **Normalization**: Standardize signal strength and quality metrics
- **Feature Extraction**: Derive network performance indicators
- **Time Series Analysis**: Trend detection and anomaly identification

#### Network Specification Analysis

- **Protocol Compliance**: Verify 3GPP standard adherence
- **Performance Metrics**: Throughput, latency, packet loss analysis
- **Coverage Mapping**: Signal strength and quality visualization
- **Interference Detection**: Identify sources of network degradation

### Phase 3: AI-Based Intrusion Detection System (Q2 2025)

#### Machine Learning Models

- **Anomaly Detection**: Unsupervised learning for network behavior analysis
- **Classification Models**: Identify attack types and severity levels
- **Real-time Processing**: Stream processing for immediate threat detection
- **Model Training**: Continuous learning from new attack patterns

#### Attack Detection Capabilities

##### 4G LTE Attacks

- **IMSI Catching**: Detect fake base stations and tracking attempts
- **Downgrade Attacks**: Identify forced fallback to 2G/3G
- **Authentication Bypass**: Detect unauthorized access attempts
- **Denial of Service**: Identify network flooding and resource exhaustion

##### 5G NR Attacks

- **Stingray Detection**: Identify IMSI catchers and fake gNBs
- **Privacy Attacks**: Detect tracking and profiling attempts
- **Network Slicing Attacks**: Identify unauthorized slice access
- **Beamforming Attacks**: Detect malicious beam manipulation

#### Security Features

- **Threat Scoring**: Risk assessment and severity classification
- **Alert System**: Real-time notifications for detected threats
- **Forensic Analysis**: Detailed attack reconstruction and evidence collection
- **Compliance Reporting**: Generate security audit reports

### Phase 4: Advanced Analytics Dashboard (Q3 2025)

#### Visualization Components

- **Network Topology**: Interactive network map with real-time status
- **Performance Metrics**: Charts and graphs for network KPIs
- **Security Dashboard**: Threat overview and incident timeline
- **Historical Analysis**: Long-term trend analysis and reporting

#### Integration Capabilities

- **SIEM Integration**: Export alerts to security information systems
- **API Endpoints**: RESTful API for external system integration
- **Data Export**: Multiple format support (JSON, CSV, XML)
- **Cloud Storage**: Secure cloud backup and analysis

## Development Guidelines

### Code Structure

```
app/src/main/java/com/example/modiv3/
├── MainActivity.java           # Main UI controller
├── QmdlService.java           # Background service
├── QmdlUtils.java             # Utility functions
└── RootManager.java           # Root access management
```

### Key Design Patterns

- **Service-Oriented Architecture**: Background processing for data collection
- **Callback Interface**: Asynchronous communication between components
- **Retry Mechanisms**: Robust error handling and recovery
- **Permission Management**: Comprehensive access control

### Testing Strategy

- **Unit Tests**: Individual component testing
- **Integration Tests**: Service and UI interaction testing
- **Root Access Testing**: Device-specific root functionality
- **Performance Testing**: Memory and CPU usage optimization

## Troubleshooting

<<<<<<< HEAD

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
- # Ensure the device is not in airplane mode

### Common Issues

#### Root Access Problems

- **Symptom**: "Root access not available" error
- **Solution**: Verify root manager (Magisk/SuperSU) is properly installed
- **Verification**: Test with `adb shell su -c "id"`
- **OnePlus 9 Pro**: Ensure Magisk is installed and root access is granted to the app
  > > > > > > > b8147b2 (added readme file to align to the app)

#### Storage Permission Issues

- **Symptom**: "Directory not writable" error
- **Solution**: Grant storage permissions in app settings
- **Alternative**: Use fallback directory `/data/local/tmp/diag_logs`

#### Process Execution Failures

- **Symptom**: "diag_mdlog not available" error
- **Solution**: Verify device supports QMDL logging
- **Check**: Ensure device is not in airplane mode
- **Qualcomm Check**: Verify device has Qualcomm chipset with QMDL support
- **OnePlus 9 Pro**: Snapdragon 888 supports QMDL logging

#### Service Binding Issues

- **Symptom**: "Service not ready" error
- **Solution**: Wait for service initialization to complete
- **Monitor**: Check setup status in log output

### Debug Information

- **Logcat**: Use `adb logcat | grep MODIV3` for detailed logs
- **Process Status**: Check `adb shell ps | grep diag_mdlog`
- **File Verification**: Verify files with `adb shell ls -la /sdcard/diag_logs/`
- **Chipset Info**: Check `adb shell cat /proc/cpuinfo | grep -i qualcomm`
- **QMDL Binary**: Verify `adb shell su -c "ls -la /vendor/bin/diag_mdlog"`

## Security Considerations

### Data Privacy

- QMDL data may contain sensitive network information
- Implement proper data encryption and access controls
- Follow data retention and disposal policies
- Ensure compliance with privacy regulations

### Root Access Security

- Root access provides system-level privileges
- Use only on devices you own or have explicit permission
- Implement proper authentication and authorization
- Monitor for unauthorized access attempts

### Network Security

- QMDL data collection may expose network vulnerabilities
- Use secure transmission protocols for data transfer
- Implement proper access controls for collected data
- Regular security audits and penetration testing

## Legal and Compliance

### Usage Restrictions

- **Educational Purpose**: Intended for research and educational use
- **Authorized Testing**: Only use on devices you own or have permission
- **Compliance**: Follow applicable laws and regulations
- **Responsibility**: Users are responsible for compliance with local laws

### Data Protection

- **GDPR Compliance**: Implement data protection measures
- **Data Minimization**: Collect only necessary data
- **User Consent**: Obtain proper consent for data collection
- **Data Rights**: Respect user data rights and requests

## Contributing

### Development Setup

1. Fork the repository
2. Create feature branch: `git checkout -b feature-name`
3. Make changes and test thoroughly
4. Submit pull request with detailed description

### Code Standards

- Follow Android development best practices
- Use meaningful variable and function names
- Add comprehensive comments and documentation
- Include unit tests for new features

### Testing Requirements

- Test on multiple Android versions
- Verify root access functionality
- Test error handling and edge cases
- Performance testing on target devices

## License and Copyright

### Copyright Notice

```
Copyright (c) 2025 MODIV3 Development Team

This software is provided "as is" without warranty of any kind.
Use at your own risk and in compliance with applicable laws.
```

### License Terms

- **Educational Use**: Free for educational and research purposes
- **Commercial Use**: Requires explicit permission and licensing
- **Modification**: Allowed with proper attribution
- **Distribution**: Subject to license terms and conditions

### Attribution Requirements

- Include original copyright notice
- Credit MODIV3 Development Team
- Maintain license and disclaimer notices
- Document any modifications made

## Support and Contact

### Documentation

- **API Documentation**: Available in source code comments
- **User Guide**: Comprehensive usage instructions
- **Developer Guide**: Technical implementation details
- **Troubleshooting**: Common issues and solutions

### Community Support

- **GitHub Issues**: Report bugs and request features
- **Discussion Forum**: Community support and questions
- **Wiki**: Additional documentation and tutorials
- **Email Support**: Direct support for critical issues

### Development Team

- **Lead Developer**: [Contact Information]
- **Security Analyst**: [Contact Information]
- **Network Engineer**: [Contact Information]
- **UI/UX Designer**: [Contact Information]

---

**Disclaimer**: This application is designed for educational and research purposes. Users are responsible for ensuring compliance with applicable laws and regulations. The development team assumes no liability for misuse or unauthorized access to network systems.
