package com.videorecorder.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Video List Activity
 * 
 * This activity displays a list of recorded videos and provides options for:
 * - Playing videos
 * - Deleting videos
 * - Sharing videos
 * 
 * Why separate activity:
 * - Clean separation of concerns between recording and playback
 * - Follows Android navigation patterns
 * - Allows for focused video management interface
 */
public class VideoListActivity extends AppCompatActivity {
    
    private static final String TAG = "VideoListActivity";
    private static final int REQUEST_CODE_PLAY_VIDEO = 1001;
    
    private ListView videoListView;
    private VideoAdapter videoAdapter;
    private MediaStoreHelper mediaStoreHelper;
    private List<MediaStoreHelper.VideoInfo> videoList;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);
        
        mediaStoreHelper = new MediaStoreHelper(this);
        initializeViews();
        loadVideos();
    }
    
    /**
     * Initialize UI components
     */
    private void initializeViews() {
        videoListView = findViewById(R.id.videoListView);
        
        videoListView.setOnItemClickListener((parent, view, position, id) -> {
            MediaStoreHelper.VideoInfo video = videoList.get(position);
            showVideoOptionsDialog(video);
        });
        
        videoListView.setOnItemLongClickListener((parent, view, position, id) -> {
            MediaStoreHelper.VideoInfo video = videoList.get(position);
            showDeleteConfirmationDialog(video);
            return true;
        });
    }
    
    /**
     * Load videos from MediaStore
     */
    private void loadVideos() {
        videoList = mediaStoreHelper.getRecordedVideos();
        
        if (videoList.isEmpty()) {
            showEmptyState();
        } else {
            showVideoList();
        }
    }
    
    /**
     * Show empty state when no videos are available
     */
    private void showEmptyState() {
        videoListView.setVisibility(View.GONE);
        findViewById(R.id.emptyStateLayout).setVisibility(View.VISIBLE);
    }
    
    /**
     * Show video list when videos are available
     */
    private void showVideoList() {
        videoListView.setVisibility(View.VISIBLE);
        findViewById(R.id.emptyStateLayout).setVisibility(View.GONE);
        
        List<String> displayNames = new ArrayList<>();
        for (MediaStoreHelper.VideoInfo video : videoList) {
            displayNames.add(video.getDisplayName());
        }
        ArrayAdapter<String> legacyAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_list_item_1, displayNames);
        videoListView.setAdapter(legacyAdapter);
    }
    
    /**
     * Show video options dialog
     */
    private void showVideoOptionsDialog(MediaStoreHelper.VideoInfo video) {
        String[] options = {"Play", "Share", "Delete"};
        
        new AlertDialog.Builder(this)
                .setTitle(video.getDisplayName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Play
                            playVideo(video);
                            break;
                        case 1: // Share
                            shareVideo(video);
                            break;
                        case 2: // Delete
                            showDeleteConfirmationDialog(video);
                            break;
                    }
                })
                .show();
    }
    
    /**
     * Play video using system video player
     */
    private void playVideo(MediaStoreHelper.VideoInfo video) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(video.getUri(), "video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No video player available", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Share video
     */
    private void shareVideo(MediaStoreHelper.VideoInfo video) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("video/mp4");
        shareIntent.putExtra(Intent.EXTRA_STREAM, video.getUri());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share Video"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share video", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show delete confirmation dialog
     */
    private void showDeleteConfirmationDialog(MediaStoreHelper.VideoInfo video) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Video")
                .setMessage("Are you sure you want to delete " + video.getDisplayName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteVideo(video))
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Delete video
     */
    private void deleteVideo(MediaStoreHelper.VideoInfo video) {
        boolean deleted = mediaStoreHelper.deleteVideo(video.getUri());
        
        if (deleted) {
            Toast.makeText(this, "Video deleted", Toast.LENGTH_SHORT).show();
            loadVideos(); // Refresh the list
        } else {
            Toast.makeText(this, "Failed to delete video", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh video list when returning to this activity
        loadVideos();
    }
}
