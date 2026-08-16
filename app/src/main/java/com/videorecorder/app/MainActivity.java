package com.videorecorder.app;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements VideoAdapter.Listener {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int POST_NOTIFICATIONS_PERMISSION_CODE = 1002;
    private static final int LOCATION_PERMISSION_CODE = 1003;
    private static final int TAB_VIDEO = 0;
    private static final int TAB_AUDIO = 1;

    private ImageButton btnCameraSwitch;
    private MaterialButton btnVideoTab;
    private MaterialButton btnAudioTab;
    private MaterialButton btnRecordToggle;
    private Spinner spinnerQuality;
    private TextView txtRecordingDuration;
    private TextView txtAudioStatus;
    private TextView txtGeoStatus;
    private TextView txtRecordingCount;
    private ImageButton btnAudioToggle;
    private ImageButton btnGeoToggle;
    private CheckBox checkboxSelectAll;
    private ImageButton btnDeleteSelected;
    private RecyclerView recordingsList;
    private TextView txtEmptyRecordings;
    private View controlPanel;
    private View recordingsHeader;
    private View recordingPreviewCard;
    private PreviewView cameraPreview;
    private TextView txtPreviewCamera;
    private View playerPanel;
    private VideoView videoPlayer;
    private TextView txtPlayerName;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaStoreHelper.VideoInfo> videoList = new ArrayList<>();
    private MediaStoreHelper mediaStoreHelper;
    private VideoAdapter videoAdapter;
    private boolean isRecording;
    private boolean isAudioEnabled = true;
    private boolean isGeoEnabled;
    private boolean isAudioTabSelected;
    private GeoTagInfo currentGeoTag;
    private int currentCameraLens = android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
    private String currentQuality = "720p";
    private long recordingStartTime;
    private MediaRecorder audioRecorder;
    private File activeAudioFile;
    private MediaPlayer activeAudioPlayer;

    private final Runnable durationTicker = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long seconds = (System.currentTimeMillis() - recordingStartTime) / 1000;
                txtRecordingDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
                handler.postDelayed(this, 1000);
            }
        }
    };

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mediaStoreHelper = new MediaStoreHelper(this);
        initializeViews();
        setupQualitySpinner();
        setupRecordingsList();
        updateTabSelection();
        checkPermissions();
        if (RecordingForegroundService.isRecordingActive()) {
            updateUIForRecordingState(true);
        }
    }

    private void initializeViews() {
        btnCameraSwitch = findViewById(R.id.btnCameraSwitch);
        btnVideoTab = findViewById(R.id.btnVideoTab);
        btnAudioTab = findViewById(R.id.btnAudioTab);
        btnRecordToggle = findViewById(R.id.btnRecordToggle);
        spinnerQuality = findViewById(R.id.spinnerQuality);
        txtRecordingDuration = findViewById(R.id.txtRecordingDuration);
        txtAudioStatus = findViewById(R.id.txtAudioStatus);
        txtGeoStatus = findViewById(R.id.txtGeoStatus);
        txtRecordingCount = findViewById(R.id.txtRecordingCount);
        btnAudioToggle = findViewById(R.id.btnAudioToggle);
        btnGeoToggle = findViewById(R.id.btnGeoToggle);
        checkboxSelectAll = findViewById(R.id.checkboxSelectAll);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        recordingsList = findViewById(R.id.recordingsList);
        txtEmptyRecordings = findViewById(R.id.txtEmptyRecordings);
        controlPanel = findViewById(R.id.controlPanel);
        recordingsHeader = findViewById(R.id.recordingsHeader);
        recordingPreviewCard = findViewById(R.id.recordingPreviewCard);
        cameraPreview = findViewById(R.id.cameraPreview);
        txtPreviewCamera = findViewById(R.id.txtPreviewCamera);
        playerPanel = findViewById(R.id.playerPanel);
        videoPlayer = findViewById(R.id.videoPlayer);
        txtPlayerName = findViewById(R.id.txtPlayerName);

        btnVideoTab.setOnClickListener(view -> switchTab(TAB_VIDEO));
        btnAudioTab.setOnClickListener(view -> switchTab(TAB_AUDIO));
        btnRecordToggle.setOnClickListener(view -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        btnCameraSwitch.setOnClickListener(view -> switchCamera());
        btnAudioToggle.setOnClickListener(view -> toggleAudio());
        btnGeoToggle.setOnClickListener(view -> toggleGeoTag());
        btnDeleteSelected.setOnClickListener(view -> confirmDeleteSelected());
        checkboxSelectAll.setOnCheckedChangeListener((button, checked) -> {
            if (videoAdapter != null && button.isPressed()) {
                videoAdapter.setAllSelected(checked);
                updateSelectionUi();
            }
        });
        findViewById(R.id.btnClosePlayer).setOnClickListener(view -> closePlayer());
        RecordingController.setPreviewSurfaceProvider(cameraPreview.getSurfaceProvider());
    }

    private void switchTab(int tab) {
        if (isRecording) {
            Toast.makeText(this, "Stop the current recording before switching tabs", Toast.LENGTH_SHORT).show();
            return;
        }
        isAudioTabSelected = tab == TAB_AUDIO;
        updateTabSelection();
        loadMediaList();
    }

    private void updateTabSelection() {
        btnVideoTab.setSelected(!isAudioTabSelected);
        btnAudioTab.setSelected(isAudioTabSelected);
        btnVideoTab.setBackgroundTintList(ContextCompat.getColorStateList(this,
                !isAudioTabSelected ? R.color.accent_color : R.color.surface));
        btnAudioTab.setBackgroundTintList(ContextCompat.getColorStateList(this,
                isAudioTabSelected ? R.color.accent_color : R.color.surface));
        if (!isAudioTabSelected) {
            btnVideoTab.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnAudioTab.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            btnVideoTab.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btnAudioTab.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
        btnCameraSwitch.setVisibility(isAudioTabSelected ? View.GONE : View.VISIBLE);
        spinnerQuality.setVisibility(isAudioTabSelected ? View.GONE : View.VISIBLE);
        txtGeoStatus.setVisibility(isAudioTabSelected ? View.GONE : View.VISIBLE);
        btnGeoToggle.setVisibility(isAudioTabSelected ? View.GONE : View.VISIBLE);
        txtRecordingDuration.setText("00:00");
        updateUIForRecordingState(false);
    }

    private void setupQualitySpinner() {
        List<String> qualityOptions = new ArrayList<>();
        qualityOptions.add("720p");
        qualityOptions.add("1080p");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, qualityOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuality.setAdapter(adapter);
        spinnerQuality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentQuality = qualityOptions.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                currentQuality = "720p";
            }
        });
    }

    private void setupRecordingsList() {
        recordingsList.setLayoutManager(new LinearLayoutManager(this));
        recordingsList.setHasFixedSize(true);
        loadMediaList();
    }

    private void loadMediaList() {
        videoList.clear();
        videoList.addAll(isAudioTabSelected ? mediaStoreHelper.getRecordedAudios() : mediaStoreHelper.getRecordedVideos());
        videoAdapter = new VideoAdapter(this, videoList, this);
        recordingsList.setAdapter(videoAdapter);
        boolean empty = videoList.isEmpty();
        recordingsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        txtEmptyRecordings.setVisibility(empty ? View.VISIBLE : View.GONE);
        String label = isAudioTabSelected ? "audio" : "video";
        txtRecordingCount.setText(empty ? "No " + label + "s yet" : videoList.size() + (videoList.size() == 1 ? " " + label : " " + label + "s"));
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        int selectedCount = videoAdapter == null ? 0 : videoAdapter.getSelectedCount();
        btnDeleteSelected.setEnabled(selectedCount > 0);
        btnDeleteSelected.setAlpha(selectedCount > 0 ? 1f : 0.45f);
        checkboxSelectAll.setOnCheckedChangeListener(null);
        checkboxSelectAll.setChecked(!videoList.isEmpty() && selectedCount == videoList.size());
        checkboxSelectAll.setOnCheckedChangeListener((button, checked) -> {
            if (videoAdapter != null && button.isPressed()) {
                videoAdapter.setAllSelected(checked);
                updateSelectionUi();
            }
        });
    }

    private void checkPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, POST_NOTIFICATIONS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_LONG).show();
                    showPermissionSettingsDialog();
                    break;
                }
            }
        } else if (requestCode == LOCATION_PERMISSION_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                enableGeoTag();
            } else {
                Toast.makeText(this, "Location permission is required for geo tags", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Camera and microphone permissions are required for video recording. Please enable them in app settings.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void switchCamera() {
        if (isRecording) {
            Toast.makeText(this, "Cannot switch camera while recording", Toast.LENGTH_SHORT).show();
            return;
        }
        currentCameraLens = currentCameraLens == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
                ? android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT
                : android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
        Toast.makeText(this, currentCameraLens == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
                ? "Back camera selected" : "Front camera selected", Toast.LENGTH_SHORT).show();
    }

    private void toggleAudio() {
        if (isRecording) {
            Toast.makeText(this, "Cannot toggle audio while recording", Toast.LENGTH_SHORT).show();
            return;
        }
        isAudioEnabled = !isAudioEnabled;
        txtAudioStatus.setText(isAudioEnabled ? "Audio: ON" : "Audio: OFF");
        txtAudioStatus.setTextColor(ContextCompat.getColor(this, isAudioEnabled ? R.color.success_color : R.color.error_color));
    }

    private void toggleGeoTag() {
        if (isRecording) {
            Toast.makeText(this, "Cannot change geo tagging while recording", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isGeoEnabled) {
            isGeoEnabled = false;
            currentGeoTag = null;
            updateGeoStatus();
            return;
        }
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        enableGeoTag();
    }

    private void enableGeoTag() {
        currentGeoTag = captureCurrentGeoTag();
        if (currentGeoTag == null) {
            isGeoEnabled = false;
            updateGeoStatus();
            Toast.makeText(this, "Turn on device location to use geo tags", Toast.LENGTH_LONG).show();
            return;
        }
        isGeoEnabled = true;
        updateGeoStatus();
    }

    private void updateGeoStatus() {
        if (isGeoEnabled && currentGeoTag != null) {
            txtGeoStatus.setText("Geo: ON\n" + currentGeoTag.getCoordinates());
            txtGeoStatus.setTextColor(ContextCompat.getColor(this, R.color.success_color));
        } else {
            txtGeoStatus.setText("Geo: OFF");
            txtGeoStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private GeoTagInfo captureCurrentGeoTag() {
        if (!hasLocationPermission()) {
            return null;
        }
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location bestLocation = null;
            String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            for (String provider : providers) {
                if (!locationManager.isProviderEnabled(provider)) {
                    continue;
                }
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null && (bestLocation == null || location.getTime() > bestLocation.getTime())) {
                    bestLocation = location;
                }
            }
            if (bestLocation == null) {
                return null;
            }

            String addressText = "";
            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(bestLocation.getLatitude(), bestLocation.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        addressText = addresses.get(0).getAddressLine(0);
                    }
                } catch (Exception ignored) {
                    // Coordinates remain available if reverse geocoding is offline.
                }
            }
            return new GeoTagInfo(bestLocation.getLatitude(), bestLocation.getLongitude(),
                    bestLocation.hasAltitude() ? bestLocation.getAltitude() : 0d,
                    System.currentTimeMillis(), addressText);
        } catch (SecurityException e) {
            return null;
        }
    }

    private void startRecording() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (isAudioTabSelected) {
            startAudioRecording();
            return;
        }
        if (!hasAllPermissions()) {
            checkPermissions();
            return;
        }
        Intent serviceIntent = new Intent(this, RecordingForegroundService.class);
        serviceIntent.setAction(RecordingForegroundService.ACTION_START_RECORDING);
        serviceIntent.putExtra(RecordingForegroundService.EXTRA_CAMERA_LENS, currentCameraLens);
        serviceIntent.putExtra(RecordingForegroundService.EXTRA_VIDEO_QUALITY, currentQuality);
        serviceIntent.putExtra(RecordingForegroundService.EXTRA_AUDIO_ENABLED, isAudioEnabled);
        serviceIntent.putExtra(RecordingForegroundService.EXTRA_GEO_ENABLED, isGeoEnabled && currentGeoTag != null);
        if (isGeoEnabled && currentGeoTag != null) {
            serviceIntent.putExtra(RecordingForegroundService.EXTRA_GEO_LATITUDE, currentGeoTag.getLatitude());
            serviceIntent.putExtra(RecordingForegroundService.EXTRA_GEO_LONGITUDE, currentGeoTag.getLongitude());
            serviceIntent.putExtra(RecordingForegroundService.EXTRA_GEO_ALTITUDE, currentGeoTag.getAltitude());
            serviceIntent.putExtra(RecordingForegroundService.EXTRA_GEO_TIMESTAMP, currentGeoTag.getTimestamp());
            serviceIntent.putExtra(RecordingForegroundService.EXTRA_GEO_ADDRESS, currentGeoTag.getAddress());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateUIForRecordingState(true);
    }

    private void stopRecording() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (isAudioTabSelected) {
            stopAudioRecording();
            return;
        }
        Intent serviceIntent = new Intent(this, RecordingForegroundService.class);
        serviceIntent.setAction(RecordingForegroundService.ACTION_STOP_RECORDING);
        startService(serviceIntent);
        updateUIForRecordingState(false);
        handler.postDelayed(this::loadMediaList, 1200);
    }

    private void startAudioRecording() {
        if (!hasAllPermissions()) {
            checkPermissions();
            return;
        }
        try {
            File musicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Surviliance");
            if (!musicDir.exists() && !musicDir.mkdirs()) {
                Toast.makeText(this, "Unable to create audio folder", Toast.LENGTH_SHORT).show();
                return;
            }
            activeAudioFile = new File(musicDir, mediaStoreHelper.generateAudioFileName());
            audioRecorder = new MediaRecorder();
            audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            audioRecorder.setOutputFile(activeAudioFile.getAbsolutePath());
            audioRecorder.prepare();
            audioRecorder.start();
            recordingStartTime = System.currentTimeMillis();
            updateUIForRecordingState(true);
        } catch (Exception e) {
            Toast.makeText(this, "Audio recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (audioRecorder != null) {
                try {
                    audioRecorder.release();
                } catch (Exception ignored) {
                }
                audioRecorder = null;
            }
        }
    }

    private void stopAudioRecording() {
        if (audioRecorder == null) {
            updateUIForRecordingState(false);
            return;
        }
        try {
            audioRecorder.stop();
        } catch (RuntimeException ignored) {
            Toast.makeText(this, "Audio recording was interrupted", Toast.LENGTH_SHORT).show();
        } finally {
            try {
                audioRecorder.release();
            } catch (Exception ignored) {
            }
            audioRecorder = null;
        }
        if (activeAudioFile != null && activeAudioFile.exists()) {
            saveAudioRecording(activeAudioFile);
        }
        activeAudioFile = null;
        updateUIForRecordingState(false);
        handler.postDelayed(this::loadMediaList, 500);
    }

    private void saveAudioRecording(File sourceFile) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, sourceFile.getName());
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Surviliance");
            values.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            Uri audioUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (audioUri == null) {
                return;
            }
            try (FileInputStream input = new FileInputStream(sourceFile);
                 FileOutputStream output = new FileOutputStream(getContentResolver().openFileDescriptor(audioUri, "w").getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            ContentValues finalizeValues = new ContentValues();
            finalizeValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
            getContentResolver().update(audioUri, finalizeValues, null, null);
            sourceFile.delete();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save audio clip", Toast.LENGTH_SHORT).show();
            if (sourceFile != null && sourceFile.exists()) {
                sourceFile.delete();
            }
        }
    }

    private void updateUIForRecordingState(boolean recording) {
        isRecording = recording;
        btnRecordToggle.setEnabled(true);
        btnRecordToggle.setText(recording ? "STOP" : (isAudioTabSelected ? "START AUDIO" : "START VIDEO"));
        btnRecordToggle.setBackgroundTintList(ContextCompat.getColorStateList(this,
                recording ? R.color.stop_color : R.color.start_color));
        btnCameraSwitch.setEnabled(!recording && !isAudioTabSelected);
        spinnerQuality.setEnabled(!recording && !isAudioTabSelected);
        btnAudioToggle.setEnabled(!recording);
        btnGeoToggle.setEnabled(!recording && !isAudioTabSelected);
        recordingPreviewCard.setVisibility(recording && !isAudioTabSelected ? View.VISIBLE : View.GONE);
        if (cameraPreview != null) {
            cameraPreview.setVisibility(View.GONE);
        }
        if (recording && !isAudioTabSelected) {
            txtPreviewCamera.setText(currentCameraLens == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
                    ? "Rear camera active" : "Front camera active");
            recordingStartTime = System.currentTimeMillis();
            txtRecordingDuration.setText("00:00");
            txtRecordingDuration.setVisibility(View.VISIBLE);
            handler.removeCallbacks(durationTicker);
            handler.post(durationTicker);
        } else {
            handler.removeCallbacks(durationTicker);
            txtRecordingDuration.setVisibility(View.VISIBLE);
            txtRecordingDuration.setText("00:00");
            if (!recording) {
                txtRecordingDuration.setText("00:00");
            }
        }
    }

    private boolean hasAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onPlay(MediaStoreHelper.VideoInfo video) {
        if (isAudioTabSelected) {
            playAudioClip(video);
            return;
        }
        txtPlayerName.setText(video.getDisplayName());
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoPlayer);
        videoPlayer.setMediaController(mediaController);
        videoPlayer.setVideoURI(video.getUri());
        recordingsHeader.setVisibility(View.GONE);
        recordingsList.setVisibility(View.GONE);
        txtEmptyRecordings.setVisibility(View.GONE);
        controlPanel.setVisibility(View.GONE);
        playerPanel.setVisibility(View.VISIBLE);
        videoPlayer.start();
    }

    private void playAudioClip(MediaStoreHelper.VideoInfo audio) {
        try {
            if (activeAudioPlayer != null) {
                activeAudioPlayer.release();
            }
            activeAudioPlayer = new MediaPlayer();
            activeAudioPlayer.setDataSource(this, audio.getUri());
            activeAudioPlayer.prepare();
            activeAudioPlayer.start();
            Toast.makeText(this, "Playing " + audio.getDisplayName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to play audio clip", Toast.LENGTH_SHORT).show();
        }
    }

    private void closePlayer() {
        if (videoPlayer != null) {
            videoPlayer.stopPlayback();
        }
        if (activeAudioPlayer != null) {
            activeAudioPlayer.release();
            activeAudioPlayer = null;
        }
        playerPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        recordingsHeader.setVisibility(View.VISIBLE);
        recordingsList.setVisibility(videoList.isEmpty() ? View.GONE : View.VISIBLE);
        txtEmptyRecordings.setVisibility(videoList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDelete(MediaStoreHelper.VideoInfo video) {
        new AlertDialog.Builder(this)
                .setTitle("Delete recording?")
                .setMessage(video.getDisplayName())
                .setPositiveButton("Delete", (dialog, which) -> {
                    mediaStoreHelper.deleteVideo(video.getUri());
                    loadMediaList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onSelectionChanged(MediaStoreHelper.VideoInfo video, boolean selected) {
        updateSelectionUi();
    }

    private void confirmDeleteSelected() {
        if (videoAdapter == null || videoAdapter.getSelectedCount() == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete selected recordings?")
                .setMessage(videoAdapter.getSelectedCount() + " file(s) will be deleted.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelected() {
        Set<Long> selectedIds = videoAdapter.getSelectedIds();
        for (MediaStoreHelper.VideoInfo video : new ArrayList<>(videoList)) {
            if (selectedIds.contains(video.getId())) {
                mediaStoreHelper.deleteVideo(video.getUri());
            }
        }
        loadMediaList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaStoreHelper != null && playerPanel != null && playerPanel.getVisibility() != View.VISIBLE) {
            loadMediaList();
        }
        if (RecordingForegroundService.isRecordingActive()) {
            updateUIForRecordingState(true);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(durationTicker);
        if (audioRecorder != null) {
            try {
                audioRecorder.release();
            } catch (Exception ignored) {
            }
        }
        if (activeAudioPlayer != null) {
            activeAudioPlayer.release();
        }
        RecordingController.setPreviewSurfaceProvider(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (playerPanel != null && playerPanel.getVisibility() == View.VISIBLE) {
            closePlayer();
            return;
        }
        super.onBackPressed();
    }
}
