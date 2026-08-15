# Video Recorder Android Application

A production-ready Android application for recording video with audio using the device camera, including support for recording while the device screen is locked, while respecting Android privacy and foreground-service restrictions.

## Technology Stack

- **Language**: Java
- **Build System**: Gradle
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Libraries**: 
  - AndroidX
  - CameraX 1.3.1
  - Material Design
  - CardView

## Features

### Core Functionality
- ✅ Camera recording with front/rear camera selection
- ✅ Video recording with microphone audio
- ✅ Configurable video quality (720p, 1080p)
- ✅ Recording duration display
- ✅ Storage in MediaStore with proper metadata
- ✅ Meaningful filenames (VID_YYYYMMDD_HHMMSS.mp4)
- ✅ Graceful permission failure handling

### Background Recording
- ✅ Recording continues when screen is locked
- ✅ Foreground service with persistent notification
- ✅ Clear notification indicating recording status
- ✅ Notification actions: Stop recording, Open app
- ✅ Proper foreground service type declarations (camera|microphone)

### User Interface
- ✅ Camera status indicator
- ✅ Start/Stop recording buttons
- ✅ Camera switch button
- ✅ Recording timer
- ✅ Video quality selector
- ✅ Audio on/off indicator
- ✅ Video list with playback options
- ✅ Delete video functionality
- ✅ Share video functionality

### Storage & Privacy
- ✅ MediaStore integration for proper gallery integration
- ✅ App-specific directory (Movies/VideoRecorder)
- ✅ Metadata (creation date, duration)
- ✅ Proper video finalization
- ✅ Never secretly records
- ✅ Never bypasses Android permission dialogs
- ✅ Never hides foreground service notification
- ✅ Never uses accessibility APIs for unauthorized access

### Error Handling
- ✅ Camera unavailable handling
- ✅ Microphone unavailable handling
- ✅ Permission denial handling
- ✅ Battery restriction handling
- ✅ Storage full handling
- ✅ Camera disconnected handling
- ✅ Service termination handling
- ✅ Application process recreation handling
- ✅ Device rotation handling
- ✅ Screen locking/unlocking handling
- ✅ Incoming phone call handling
- ✅ Audio focus change handling

## Architecture

```
MainActivity 
→ RecordingController (Camera logic)
→ RecordingForegroundService (Background recording)
→ CameraX (Camera operations)
→ MediaStoreHelper (Storage operations)
→ ErrorHandler (Error management)
```

## Key Implementation Decisions

### Why CameraX?
- **Backward Compatibility**: Works across Android versions automatically
- **Lifecycle Awareness**: Handles camera lifecycle automatically
- **Device Compatibility**: Handles device-specific quirks
- **Future-Proof**: Actively developed by Google
- **Built-in Recording**: Native video recording support with quality selection

### Why Foreground Service?
- **Android 8.0+ Restrictions**: Required for background operations
- **User Visibility**: Persistent notification shows recording status
- **System Priority**: Prevents system from killing the service
- **Privacy Compliance**: Clearly indicates camera/microphone usage

### Why MediaStore?
- **Scoped Storage**: Required by Android 10+
- **Gallery Integration**: Videos appear in gallery apps automatically
- **Metadata Management**: Consistent metadata handling
- **Storage Permission Changes**: Handles permission changes across Android versions

### Why Manual LifecycleOwner in Service?
- **Service Context**: Services don't have natural lifecycle
- **CameraX Requirement**: CameraX needs lifecycle for resource management
- **Manual Control**: Prevents premature camera release
- **Proper Cleanup**: Ensures resources are released correctly

## Permissions

The app requires the following permissions:

### Core Permissions
- `CAMERA` - Required for camera access
- `RECORD_AUDIO` - Required for microphone access
- `WRITE_EXTERNAL_STORAGE` (maxSdkVersion="32") - Legacy storage permission
- `READ_EXTERNAL_STORAGE` (maxSdkVersion="32") - Legacy storage permission

### Android 13+ Permissions
- `READ_MEDIA_VIDEO` - Required for video access on Android 13+
- `READ_MEDIA_IMAGES` - Required for image access on Android 13+
- `POST_NOTIFICATIONS` - Required for notifications on Android 13+

### Foreground Service Permissions
- `FOREGROUND_SERVICE` - Required for foreground services
- `FOREGROUND_SERVICE_CAMERA` - Required for camera foreground service (Android 14+)
- `FOREGROUND_SERVICE_MICROPHONE` - Required for microphone foreground service (Android 14+)

### Additional Permissions
- `WAKE_LOCK` - For screen management during recording
- `READ_PHONE_STATE` - For handling incoming calls

## Building the Project

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 8 or later
- Android SDK 34

### Build Steps
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Build the project: Build → Make Project
4. Run on device/emulator: Run → Run 'app'

## Privacy & Security Compliance

### Google Play Policy Compliance
- ✅ No secret recording
- ✅ No permission bypassing
- ✅ No hidden notifications
- ✅ No accessibility service abuse
- ✅ Clear user consent
- ✅ Transparent recording status

### Android Best Practices
- ✅ Proper permission handling
- ✅ Scoped storage compliance
- ✅ Foreground service best practices
- ✅ Privacy by design
- ✅ User control over recording

## File Structure

```
app/
├── src/main/
│   ├── java/com/videorecorder/app/
│   │   ├── MainActivity.java              # Main UI activity
│   │   ├── VideoListActivity.java        # Video list and playback
│   │   ├── VideoAdapter.java             # Video list adapter
│   │   ├── RecordingForegroundService.java # Background recording service
│   │   ├── RecordingController.java      # CameraX recording logic
│   │   ├── MediaStoreHelper.java         # MediaStore operations
│   │   ├── ErrorHandler.java             # Centralized error handling
│   │   └── CallReceiver.java            # Incoming call handling
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml         # Main activity layout
│   │   │   ├── activity_video_list.xml   # Video list layout
│   │   │   └── item_video.xml           # Video list item layout
│   │   ├── values/
│   │   │   ├── strings.xml               # String resources
│   │   │   ├── colors.xml                # Color resources
│   │   │   └── themes.xml                # Theme resources
│   │   ├── values-night/
│   │   │   └── themes.xml                # Dark theme
│   │   ├── drawable/
│   │   │   ├── circle_button_background.xml
│   │   │   ├── start_button_background.xml
│   │   │   ├── stop_button_background.xml
│   │   │   └── view_videos_button_background.xml
│   │   └── xml/
│   │       ├── backup_rules.xml          # Backup rules
│   │       ├── data_extraction_rules.xml # Data extraction rules
│   │       └── file_paths.xml            # File provider paths
│   └── AndroidManifest.xml               # App manifest
├── build.gradle                          # App-level build config
├── proguard-rules.pro                    # ProGuard rules
settings.gradle                           # Project settings
build.gradle                              # Project-level build config
gradle.properties                         # Gradle properties
```

## Testing

### Manual Testing Checklist
- [ ] Camera recording with front camera
- [ ] Camera recording with rear camera
- [ ] Recording with audio enabled
- [ ] Recording with audio disabled
- [ ] Quality selection (720p, 1080p)
- [ ] Recording while screen is locked
- [ ] Notification actions (stop, open app)
- [ ] Video list display
- [ ] Video playback
- [ ] Video deletion
- [ ] Video sharing
- [ ] Permission denial handling
- [ ] Incoming call interruption
- [ ] Storage full scenario
- [ ] Battery optimization scenario

### Automated Testing
Add automated tests in `app/src/test/` and `app/src/androidTest/` directories.

## Troubleshooting

### Common Issues

**Camera not available**
- Check if another app is using the camera
- Ensure camera permissions are granted
- Restart the device if camera hardware is not responding

**Recording stops unexpectedly**
- Check battery optimization settings
- Ensure sufficient storage space
- Check for incoming calls interrupting recording

**Videos not appearing in gallery**
- Wait for MediaStore to index the files
- Check if the app has storage permissions
- Look in Movies/VideoRecorder directory

**Foreground service not starting**
- Ensure foreground service permissions are granted
- Check if the app is allowed to run in background
- Verify notification permissions on Android 13+

## Future Enhancements

- [ ] Camera preview in MainActivity
- [ ] Video editing capabilities
- [ ] Cloud backup integration
- [ ] Advanced video settings (bitrate, frame rate)
- [ ] Video compression options
- [ ] Recording scheduling
- [ ] Time-lapse recording
- [ ] Slow-motion recording
- [ ] Video stabilization
- [ ] Multiple language support

## License

This project is provided as-is for educational and development purposes.

## Credits

Built with:
- [CameraX](https://developer.android.com/training/camerax) - Camera library
- [Material Components](https://material.io/develop/android) - UI components
- [AndroidX](https://developer.android.com/jetpack/androidx) - Android libraries

## Support

For issues and questions, please refer to the Android documentation:
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [Foreground Services](https://developer.android.com/guide/components/foreground-services)
- [MediaStore](https://developer.android.com/training/data-storage)
- [Runtime Permissions](https://developer.android.com/training/permissions)
