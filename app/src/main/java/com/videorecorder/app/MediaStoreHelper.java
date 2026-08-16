package com.videorecorder.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.content.ContentUris;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.ParcelFileDescriptor;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MediaStore Helper for Video Storage
 * 
 * This class handles all MediaStore operations for video storage.
 * 
 * Why MediaStore is used instead of direct file access:
 * - Required by Android 10+ scoped storage model
 * - Ensures videos appear in gallery apps automatically
 * - Provides consistent metadata management
 * - Required for proper Android compatibility
 * - Handles storage permission changes across Android versions
 * 
 * Storage location considerations:
 * - Uses Environment.DIRECTORY_MOVIES for user-facing content
 * - Creates app-specific subdirectory for organization
 * - Follows Android storage best practices
 */
public class MediaStoreHelper {
    
    private static final String TAG = "MediaStoreHelper";
    private static final String APP_DIRECTORY = "VideoRecorder";
    
    private final Context context;
    private final ContentResolver contentResolver;
    private final SharedPreferences geoPreferences;
    
    public MediaStoreHelper(Context context) {
        this.context = context.getApplicationContext();
        this.contentResolver = context.getContentResolver();
        this.geoPreferences = this.context.getSharedPreferences("video_geo_tags", Context.MODE_PRIVATE);
    }
    
    /**
     * Create video content values for MediaStore insertion
     */
    public ContentValues createVideoContentValues(String filename) {
        ContentValues values = new ContentValues();
        
        values.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, 
                Environment.DIRECTORY_MOVIES + "/" + APP_DIRECTORY);
        
        // Set date taken to current time
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
        
        // Mark as pending while being written
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        
        return values;
    }
    
    /**
     * Update video content after recording is complete
     */
    public void finalizeVideo(Uri videoUri, long durationMs) {
        finalizeVideo(videoUri, durationMs, null);
    }

    public void finalizeVideo(Uri videoUri, long durationMs, GeoTagInfo geoTagInfo) {
        try {
            ContentValues values = new ContentValues();
            long finalizedAt = System.currentTimeMillis();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            values.put(MediaStore.Video.Media.DURATION, durationMs);
            values.put(MediaStore.Video.Media.DATE_TAKEN, finalizedAt);
            values.put(MediaStore.Video.Media.DATE_ADDED, finalizedAt / 1000);
            values.put(MediaStore.Video.Media.DATE_MODIFIED, finalizedAt / 1000);
            
            contentResolver.update(videoUri, values, null, null);
            if (geoTagInfo != null) {
                saveGeoTag(videoUri, geoTagInfo);
            }
            Log.d(TAG, "Video finalized: " + videoUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to finalize video", e);
        }
    }

    public Uri saveRecordedVideo(File sourceFile, String filename, long durationMs, GeoTagInfo geoTagInfo) {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() <= 0) {
            Log.e(TAG, "Recorded video source file missing or empty");
            return null;
        }

        Uri videoUri = null;
        try {
            long finalizedAt = System.currentTimeMillis();
            ContentValues values = createVideoContentValues(filename);
            values.put(MediaStore.Video.Media.DURATION, durationMs);
            values.put(MediaStore.Video.Media.DATE_TAKEN, finalizedAt);
            values.put(MediaStore.Video.Media.DATE_ADDED, finalizedAt / 1000);
            values.put(MediaStore.Video.Media.DATE_MODIFIED, finalizedAt / 1000);

            videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (videoUri == null) {
                Log.e(TAG, "MediaStore insert returned null for video file");
                return null;
            }

            try (ParcelFileDescriptor descriptor = contentResolver.openFileDescriptor(videoUri, "w")) {
                if (descriptor == null) {
                    Log.e(TAG, "Could not open MediaStore output stream for video");
                    contentResolver.delete(videoUri, null, null);
                    return null;
                }

                try (FileInputStream input = new FileInputStream(sourceFile);
                     FileOutputStream output = new FileOutputStream(descriptor.getFileDescriptor())) {
                    byte[] copyBuffer = new byte[8192];
                    int read;
                    while ((read = input.read(copyBuffer)) != -1) {
                        output.write(copyBuffer, 0, read);
                    }
                    output.flush();
                }
            }

            ContentValues finalizedValues = new ContentValues();
            finalizedValues.put(MediaStore.Video.Media.IS_PENDING, 0);
            finalizedValues.put(MediaStore.Video.Media.DURATION, durationMs);
            finalizedValues.put(MediaStore.Video.Media.DATE_TAKEN, finalizedAt);
            finalizedValues.put(MediaStore.Video.Media.DATE_ADDED, finalizedAt / 1000);
            finalizedValues.put(MediaStore.Video.Media.DATE_MODIFIED, finalizedAt / 1000);
            contentResolver.update(videoUri, finalizedValues, null, null);

            if (geoTagInfo != null) {
                saveGeoTag(videoUri, geoTagInfo);
            }

            Log.d(TAG, "Saved recorded video directly to MediaStore: " + videoUri);
            return videoUri;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save recorded video directly to MediaStore", e);
            if (videoUri != null) {
                contentResolver.delete(videoUri, null, null);
            }
            return null;
        } finally {
            if (sourceFile != null && sourceFile.exists()) {
                sourceFile.delete();
            }
        }
    }

    
    /**
     * Get list of all recorded videos from MediaStore
     */
    public List<VideoInfo> getRecordedVideos() {
        List<VideoInfo> videoList = new ArrayList<>();
        
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATA
        };
        
        String selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = {"%" + APP_DIRECTORY + "%"};
        String sortOrder = MediaStore.Video.Media.DATE_TAKEN + " DESC";
        
        try (Cursor cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder)) {
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    VideoInfo video = new VideoInfo();
                    video.setId(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)));
                    video.setDisplayName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)));
                    video.setSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)));
                    video.setDuration(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)));
                    video.setDateTaken(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)));
                    video.setDataPath(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)));
                    video.setUri(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.getId()));
                    video.setGeoTag(getGeoTag(video.getUri()));
                    
                    videoList.add(video);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query videos", e);
        }
        
        return videoList;
    }

    public List<VideoInfo> getRecordedAudios() {
        List<VideoInfo> audioList = new ArrayList<>();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
        };

        String selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = {"%/Surviliance%"};
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    VideoInfo audio = new VideoInfo();
                    audio.setId(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)));
                    audio.setDisplayName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)));
                    audio.setSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)));
                    audio.setDuration(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)));
                    audio.setDateTaken(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) * 1000L);
                    audio.setDataPath(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)));
                    audio.setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio.getId()));
                    audioList.add(audio);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query audios", e);
        }

        return audioList;
    }
    
    /**
     * Delete a video from MediaStore
     */
    public boolean deleteVideo(Uri videoUri) {
        try {
            int rowsDeleted = contentResolver.delete(videoUri, null, null);
            if (rowsDeleted > 0) {
                String key = videoUri.toString();
                geoPreferences.edit()
                        .remove(key + ".coordinates")
                        .remove(key + ".latitude")
                        .remove(key + ".longitude")
                        .remove(key + ".altitude")
                        .remove(key + ".timestamp")
                        .remove(key + ".address")
                        .apply();
                Log.d(TAG, "Video deleted: " + videoUri);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete video", e);
        }
        return false;
    }

    private void saveGeoTag(Uri videoUri, GeoTagInfo geoTagInfo) {
        String key = videoUri.toString();
        geoPreferences.edit()
                .putString(key + ".coordinates", geoTagInfo.getCoordinates())
                .putFloat(key + ".latitude", (float) geoTagInfo.getLatitude())
                .putFloat(key + ".longitude", (float) geoTagInfo.getLongitude())
                .putFloat(key + ".altitude", (float) geoTagInfo.getAltitude())
                .putLong(key + ".timestamp", geoTagInfo.getTimestamp())
                .putString(key + ".address", geoTagInfo.getAddress())
                .apply();
    }

    private GeoTagInfo getGeoTag(Uri videoUri) {
        String key = videoUri.toString();
        if (!geoPreferences.contains(key + ".latitude")) {
            return null;
        }
        return new GeoTagInfo(
                geoPreferences.getFloat(key + ".latitude", 0f),
                geoPreferences.getFloat(key + ".longitude", 0f),
                geoPreferences.getFloat(key + ".altitude", 0f),
                geoPreferences.getLong(key + ".timestamp", 0L),
                geoPreferences.getString(key + ".address", ""));
    }
    
    /**
     * Get video URI by filename
     */
    public Uri getVideoUriByFilename(String filename) {
        String[] projection = {MediaStore.Video.Media._ID};
        String selection = MediaStore.Video.Media.DISPLAY_NAME + " = ?";
        String[] selectionArgs = {filename};
        
        try (Cursor cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {
            
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get video URI", e);
        }
        
        return null;
    }
    
    /**
     * Generate unique video filename
     */
    public String generateVideoFileName() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        return "VID_" + timestamp + ".mp4";
    }

    public String generateAudioFileName() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        return "AUD_" + timestamp + ".m4a";
    }
    
    /**
     * Check if storage is available
     */
    public boolean isStorageAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ scoped storage is always available for app directories
            return true;
        } else {
            // Check external storage state for older versions
            String state = Environment.getExternalStorageState();
            return Environment.MEDIA_MOUNTED.equals(state);
        }
    }
    
    /**
     * Get app-specific video directory
     */
    public File getAppVideoDirectory() {
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File appDir = new File(moviesDir, APP_DIRECTORY);
        
        if (!appDir.exists()) {
            if (appDir.mkdirs()) {
                Log.d(TAG, "Created app video directory: " + appDir.getAbsolutePath());
            }
        }
        
        return appDir;
    }
    
    /**
     * Video information container class
     */
    public static class VideoInfo {
        private long id;
        private String displayName;
        private long size;
        private long duration;
        private long dateTaken;
        private String dataPath;
        private Uri uri;
        private GeoTagInfo geoTag;
        
        public long getId() {
            return id;
        }
        
        public void setId(long id) {
            this.id = id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
        
        public long getDuration() {
            return duration;
        }
        
        public void setDuration(long duration) {
            this.duration = duration;
        }
        
        public long getDateTaken() {
            return dateTaken;
        }
        
        public void setDateTaken(long dateTaken) {
            this.dateTaken = dateTaken;
        }
        
        public String getDataPath() {
            return dataPath;
        }
        
        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }
        
        public Uri getUri() {
            return uri;
        }
        
        public void setUri(Uri uri) {
            this.uri = uri;
        }

        public GeoTagInfo getGeoTag() {
            return geoTag;
        }

        public void setGeoTag(GeoTagInfo geoTag) {
            this.geoTag = geoTag;
        }
        
        /**
         * Get formatted file size
         */
        public String getFormattedSize() {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
            } else {
                return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
            }
        }
        
        /**
         * Get formatted duration
         */
        public String getFormattedDuration() {
            long seconds = duration / 1000;
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds);
        }
        
        /**
         * Get formatted date
         */
        public String getFormattedDate() {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            return dateFormat.format(new Date(dateTaken));
        }
    }
}
