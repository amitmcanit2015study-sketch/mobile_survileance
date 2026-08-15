package com.videorecorder.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder> {

    public interface Listener {
        void onPlay(MediaStoreHelper.VideoInfo video);
        void onDelete(MediaStoreHelper.VideoInfo video);
        void onSelectionChanged(MediaStoreHelper.VideoInfo video, boolean selected);
    }

    private final LayoutInflater inflater;
    private final List<MediaStoreHelper.VideoInfo> videos;
    private final Listener listener;
    private final Set<Long> selectedIds = new HashSet<>();

    public VideoAdapter(Context context, List<MediaStoreHelper.VideoInfo> videos, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.videos = videos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.item_video, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaStoreHelper.VideoInfo video = videos.get(position);
        holder.txtFileName.setText(video.getDisplayName());
        holder.txtDate.setText(video.getFormattedDate());
        holder.txtMeta.setText(video.getFormattedDuration() + "  •  " + video.getFormattedSize());
        if (video.getGeoTag() == null) {
            holder.txtGeo.setVisibility(View.GONE);
        } else {
            holder.txtGeo.setVisibility(View.VISIBLE);
            holder.txtGeo.setText("GPS " + video.getGeoTag().getDisplayText());
        }

        holder.checkboxVideo.setOnCheckedChangeListener(null);
        holder.checkboxVideo.setChecked(selectedIds.contains(video.getId()));
        holder.checkboxVideo.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                selectedIds.add(video.getId());
            } else {
                selectedIds.remove(video.getId());
            }
            listener.onSelectionChanged(video, checked);
        });
        holder.btnPlayVideo.setOnClickListener(view -> listener.onPlay(video));
        holder.btnDeleteVideo.setOnClickListener(view -> listener.onDelete(video));
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    public void setAllSelected(boolean selected) {
        selectedIds.clear();
        if (selected) {
            for (MediaStoreHelper.VideoInfo video : videos) {
                selectedIds.add(video.getId());
            }
        }
        notifyDataSetChanged();
    }

    public void clearSelections() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isSelected(long id) {
        return selectedIds.contains(id);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkboxVideo;
        final TextView txtFileName;
        final TextView txtDate;
        final TextView txtMeta;
        final TextView txtGeo;
        final ImageButton btnPlayVideo;
        final ImageButton btnDeleteVideo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkboxVideo = itemView.findViewById(R.id.checkboxVideo);
            txtFileName = itemView.findViewById(R.id.txtFileName);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtMeta = itemView.findViewById(R.id.txtMeta);
            txtGeo = itemView.findViewById(R.id.txtGeo);
            btnPlayVideo = itemView.findViewById(R.id.btnPlayVideo);
            btnDeleteVideo = itemView.findViewById(R.id.btnDeleteVideo);
        }
    }
}
