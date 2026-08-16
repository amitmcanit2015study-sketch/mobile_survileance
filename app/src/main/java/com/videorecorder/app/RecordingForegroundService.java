package com.videorecorder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground Service for Camera and Microphone Recording
 * 
 * This service maintains a persistent notification while recording is active,
 * ensuring compliance with Android's foreground service requirements.
 * 
 * Why Foreground Service is required:
 * - Android 8.0+ restricts background services, requiring foreground services for long-running operations
 * - Camera and microphone access must be clearly indicated to the user via notification
 * - Android 14+ requires specific foregroundServiceType declarations (camera|microphone)
 * - Prevents the system from killing the service during recording
 */
public class RecordingForegroundService extends Service {
    
    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "recording_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Actions
    public static final String ACTION_START_RECORDING = "com.videorecorder.app.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.videorecorder.app.STOP_RECORDING";
    public static final String ACTION_OPEN_APP = "com.videorecorder.app.OPEN_APP";
    
    // Extras
    public static final String EXTRA_CAMERA_LENS = "camera_lens";
    public static final String EXTRA_VIDEO_QUALITY = "video_quality";
    public static final String EXTRA_AUDIO_ENABLED = "audio_enabled";
    public static final String EXTRA_GEO_ENABLED = "geo_enabled";
    public static final String EXTRA_GEO_LATITUDE = "geo_latitude";
    public static final String EXTRA_GEO_LONGITUDE = "geo_longitude";
    public static final String EXTRA_GEO_ALTITUDE = "geo_altitude";
    public static final String EXTRA_GEO_TIMESTAMP = "geo_timestamp";
    public static final String EXTRA_GEO_ADDRESS = "geo_address";
    
    // Recording state
    private static final AtomicBoolean isRecording = new AtomicBoolean(false);
    private RecordingController recordingController;
    private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        recordingController = new RecordingController(this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        
        if (ACTION_START_RECORDING.equals(action)) {
            handleStartRecording(intent);
        } else if (ACTION_STOP_RECORDING.equals(action)) {
            handleStopRecording();
        }
        
        return START_STICKY;
    }
    
    private void handleStartRecording(Intent intent) {
        if (isRecording.get()) {
            Toast.makeText(this, "Recording already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        acquireWakeLock();
        
        int cameraLens = intent.getIntExtra(EXTRA_CAMERA_LENS, 
                android.hardware.camera2.CameraMetadata.LENS_FACING_BACK);
        String videoQuality = intent.getStringExtra(EXTRA_VIDEO_QUALITY);
        boolean audioEnabled = intent.getBooleanExtra(EXTRA_AUDIO_ENABLED, true);
        GeoTagInfo geoTagInfo = null;
        if (intent.getBooleanExtra(EXTRA_GEO_ENABLED, false)) {
            geoTagInfo = new GeoTagInfo(
                intent.getDoubleExtra(EXTRA_GEO_LATITUDE, 0d),
                intent.getDoubleExtra(EXTRA_GEO_LONGITUDE, 0d),
                intent.getDoubleExtra(EXTRA_GEO_ALTITUDE, 0d),
                intent.getLongExtra(EXTRA_GEO_TIMESTAMP, System.currentTimeMillis()),
                intent.getStringExtra(EXTRA_GEO_ADDRESS));
        }
        
        // Start foreground service with notification
        Notification notification = createRecordingNotification();
        
        // For Android 14+, we need to specify the foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        // Initialize and start recording
        recordingController.initializeRecording(cameraLens, videoQuality, audioEnabled, geoTagInfo,
                new RecordingController.RecordingCallback() {
                    @Override
                    public void onRecordingStarted() {
                        isRecording.set(true);
                        updateRecordingNotification(true);
                    }
                    
                    @Override
                    public void onRecordingStopped(String filePath) {
                        isRecording.set(false);
                        stopForegroundService();
                    }
                    
                    @Override
                    public void onRecordingError(String error) {
                        isRecording.set(false);
                        ErrorHandler.handleUnknownError(RecordingForegroundService.this, 
                                new Exception(error), new RecordingController.RecordingCallback() {
                            @Override
                            public void onRecordingStarted() {}
                            
                            @Override
                            public void onRecordingStopped(String filePath) {}
                            
                            @Override
                            public void onRecordingError(String error) {
                                Toast.makeText(RecordingForegroundService.this, 
                                        "Recording error: " + error, Toast.LENGTH_LONG).show();
                            }
                            
                            @Override
                            public void onDurationUpdate(long durationMs) {}
                        });
                        stopForegroundService();
                    }
                    
                    @Override
                    public void onDurationUpdate(long durationMs) {
                        updateRecordingNotification(true, durationMs);
                    }
                });
    }
    
    private void handleStopRecording() {
        if (recordingController != null) {
            recordingController.stopRecording();
        }
    }
    
    private void stopForegroundService() {
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            int wakeFlags = PowerManager.PARTIAL_WAKE_LOCK;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wakeFlags |= PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
            }
            wakeLock = powerManager.newWakeLock(
                    wakeFlags | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Surviliance:RecordingWakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(30 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Video Recording",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows camera and microphone recording status");
            channel.enableVibration(false);
            channel.setSound(null, null);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createRecordingNotification() {
        Intent stopIntent = new Intent(this, RecordingForegroundService.class);
        stopIntent.setAction(ACTION_STOP_RECORDING);
        
        PendingIntent stopPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stopPendingIntent = PendingIntent.getService(
                    this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            stopPendingIntent = PendingIntent.getService(
                    this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent openAppPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            openAppPendingIntent = PendingIntent.getActivity(
                    this, 1, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            openAppPendingIntent = PendingIntent.getActivity(
                    this, 1, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Recording in Progress")
                .setContentText("Camera and microphone are active")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent);
        
        return builder.build();
    }
    
    private void updateRecordingNotification(boolean isRecording) {
        updateRecordingNotification(isRecording, 0);
    }
    
    private void updateRecordingNotification(boolean isRecording, long durationMs) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        
        String durationText = formatDuration(durationMs);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(isRecording ? "Recording in Progress" : "Recording Stopped")
                .setContentText(isRecording ? "Duration: " + durationText : "Saving video...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(isRecording)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        
        if (isRecording) {
            Intent stopIntent = new Intent(this, RecordingForegroundService.class);
            stopIntent.setAction(ACTION_STOP_RECORDING);
            
            PendingIntent stopPendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stopPendingIntent = PendingIntent.getService(
                        this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                stopPendingIntent = PendingIntent.getService(
                        this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent);
        }
        
        manager.notify(NOTIFICATION_ID, builder.build());
    }
    
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
    
    public static boolean isRecordingActive() {
        return isRecording.get();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recordingController != null) {
            recordingController.cleanup();
        }
        isRecording.set(false);
    }
}
