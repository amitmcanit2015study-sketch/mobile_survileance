# Deployment Instructions

## Prerequisites
- Android Studio is installed
- Mobile device is connected (detected: adb-ZN5223TLT8-OjRVbg._adb-tls-connect._tcp)
- Android SDK is available at: C:\Users\Administrator\AppData\Local\Android\Sdk

## Method 1: Using Android Studio (Recommended)

1. **Open Project in Android Studio**
   - Launch Android Studio
   - File → Open → Navigate to `D:\_test\_mobile_survileance`
   - Wait for Gradle sync to complete

2. **Select Connected Device**
   - In the toolbar, you should see your connected device
   - Select the device from the dropdown menu

3. **Build and Run**
   - Click the green "Run" button (▶) or press Shift+F10
   - Android Studio will build the APK and install it on your device
   - The app will launch automatically

## Method 2: Using Command Line (if Android SDK is configured)

If you have Android SDK command-line tools configured in your PATH:

```bash
# Navigate to project directory
cd D:\_test\_mobile_survileance

# Build the debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.videorecorder.app/.MainActivity
```

## Method 3: Manual APK Installation

1. **Build APK using Android Studio**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Wait for build completion
   - Click "locate" in the notification to find the APK

2. **Transfer to Device**
   - Copy the APK file to your device via USB or cloud storage
   - Enable "Install from unknown sources" in device settings
   - Open the APK file on your device to install

## Troubleshooting

### Device Not Detected
- Ensure USB debugging is enabled on your device
- Check USB cable connection
- Try different USB port
- Restart ADB: `adb kill-server && adb start-server`

### Build Errors
- Ensure JDK 8 or later is installed
- Check Android SDK version compatibility
- Clean project: Build → Clean Project
- Rebuild project: Build → Rebuild Project

### Permission Issues
- The app will request camera and microphone permissions on first launch
- Grant these permissions for full functionality
- Enable notifications for Android 13+ devices

## First Launch Setup

1. **Grant Permissions**
   - Camera permission
   - Microphone permission
   - Storage permission (if prompted)
   - Notification permission (Android 13+)

2. **Test Recording**
   - Select camera (front/back)
   - Choose video quality
   - Enable/disable audio
   - Start recording
   - Test with screen locked

3. **Verify Storage**
   - Check if videos appear in gallery
   - Navigate to Movies/VideoRecorder folder
   - Test playback, deletion, and sharing

## Connected Device Information
- Device ID: adb-ZN5223TLT8-OjRVbg._adb-tls-connect._tcp
- Status: Connected
- Ready for deployment

## Next Steps
After successful deployment:
1. Test core recording functionality
2. Test background recording (screen lock)
3. Test video list and playback
4. Test error scenarios
5. Verify notification behavior
