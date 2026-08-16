package com.videorecorder.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Centralized Error Handler for Video Recorder Application
 * 
 * This class handles various error scenarios and edge cases:
 * - Camera unavailable
 * - Microphone unavailable
 * - Permission denial
 * - Battery restrictions
 * - Storage full
 * - Camera disconnected
 * - Service termination
 * - Application process recreation
 * - Device rotation
 * - Screen locking/unlocking
 * - Incoming phone calls
 * - Audio focus changes
 * 
 * Why centralized error handling:
 * - Consistent error messaging across the app
 * - Centralized logging for debugging
 * - Easier maintenance and updates
 * - Reusable error recovery strategies
 */
public class ErrorHandler {
    
    private static final String TAG = "ErrorHandler";
    
    /**
     * Error types for categorization
     */
    public enum ErrorType {
        CAMERA_UNAVAILABLE,
        MICROPHONE_UNAVAILABLE,
        PERMISSION_DENIED,
        BATTERY_RESTRICTION,
        STORAGE_FULL,
        CAMERA_DISCONNECTED,
        SERVICE_TERMINATED,
        APP_PROCESS_RECREATED,
        DEVICE_ROTATION,
        SCREEN_LOCKED,
        INCOMING_CALL,
        AUDIO_FOCUS_LOST,
        UNKNOWN_ERROR
    }
    
    /**
     * Handle camera unavailable error
     */
    public static void handleCameraUnavailable(Context context, RecordingController.RecordingCallback callback) {
        Log.e(TAG, "Camera unavailable");
        if (callback != null) {
            callback.onRecordingError("Camera unavailable. Please check if another app is using the camera.");
        }
    }
    
    /**
     * Handle microphone unavailable error
     */
    public static void handleMicrophoneUnavailable(Context context, RecordingController.RecordingCallback callback) {
        Log.e(TAG, "Microphone unavailable");
        if (callback != null) {
            callback.onRecordingError("Microphone unavailable. Please check if another app is using the microphone.");
        }
    }
    
    /**
     * Handle permission denied error
     */
    public static void handlePermissionDenied(Context context, String permission) {
        Log.e(TAG, "Permission denied: " + permission);
        // This is handled in MainActivity's permission request flow
    }
    
    /**
     * Handle battery restriction error
     */
    public static void handleBatteryRestriction(Context context) {
        Log.w(TAG, "Battery optimization restricting recording");
        // Guide user to disable battery optimization for the app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open battery optimization settings", e);
            }
        }
    }
    
    /**
     * Handle storage full error
     */
    public static void handleStorageFull(Context context, RecordingController.RecordingCallback callback) {
        Log.e(TAG, "Storage full");
        if (callback != null) {
            callback.onRecordingError("Storage full. Please free up space and try again.");
        }
    }
    
    /**
     * Handle camera disconnected error
     */
    public static void handleCameraDisconnected(Context context, RecordingController.RecordingCallback callback) {
        Log.e(TAG, "Camera disconnected");
        if (callback != null) {
            callback.onRecordingError("Camera disconnected. Please check camera connection.");
        }
    }
    
    /**
     * Handle service termination
     */
    public static void handleServiceTermination(Context context) {
        Log.w(TAG, "Service terminated unexpectedly");
        // Attempt to restart the service if recording was in progress
        if (RecordingForegroundService.isRecordingActive()) {
            // This would require user intervention to restart recording
            Log.w(TAG, "Recording was in progress when service terminated");
        }
    }
    
    /**
     * Handle application process recreation
     */
    public static void handleAppProcessRecreation(Context context) {
        Log.i(TAG, "App process recreated");
        // Check if recording service is still running
        if (RecordingForegroundService.isRecordingActive()) {
            Log.i(TAG, "Recording service still active after process recreation");
        }
    }
    
    /**
     * Handle device rotation
     */
    public static void handleDeviceRotation(Context context) {
        Log.d(TAG, "Device rotation detected");
        // CameraX handles rotation automatically via use case configuration
        // No manual intervention needed
    }
    
    /**
     * Handle screen lock
     */
    public static void handleScreenLock(Context context) {
        Log.d(TAG, "Screen locked");
        // Foreground service continues recording in background
        // This is the expected behavior and doesn't require error handling
    }
    
    /**
     * Handle screen unlock
     */
    public static void handleScreenUnlock(Context context) {
        Log.d(TAG, "Screen unlocked");
        // Recording continues normally
        // MainActivity will check service status in onResume()
    }
    
    /**
     * Handle incoming phone call
     */
    public static void handleIncomingCall(Context context, RecordingController.RecordingCallback callback) {
        Log.w(TAG, "Incoming phone call detected");
        // Android system will handle audio focus
        // CameraX may need to handle interruption
        if (callback != null) {
            callback.onRecordingError("Recording interrupted by incoming call");
        }
    }
    
    /**
     * Handle audio focus loss
     */
    public static void handleAudioFocusLoss(Context context) {
        Log.w(TAG, "Audio focus lost");
        // CameraX handles audio focus changes automatically
        // Recording continues but audio may be affected
    }
    
    /**
     * Handle unknown errors
     */
    public static void handleUnknownError(Context context, Exception e, RecordingController.RecordingCallback callback) {
        Log.e(TAG, "Unknown error occurred", e);
        String message = (e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty())
                ? e.getMessage()
                : "Recording was interrupted";

        if (callback != null) {
            callback.onRecordingError("Recording interrupted. Keep the screen awake and avoid locking the phone during capture. Details: " + message);
        }
    }
    
    /**
     * Check if device is in power save mode
     */
    public static boolean isInPowerSaveMode(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager != null && powerManager.isPowerSaveMode();
        }
        return false;
    }
    
    /**
     * Check if storage space is sufficient
     */
    public static boolean hasSufficientStorage(Context context, long requiredBytes) {
        android.os.StatFs stat = new android.os.StatFs(
                android.os.Environment.getExternalStorageDirectory().getPath());
        long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        return availableBytes >= requiredBytes;
    }
    
    /**
     * Get appropriate error message for error type
     */
    public static String getErrorMessage(ErrorType errorType) {
        switch (errorType) {
            case CAMERA_UNAVAILABLE:
                return "Camera is currently unavailable";
            case MICROPHONE_UNAVAILABLE:
                return "Microphone is currently unavailable";
            case PERMISSION_DENIED:
                return "Required permissions were denied";
            case BATTERY_RESTRICTION:
                return "Battery optimization is restricting recording";
            case STORAGE_FULL:
                return "Insufficient storage space";
            case CAMERA_DISCONNECTED:
                return "Camera was disconnected";
            case SERVICE_TERMINATED:
                return "Recording service was terminated";
            case INCOMING_CALL:
                return "Recording was interrupted by a phone call";
            case AUDIO_FOCUS_LOST:
                return "Audio focus was lost";
            default:
                return "An unexpected error occurred";
        }
    }
}
