package com.videorecorder.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraX Video Recording Controller
 * 
 * This class manages CameraX video recording with audio capture.
 * 
 * Why CameraX is used instead of deprecated Camera2 API:
 * - CameraX provides backward compatibility across Android versions
 * - Simplifies camera operations with lifecycle awareness
 * - Handles device-specific quirks automatically
 * - Future-proof as Google continues to develop CameraX
 * - Built-in support for video recording with quality selection
 * 
 * Why ProcessCameraProvider is used:
 * - Manages camera lifecycle tied to application lifecycle
 * - Handles camera opening/closing automatically
 * - Prevents resource leaks and crashes
 * 
 * Service lifecycle considerations:
 * - We implement LifecycleOwner to provide lifecycle to CameraX
 * - This allows proper camera resource management in service context
 * - Lifecycle is manually controlled to prevent premature camera release
 * - Preview is skipped in service context since we don't have a surface
 */
public class RecordingController implements LifecycleOwner {
    
    private static final String TAG = "RecordingController";
    private static final int DEFAULT_FPS = 30;
    private static volatile Preview.SurfaceProvider previewSurfaceProvider;
    
    private final Context context;
    private final ExecutorService cameraExecutor;
    private final Handler mainHandler;
    private final LifecycleRegistry lifecycleRegistry;
    private final MediaStoreHelper mediaStoreHelper;
    
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private RecordingCallback callback;
    
    private boolean isAudioEnabled = true;
    private GeoTagInfo geoTagInfo;
    private File recordingFile;
    private int currentCameraLens = CameraSelector.LENS_FACING_BACK;
    private String currentQuality = "720p";
    private volatile boolean stopRequestedByUser;
    
    private long recordingStartTime;
    private final Runnable durationUpdateRunnable;
    
    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingStopped(String filePath);
        void onRecordingError(String error);
        void onDurationUpdate(long durationMs);
    }

    public static void setPreviewSurfaceProvider(Preview.SurfaceProvider surfaceProvider) {
        previewSurfaceProvider = surfaceProvider;
    }
    
    public RecordingController(Context context) {
        this.context = context.getApplicationContext();
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.mediaStoreHelper = new MediaStoreHelper(context);
        
        // Create manual lifecycle owner for service context
        this.lifecycleRegistry = new LifecycleRegistry(this);
        this.lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        
        // Duration update runnable (updates every second)
        this.durationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentRecording != null && callback != null) {
                    long duration = System.currentTimeMillis() - recordingStartTime;
                    callback.onDurationUpdate(duration);
                    mainHandler.postDelayed(this, 1000);
                }
            }
        };
    }
    
    /**
     * Initialize recording with specified parameters
     */
    public void initializeRecording(int cameraLens, String quality, boolean audioEnabled,
                                   GeoTagInfo geoTagInfo,
                                   RecordingCallback callback) {
        this.currentCameraLens = cameraLens;
        this.currentQuality = quality;
        this.isAudioEnabled = audioEnabled;
        this.geoTagInfo = geoTagInfo;
        this.callback = callback;
        
        // Set lifecycle to started state
        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
        
        // Check permissions first
        if (!hasRequiredPermissions()) {
            ErrorHandler.handlePermissionDenied(context, "Camera or Microphone");
            if (callback != null) {
                callback.onRecordingError("Missing camera or microphone permissions");
            }
            return;
        }
        
        // Initialize camera provider
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(context);
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                startCamera();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider initialization failed", e);
                ErrorHandler.handleUnknownError(context, e, callback);
            }
        }, ContextCompat.getMainExecutor(context));
    }
    
    /**
     * Start camera with video capture (no preview in service context)
     */
    private void startCamera() {
        if (cameraProvider == null) {
            if (callback != null) {
                callback.onRecordingError("Camera provider not initialized");
            }
            return;
        }
        
        try {
            // Unbind previous use cases
            cameraProvider.unbindAll();
            
            // Create camera selector based on lens facing
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(currentCameraLens)
                    .build();
            
            // Create video capture use case with quality selector
            QualitySelector qualitySelector = createQualitySelector();
            
            Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build();
            
            videoCapture = VideoCapture.withOutput(recorder);
            
            Preview preview = null;
            if (previewSurfaceProvider != null) {
                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewSurfaceProvider);
            }

            if (preview == null) {
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture);
            } else {
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
            }
            
            // Start recording immediately
            startRecording();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
            ErrorHandler.handleCameraUnavailable(context, callback);
        }
    }
    
    /**
     * Create quality selector based on user preference
     */
    private QualitySelector createQualitySelector() {
        Quality quality;
        
        switch (currentQuality) {
            case "1080p":
                quality = Quality.FHD;
                break;
            case "720p":
            default:
                quality = Quality.HD;
                break;
        }
        
        // Create fallback strategy for when preferred quality isn't available
        FallbackStrategy fallbackStrategy = FallbackStrategy.higherQualityOrLowerThan(quality);
        
        return QualitySelector.from(quality, fallbackStrategy);
    }
    
    /**
     * Start video recording
     */
    private void startRecording() {
        stopRequestedByUser = false;

        if (videoCapture == null) {
            ErrorHandler.handleCameraUnavailable(context, callback);
            return;
        }
        
        // Check storage space before starting recording
        if (!ErrorHandler.hasSufficientStorage(context, 100 * 1024 * 1024)) { // 100MB minimum
            ErrorHandler.handleStorageFull(context, callback);
            return;
        }
        
        // Create MediaStore output options
        String name = mediaStoreHelper.generateVideoFileName();
        recordingFile = new File(context.getCacheDir(), name);
        FileOutputOptions fileOutputOptions = new FileOutputOptions.Builder(recordingFile).build();
        
        // Start recording
        PendingRecording pendingRecording = videoCapture.getOutput()
            .prepareRecording(context, fileOutputOptions);
        if (isAudioEnabled) {
            pendingRecording = pendingRecording.withAudioEnabled();
        }

        currentRecording = pendingRecording.start(ContextCompat.getMainExecutor(context), videoRecordEvent -> {
                    
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        recordingStartTime = System.currentTimeMillis();
                        mainHandler.post(durationUpdateRunnable);
                        if (callback != null) {
                            callback.onRecordingStarted();
                        }
                        Log.d(TAG, "Recording started");
                        
                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                        
                        mainHandler.removeCallbacks(durationUpdateRunnable);

                        try {
                            if (!finalizeEvent.hasError()) {
                                long duration = System.currentTimeMillis() - recordingStartTime;

                                Uri videoUri = mediaStoreHelper.saveRecordedVideo(
                                        recordingFile, name, duration, geoTagInfo);
                                if (videoUri == null) {
                                    Log.e(TAG, "Unable to save recorded video to MediaStore");
                                    if (callback != null) {
                                        callback.onRecordingError("Unable to save recorded video");
                                    }
                                    return;
                                }

                                Log.d(TAG, "Recording saved to: " + videoUri);
                                if (callback != null) {
                                    callback.onRecordingStopped(videoUri.toString());
                                }
                                return;
                            }

                            int errorCode = finalizeEvent.getError();
                            Log.e(TAG, "Recording error code: " + errorCode + ", stopRequestedByUser=" + stopRequestedByUser);

                            if (stopRequestedByUser) {
                                Log.i(TAG, "User stopped recording; suppressing interruption error.");
                                return;
                            }

                            if (callback != null) {
                                callback.onRecordingError(
                                        "Recording interrupted while the device was locking or the camera was unavailable. Keep the screen awake during recording. Error code: " + errorCode);
                            }
                        } finally {
                            currentRecording = null;
                            stopRequestedByUser = false;
                        }
                    }
                });
    }
    
    /**
     * Stop current recording
     */
    public void stopRecording() {
        if (currentRecording == null) {
            return;
        }

        stopRequestedByUser = true;

        try {
            currentRecording.stop();
        } catch (IllegalStateException e) {
            Log.w(TAG, "Stop requested while recording state was not active", e);
            stopRequestedByUser = false;
        }
    }
    
    /**
     * Check if required permissions are granted
     */
    private boolean hasRequiredPermissions() {
        boolean hasCameraPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        
        boolean hasAudioPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        
        return hasCameraPermission && hasAudioPermission;
    }
    
    /**
     * Switch between front and back camera
     */
    public void switchCamera() {
        if (cameraProvider == null) return;
        
        currentCameraLens = (currentCameraLens == CameraSelector.LENS_FACING_BACK) 
                ? CameraSelector.LENS_FACING_FRONT 
                : CameraSelector.LENS_FACING_BACK;
        
        // Restart camera with new lens facing
        mainHandler.post(() -> {
            if (currentRecording != null) {
                // Stop current recording if active
                stopRecording();
            }
            startCamera();
        });
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        mainHandler.removeCallbacks(durationUpdateRunnable);
        
        if (currentRecording != null) {
            try {
                currentRecording.stop();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Cleanup stop requested while no active recording existed", e);
            } finally {
                currentRecording = null;
            }
        }
        
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        
        // Set lifecycle to destroyed
        lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        
        cameraExecutor.shutdown();
    }
    
    /**
     * Get current camera lens facing
     */
    public int getCurrentCameraLens() {
        return currentCameraLens;
    }
    
    /**
     * Get current quality setting
     */
    public String getCurrentQuality() {
        return currentQuality;
    }
    
    /**
     * Check if audio is enabled
     */
    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }
    
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }
}
